package test.http;

import com.firefly.server.http2.servlet.ServerBootstrap;

public class ServerDemo1 {

	public static void main(String[] args) throws Throwable {
		start();
	}
	
	public static void start() {
		ServerBootstrap bootstrap = new ServerBootstrap("firefly-server1.xml", "localhost", 6656);
		bootstrap.start();
	}
	

}
