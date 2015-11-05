package com.firefly.server.http2;

import com.firefly.codec.http2.decode.ServerParser;
import com.firefly.codec.http2.encode.Generator;
import com.firefly.codec.http2.stream.BufferingFlowControlStrategy;
import com.firefly.codec.http2.stream.FlowControlStrategy;
import com.firefly.codec.http2.stream.SimpleFlowControlStrategy;
import com.firefly.utils.concurrent.Scheduler;
import com.firefly.utils.concurrent.Schedulers;

public class HTTP2SessionAttachment {

	private ServerParser serverParser;
	private com.firefly.net.Session endPoint;
	private static Scheduler scheduler = Schedulers.createScheduler();
	
	public HTTP2SessionAttachment(HTTP2Configuration config, com.firefly.net.Session endPoint, ServerSessionListener serverSessionListener) {
		this.endPoint = endPoint;
		FlowControlStrategy flowControl = null;
		switch (config.getFlowControlStrategy()) {
		case "buffer":
			flowControl = new BufferingFlowControlStrategy(config.getInitialStreamSendWindow(), 0.5f);
			break;
		case "simple":
			flowControl = new SimpleFlowControlStrategy(config.getInitialStreamSendWindow());
			break;
		default:
			break;
		}
		
		Generator generator = new Generator(config.getMaxDynamicTableSize(), config.getMaxHeaderBlockFragment());
		HTTP2ServerSession http2ServerSession = new HTTP2ServerSession(scheduler, this.endPoint, generator, serverSessionListener, flowControl, config.getStreamIdleTimeout());
		http2ServerSession.setMaxLocalStreams(config.getMaxConcurrentStreams());
		http2ServerSession.setMaxRemoteStreams(config.getMaxConcurrentStreams());

		this.serverParser = new ServerParser(http2ServerSession, config.getMaxDynamicTableSize(), config.getRequestHeaderSize());
	}

	public ServerParser getServerParser() {
		return serverParser;
	}

	public com.firefly.net.Session getEndPoint() {
		return endPoint;
	}

	public static Scheduler getScheduler() {
		return scheduler;
	}
}