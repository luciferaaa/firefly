package com.firefly.server.http2;

import java.util.List;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.alpn.ALPN;

import com.firefly.codec.http2.model.HttpVersion;
import com.firefly.codec.http2.stream.AbstractHTTPHandler;
import com.firefly.codec.http2.stream.HTTP2Configuration;
import com.firefly.codec.http2.stream.HTTPConnection;
import com.firefly.net.SSLEventHandler;
import com.firefly.net.Session;
import com.firefly.net.tcp.ssl.SSLSession;

public class HTTP2ServerHandler extends AbstractHTTPHandler {

	private final ServerSessionListener listener;
	private final ServerHTTPHandler serverHTTPHandler;

	public HTTP2ServerHandler(HTTP2Configuration config, ServerSessionListener listener,
			ServerHTTPHandler serverHTTPHandler) {
		super(config);
		this.listener = listener;
		this.serverHTTPHandler = serverHTTPHandler;
	}

	@Override
	public void sessionOpened(final Session session) throws Throwable {
		if (config.isSecureConnectionEnabled()) {
			final SSLEngine sslEngine = sslContext.createSSLEngine();
			HTTP2ServerSSLHandshakeContext handshakeContext = new HTTP2ServerSSLHandshakeContext();
			session.attachObject(handshakeContext);

			handshakeContext.sslSession = new SSLSession(sslContext, sslEngine, session, false, new SSLEventHandler() {

				@Override
				public void handshakeFinished(SSLSession sslSession) {
					log.debug("server session {} SSL handshake finished", session.getSessionId());
					if (session.getAttachment() instanceof HTTP2ServerSSLHandshakeContext) {
						HTTP2ServerSSLHandshakeContext context = (HTTP2ServerSSLHandshakeContext) session
								.getAttachment();
						log.debug("server current HTTP version is {}", context.httpVersion);

						HTTPConnection httpConnection;
						switch (context.httpVersion) {
						case HTTP_2:
							httpConnection = new HTTP2ServerConnection(config, session, sslSession, listener);
							break;
						case HTTP_1_1:
							httpConnection = new HTTP1ServerConnection(config, session, sslSession,
									new HTTP1ServerRequestHandler(serverHTTPHandler), listener);
							break;
						default:
							throw new IllegalStateException(
									"server does not support the http version " + context.httpVersion);
						}
						session.attachObject(httpConnection);
						serverHTTPHandler.acceptConnection(httpConnection);
					} else {
						log.error("HTTP2 server can not get the HTTP version of session {}", session.getSessionId());
						session.closeNow();
					}
				}
			}, new ServerALPN(sslEngine, session));

		} else {
			HTTPConnection httpConnection = new HTTP1ServerConnection(config, session, null,
					new HTTP1ServerRequestHandler(serverHTTPHandler), listener);
			session.attachObject(httpConnection);
			serverHTTPHandler.acceptConnection(httpConnection);
		}
	}

	public class ServerALPN implements ALPN.ServerProvider {
		private final SSLEngine sslEngine;
		private final Session session;

		public ServerALPN(SSLEngine sslEngine, Session session) {
			super();
			this.sslEngine = sslEngine;
			this.session = session;
		}

		@Override
		public void unsupported() {
			try {
				HTTP2ServerSSLHandshakeContext handshakeContext = (HTTP2ServerSSLHandshakeContext) session
						.getAttachment();
				handshakeContext.httpVersion = HttpVersion.HTTP_1_1;
			} finally {
				ALPN.remove(sslEngine);
			}
		}

		@Override
		public String select(List<String> clientProtocols) {
			try {
				HTTP2ServerSSLHandshakeContext handshakeContext = (HTTP2ServerSSLHandshakeContext) session
						.getAttachment();

				for (String serverProtocol : protocols) {
					for (String clientProtocol : clientProtocols) {
						if (serverProtocol.equals(clientProtocol)) {
							log.debug("HTTP2 server selected protocol {}", clientProtocol);

							if (serverProtocol.equals("http/1.1")) {
								handshakeContext.httpVersion = HttpVersion.HTTP_1_1;
							} else {
								handshakeContext.httpVersion = HttpVersion.HTTP_2;
							}
							return clientProtocol;
						}
					}
				}
				return "http/1.1";
			} finally {
				ALPN.remove(sslEngine);
			}
		}
	}

}
