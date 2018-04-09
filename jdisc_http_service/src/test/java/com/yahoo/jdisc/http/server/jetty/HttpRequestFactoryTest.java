// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Key;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.server.HttpConnection;
import org.testng.annotations.Test;

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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.fail;

/**
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HttpRequestFactoryTest {

    private static final class MockRequest implements HttpServletRequest {
        String queryString = null;
        StringBuffer requestUrl;
        final String method = "GET";
        final String protocol = "HTTP/1.1";
        final String remoteAddr = "127.0.0.1";
        final int remotePort = 0;

        public MockRequest(String sortOfUri) {
            int mark = sortOfUri.indexOf('?');
            if (mark > 0) {
                queryString = sortOfUri.substring(mark + 1);
                requestUrl = new StringBuffer(sortOfUri.substring(0, mark));
            } else {
                queryString = null;
                requestUrl = new StringBuffer(sortOfUri);
            }
        }

        @Override
        public Object getAttribute(String name) {
            switch (name) {
                case "org.eclipse.jetty.server.HttpConnection":
                    HttpConnection connection = mock(HttpConnection.class);
                    when(connection.getCreatedTimeStamp()).thenReturn(System.currentTimeMillis());
                    return connection;
                default:
                    return null;
            }
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getCharacterEncoding() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setCharacterEncoding(String env)
                throws UnsupportedEncodingException {
            // TODO Auto-generated method stub

        }

        @Override
        public int getContentLength() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public long getContentLengthLong() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public String getContentType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getParameter(String name) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String[] getParameterValues(String name) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public String getScheme() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getServerName() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int getServerPort() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }

        @Override
        public String getRemoteHost() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setAttribute(String name, Object o) {
            // TODO Auto-generated method stub

        }

        @Override
        public void removeAttribute(String name) {
            // TODO Auto-generated method stub

        }

        @Override
        public Locale getLocale() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isSecure() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        @Deprecated
        public String getRealPath(String path) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int getRemotePort() {
            return remotePort;
        }

        @Override
        public String getLocalName() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getLocalAddr() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int getLocalPort() {
            // TODO Auto-generated method stub
            return 0;
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
        public String getAuthType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Cookie[] getCookies() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getDateHeader(String name) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public String getHeader(String name) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int getIntHeader(String name) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getPathInfo() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getPathTranslated() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getContextPath() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getQueryString() {
            return queryString;
        }

        @Override
        public String getRemoteUser() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getRequestURI() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public StringBuffer getRequestURL() {
            return requestUrl;
        }

        @Override
        public String getServletPath() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public HttpSession getSession(boolean create) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public HttpSession getSession() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String changeSessionId() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        @Deprecated
        public boolean isRequestedSessionIdFromUrl() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse response)
                throws IOException, ServletException {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void login(String username, String password)
                throws ServletException {
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
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass)
                throws IOException, ServletException {
            // TODO Auto-generated method stub
            return null;
        }

    }

    @Test
    public final void test() {
        String noise = "query=a" + "\\" + "^{|}&other=madeit";
        HttpServletRequest servletRequest = new MockRequest(
                "http://yahoo.com/search?" + noise);
        HttpRequest request = HttpRequestFactory.newJDiscRequest(
                new MockContainer(), servletRequest);
        assertThat(request.getUri().getQuery(), equalTo(noise));
    }

    @Test
    public final void testIllegalQuery() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    new MockRequest("http://example.com/search?query=\"contains_quotes\""));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
        }
    }

    @Test
    public final void illegal_unicode_in_query_throws_requestexception() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    new MockRequest("http://example.com/search?query=%c0%ae"));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
            assertThat(e.getMessage(), equalTo("Query violates RFC 2396: Not valid UTF8! byte C0 in state 0"));
        }
    }


    private static final class MockContainer implements CurrentContainer {

        @Override
        public Container newReference(URI uri) {
            return new Container() {

                @Override
                public RequestHandler resolveHandler(com.yahoo.jdisc.Request request) {
                    return null;
                }

                @Override
                public <T> T getInstance(Key<T> tKey) {
                    return null;
                }

                @Override
                public <T> T getInstance(Class<T> tClass) {
                    return null;
                }

                @Override
                public ResourceReference refer() {
                    return References.NOOP_REFERENCE;
                }

                @Override
                public void release() {

                }

                @Override
                public long currentTimeMillis() {
                    return 0;
                }
            };
        }
    }

}
