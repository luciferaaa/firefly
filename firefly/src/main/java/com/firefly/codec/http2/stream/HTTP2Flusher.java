package com.firefly.codec.http2.stream;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.firefly.codec.http2.frame.Frame;
import com.firefly.codec.http2.frame.WindowUpdateFrame;
import com.firefly.net.ByteBufferArrayOutputEntry;
import com.firefly.utils.collection.ArrayQueue;
import com.firefly.utils.concurrent.Callback;
import com.firefly.utils.concurrent.IteratingCallback;
import com.firefly.utils.io.BufferUtils;
import com.firefly.utils.io.EofException;
import com.firefly.utils.log.Log;
import com.firefly.utils.log.LogFactory;

public class HTTP2Flusher extends IteratingCallback {
	private static Log log = LogFactory.getInstance().getLog("firefly-system");

	private final Queue<WindowEntry> windows = new ArrayDeque<>();
	private final ArrayQueue<Entry> frames = new ArrayQueue<>(ArrayQueue.DEFAULT_CAPACITY, ArrayQueue.DEFAULT_GROWTH,
			this);
	private final Queue<Entry> entries = new ArrayDeque<>();
	private final List<Entry> actives = new ArrayList<>();
	private final HTTP2Session session;
	private Entry stalled;
	private boolean terminated;

	private final Queue<ByteBuffer> buffers = new LinkedList<>();

	public HTTP2Flusher(HTTP2Session session) {
		this.session = session;
	}

	public void window(StreamSPI stream, WindowUpdateFrame frame) {
		boolean closed;
		synchronized (this) {
			closed = terminated;
			if (!closed)
				windows.offer(new WindowEntry(stream, frame));
		}
		// Flush stalled data.
		if (!closed)
			iterate();
	}

	public boolean prepend(Entry entry) {
		boolean closed;
		synchronized (this) {
			closed = terminated;
			if (!closed) {
				frames.add(0, entry);
				if (log.isDebugEnabled())
					log.debug("Prepended {}, frames={}", entry, frames.size());
			}
		}
		if (closed)
			closed(entry, new ClosedChannelException());
		return !closed;
	}

	public boolean append(Entry entry) {
		boolean closed;
		synchronized (this) {
			closed = terminated;
			if (!closed) {
				frames.offer(entry);
				if (log.isDebugEnabled())
					log.debug("Appended {}, frames={}", entry, frames.size());
			}
		}
		if (closed)
			closed(entry, new ClosedChannelException());
		return !closed;
	}

	public int getQueueSize() {
		synchronized (this) {
			return frames.size();
		}
	}

	@Override
	protected Action process() throws Exception {
		if (log.isDebugEnabled())
			log.debug("Flushing {}", session);

		synchronized (this) {
			if (terminated)
				throw new ClosedChannelException();

			while (!windows.isEmpty()) {
				WindowEntry entry = windows.poll();
				entry.perform();
			}

			if (!frames.isEmpty()) {
				for (Entry entry : frames) {
					entries.offer(entry);
					actives.add(entry);
				}
				frames.clear();
			}
		}

		if (entries.isEmpty()) {
			if (log.isDebugEnabled())
				log.debug("Flushed {}", session);
			return Action.IDLE;
		}

		while (!entries.isEmpty()) {
			Entry entry = entries.poll();
			if (log.isDebugEnabled())
				log.debug("Processing {}, {}", entry, entry.dataRemaining());

			// If the stream has been reset, don't send the frame.
			if (entry.reset()) {
				if (log.isDebugEnabled())
					log.debug("Resetting {}", entry);
				continue;
			}

			try {
				if (entry.generate(buffers)) {
					if(log.isDebugEnabled()) {
						log.debug("Generated entry {}, {}", entry, entry.dataRemaining());
					}
					if (entry.dataRemaining() > 0)
						entries.offer(entry);
				} else {
					if (stalled == null)
						stalled = entry;
				}
			} catch (Throwable failure) {
				// Failure to generate the entry is catastrophic.
				if (log.isDebugEnabled())
					log.debug("Failure generating frame " + entry.frame, failure);
				failed(failure);
				return Action.SUCCEEDED;
			}
		}

		if (buffers.isEmpty()) {
			complete();
			return Action.IDLE;
		}

		if (log.isDebugEnabled())
			log.debug("Writing {} buffers ({} bytes) for {} frames {}", buffers.size(), getBufferTotalLength(),
					actives.size(), actives.toString());

		ByteBufferArrayOutputEntry outputEntry = new ByteBufferArrayOutputEntry(this,
				buffers.toArray(BufferUtils.EMPTY_BYTE_BUFFER_ARRAY));
		session.getEndPoint().encode(outputEntry);
		return Action.SCHEDULED;
	}

