package test.mock.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import com.firefly.utils.ConvertUtils;

public class MockHttpServletRequest implements HttpServletRequest {

	protected HttpSession session;

	protected String contextPath;

	protected String[] dispatcherTarget;

	public MockHttpServletRequest() {
		this.headers = new HashMap<String, String>();
		this.dispatcherTarget = new String[1];
	}

	public String getDispatcherTarget() {
		return this.dispatcherTarget[0];
	}

	public String getAuthType() {
		throw new NoImplException();
	}

	public String getContextPath() {
		return contextPath;
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	public Cookie[] getCookies() {
		throw new NoImplException();
	}

	public long getDateHeader(String arg0) {
		throw new NoImplException();
	}

	protected Map<String, String> headers;

	public String getHeader(String name) {
		return headers.get(name);
	}

	public void setHeader(String name, Object value) {
		headers.put(name, value.toString());
	}

	public Enumeration<String> getHeaderNames() {
		return ConvertUtils.enumeration(headers.keySet());
	}

	public Enumeration<String> getHeaders(String name) {
		throw new NoImplException();
	}

	public int getIntHeader(String arg0) {
		throw new NoImplException();
	}

	protected String method;

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	protected String pathInfo;

	public String getPathInfo() {
		return pathInfo;
	}

	public void setPathInfo(String pathInfo) {
		this.pathInfo = pathInfo;
	}

	protected String pathTranslated;

	public String getPathTranslated() {
		return pathTranslated;
	}

	public void setPathTranslated(String pathTranslated) {
		this.pathTranslated = pathTranslated;
	}

	// protected String queryString;

	public String getQueryString() {
		if (params.size() == 0)
			return null;
		StringBuilder sb = new StringBuilder();
		for (Entry<String, String[]> entry : params.entrySet()) {
			if (entry.getValue() == null)
				sb.append(entry.getKey()).append("=&");
			else
				for (String str : entry.getValue()) {
					sb.append(entry.getKey()).append("=").append(str)
							.append("&");
				}
		}
		return sb.toString();
	}

	// public void setQueryString(String queryString) {
	// this.queryString = queryString;
	// }

	public String remoteUser;

	public String getRemoteUser() {
		return remoteUser;
	}

	public void setRemoteUser(String remoteUser) {
		this.remoteUser = remoteUser;
	}

	protected String requestURI;

	public String getRequestURI() {
		return requestURI;
	}

	public void setRequestURI(String requestURI) {
		this.requestURI = requestURI;
	}

	protected StringBuffer requestURL;

	public StringBuffer getRequestURL() {
		return requestURL;
	}

	public void setRequestURL(StringBuffer requestURL) {
		this.requestURL = requestURL;
	}

	public String getRequestedSessionId() {
		if (session != null)
			return session.getId();
		return null;
	}

	protected String servletPath;

	public String getServletPath() {
		return servletPath;
	}

	public void setServletPath(String servletPath) {
		this.servletPath = servletPath;
	}

	public HttpSession getSession() {
		return getSession(true);
	}

	public HttpSession getSession(boolean flag) {
		return session;
	}

	public MockHttpServletRequest setSession(HttpSession session) {
		this.session = session;
		return this;
	}

	protected Principal userPrincipal;

	public Principal getUserPrincipal() {
		return userPrincipal;
	}

	public void setUserPrincipal(Principal userPrincipal) {
		this.userPrincipal = userPrincipal;
	}

	public boolean isRequestedSessionIdFromCookie() {
		throw new NoImplException();
	}

	public boolean isRequestedSessionIdFromURL() {
		throw new NoImplException();
	}

	public boolean isRequestedSessionIdFromUrl() {
		throw new NoImplException();
	}

	public boolean isRequestedSessionIdValid() {
		throw new NoImplException();
	}

	public boolean isUserInRole(String arg0) {
		throw new NoImplException();
	}

	protected Map<String, Object> attributeMap = new HashMap<String, Object>();

	public Object getAttribute(String key) {
		return attributeMap.get(key);
	}

	public Enumeration<String> getAttributeNames() {
		return new Vector<String>(attributeMap.keySet()).elements();
	}

	protected String characterEncoding;

	public String getCharacterEncoding() {
		return characterEncoding;
	}

	public int getContentLength() {
		String cl = this.getHeader("content-length");
		try {
			return Integer.parseInt(cl);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public String getContentType() {
		return this.getHeader("content-type");
	}

	protected ServletInputStream inputStream;

	public ServletInputStream getInputStream() throws IOException {
		return inputStream;
	}

	public MockHttpServletRequest setInputStream(ServletInputStream ins) {
		this.inputStream = ins;
		return this;
	}

	public MockHttpServletRequest init() {
		// if (null != inputStream)
		// if (inputStream instanceof MultipartInputStream) {
		// ((MultipartInputStream) inputStream).init();
		// this.setCharacterEncoding(((MultipartInputStream)
		// inputStream).getCharset());
		// try {
		// this.setHeader("content-length", inputStream.available());
		// this.setHeader( "content-type",
		// ((MultipartInputStream) inputStream).getContentType());
		// }
		// catch (IOException e) {
		//
		// }
		// }
		return this;
	}

	public String getLocalAddr() {
		throw new NoImplException();
	}

	public String getLocalName() {
		throw new NoImplException();
	}

	public int getLocalPort() {
		throw new NoImplException();
	}

	public Locale getLocale() {
		throw new NoImplException();
	}

	public Enumeration<Locale> getLocales() {
		throw new NoImplException();
	}

	protected Map<String, String[]> params = new HashMap<String, String[]>();

	public String getParameter(String key) {
		if (params.containsKey(key)) {
			return params.get(key)[0];
		}
		return null;
	}

	public void setParameter(String key, String value) {
		params.put(key, new String[] { value });
	}

	public void setParameter(String key, Number num) {
		setParameter(key, num.toString());
	}

	public void setParameterValues(String key, String[] values) {
		params.put(key, values);
	}

	public void addParameter(String key, String value) {
		params.put(key, new String[] { value });
	}

	public Map<String, String[]> getParameterMap() {
		return params;
	}

	public Enumeration<String> getParameterNames() {
		return new Vector<String>(params.keySet()).elements();
	}

	public String[] getParameterValues(String name) {
		String[] param = params.get(name);
		return param;
	}

	protected String protocol;

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public BufferedReader getReader() throws IOException {
		throw new NoImplException();
	}

	public String getRealPath(String arg0) {
		throw new NoImplException();
	}

	public String getRemoteAddr() {
		throw new NoImplException();
	}

	public String getRemoteHost() {
		throw new NoImplException();
	}

	public int getRemotePort() {
		throw new NoImplException();
	}

	public RequestDispatcher getRequestDispatcher(String dest) {
		return new MockRequestDispatcher(dispatcherTarget, dest);
	}

	public String getScheme() {
		throw new NoImplException();
	}

	public String getServerName() {
		throw new NoImplException();
	}

	public int getServerPort() {
		throw new NoImplException();
	}

	public boolean isSecure() {
		throw new NoImplException();
	}

	public void removeAttribute(String key) {
		attributeMap.remove(key);
	}

	public void setAttribute(String key, Object value) {
		attributeMap.put(key, value);
	}

	public void setCharacterEncoding(String characterEncoding) {
		this.characterEncoding = characterEncoding;
	}

	@Override
	public ServletContext getServletContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest,
			ServletResponse servletResponse) throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAsyncStarted() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public AsyncContext getAsyncContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DispatcherType getDispatcherType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean authenticate(HttpServletResponse response)
			throws IOException, ServletException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void login(String username, String password) throws ServletException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void logout() throws ServletException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Part getPart(String name) throws IOException, ServletException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getContentLengthLong() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String changeSessionId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
		// TODO Auto-generated method stub
		return null;
	}

}
