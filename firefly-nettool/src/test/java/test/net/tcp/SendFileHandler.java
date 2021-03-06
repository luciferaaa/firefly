package test.net.tcp;

import java.io.File;
import java.io.IOException;

import com.firefly.net.Handler;
import com.firefly.net.Session;
import com.firefly.net.buffer.FileRegion;
import com.firefly.net.support.wrap.client.SessionAttachment;
import com.firefly.utils.concurrent.Callback;
import com.firefly.utils.log.Log;
import com.firefly.utils.log.LogFactory;

public class SendFileHandler implements Handler {
	private static Log log = LogFactory.getInstance().getLog("firefly-system");

	@Override
	public void sessionOpened(Session session) throws Throwable {
		log.info("session {} is opened ", session.getSessionId());
		log.debug("local: {}", session.getLocalAddress());
		log.debug("remote: {}", session.getRemoteAddress());
		session.attachObject(new SessionAttachment());
	}

	@Override
	public void sessionClosed(Session session) throws Throwable {
		log.debug("session {} is closed", session.getSessionId());
	}

	@Override
	public void messageRecieved(Session session, Object message) throws Throwable {
		String str = (String) message;
		if (str.equals("quit")) {
			session.encode("bye!");
			session.close();
		} else if (str.equals("getfile")) {
			try (FileRegion fileRegion = new FileRegion(
					new File(SendFileHandler.class.getResource("/testFile.txt").toURI()))) {
				session.write(fileRegion, Callback.NOOP);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			log.debug("recive: " + str);
			session.encode(message);
		}
		log.debug("r {}  {} | w {} {}", session.getReadBytes(), str, session.getWrittenBytes(), message);
	}

	@Override
	public void exceptionCaught(Session session, Throwable t) throws Throwable {
		log.error(t.getMessage() + "|" + session.getSessionId(), t);
	}

}