	private int getBufferTotalLength() {
		int length = 0;
		for (ByteBuffer buf : buffers) {
			length += buf.remaining();
		}
		return length;
	}

	@Override
	public void succeeded() {
		if (log.isDebugEnabled())
			log.debug("Written {} frames for {}", actives.size(), actives.toString());

		complete();

		super.succeeded();
	}

	private void complete() {
		buffers.clear();

		actives.forEach(Entry::complete);

		if (stalled != null) {
			// We have written part of the frame, but there is more to write.
			// The API will not allow to send two data frames for the same
			// stream so we append the unfinished frame at the end to allow
			// better interleaving with other streams.
			int index = actives.indexOf(stalled);
			for (int i = index; i < actives.size(); ++i) {
				Entry entry = actives.get(i);
				if (entry.dataRemaining() > 0)
					append(entry);
			}
			for (int i = 0; i < index; ++i) {
				Entry entry = actives.get(i);
				if (entry.dataRemaining() > 0)
					append(entry);
			}
			stalled = null;
		}

		actives.clear();
	}

	@Override
	protected void onCompleteSuccess() {
		throw new IllegalStateException();
	}

	@Override
	protected void onCompleteFailure(Throwable x) {
		buffers.clear();

		boolean closed;
		synchronized (this) {
			closed = terminated;
			terminated = true;
			if (log.isDebugEnabled())
				log.debug("{}, active/queued={}/{}", closed ? "Closing" : "Failing", actives.size(), frames.size());
			actives.addAll(frames);
			frames.clear();
		}

		actives.forEach(entry -> entry.failed(x));
		actives.clear();

		// If the failure came from within the
		// flusher, we need to close the connection.
		if (!closed)
			session.abort(x);
	}

	void terminate() {
		boolean closed;
		synchronized (this) {
			closed = terminated;
			terminated = true;
			if (log.isDebugEnabled())
				log.debug("{}", closed ? "Terminated" : "Terminating");
		}
		if (!closed)
			iterate();
	}

	private void closed(Entry entry, Throwable failure) {
		entry.failed(failure);
	}

	public static abstract class Entry extends Callback.Nested {
		protected final Frame frame;
		protected final StreamSPI stream;
		private boolean reset;

		protected Entry(Frame frame, StreamSPI stream, Callback callback) {
			super(callback);
			this.frame = frame;
			this.stream = stream;
		}

		public int dataRemaining() {
			return 0;
		}

		protected abstract boolean generate(Queue<ByteBuffer> buffers);

		private void complete() {
			if (reset)
				failed(new EofException("reset"));
			else
				succeeded();
		}

		@Override
		public void failed(Throwable x) {
			if (stream != null) {
				stream.close();
				stream.getSession().removeStream(stream);
			}
			super.failed(x);
		}

		private boolean reset() {
			return this.reset = stream != null && stream.isReset() && !isProtocol();
		}

		private boolean isProtocol() {
			switch (frame.getType()) {
			case PRIORITY:
			case RST_STREAM:
			case GO_AWAY:
			case WINDOW_UPDATE:
			case DISCONNECT:
				return true;
			default:
				return false;
			}
		}

		@Override
		public String toString() {
			return frame.toString();
		}
	}

	private class WindowEntry {
		private final StreamSPI stream;
		private final WindowUpdateFrame frame;

		public WindowEntry(StreamSPI stream, WindowUpdateFrame frame) {
			this.stream = stream;
			this.frame = frame;
		}

		public void perform() {
			FlowControlStrategy flowControl = session.getFlowControlStrategy();
			flowControl.onWindowUpdate(session, stream, frame);
		}
	}

}
