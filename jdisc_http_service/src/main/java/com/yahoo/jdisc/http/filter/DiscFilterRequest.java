// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Version;
import com.yahoo.jdisc.http.servlet.ServletOrJdiscHttpRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The Request class on which all filters will operate upon.
 * Test cases that need a concrete
 * instance should create a {@link JdiscFilterRequest}.
 */
public abstract class DiscFilterRequest {

    protected static final String HTTPS_PREFIX = "https";
    protected static final int DEFAULT_HTTP_PORT = 80;
    protected static final int DEFAULT_HTTPS_PORT = 443;

    private final ServletOrJdiscHttpRequest parent;
    protected final Map<String, List<String>> untreatedParams;
    private final HeaderFields untreatedHeaders;
    private List<Cookie> untreatedCookies = null;
    private String remoteUser = null;
    private String[] roles = null;
    private boolean overrideIsUserInRole = false;

    public DiscFilterRequest(ServletOrJdiscHttpRequest parent) {
        this.parent = parent;

        // save untreated headers from parent
        untreatedHeaders = new HeaderFields();
        parent.copyHeaders(untreatedHeaders);

        untreatedParams = new HashMap<>(parent.parameters());
    }

    public abstract String getMethod();

    public Version getVersion() {
        return parent.getVersion();
    }

    public URI getUri() {
        return parent.getUri();
    }

    public abstract void setUri(URI uri);

    public HttpRequest getParentRequest() {
        throw new UnsupportedOperationException(
                "getParentRequest is not supported for " + parent.getClass().getName());
    }

    /**
     * Returns the Internet Protocol (IP) address of the client
     * or last proxy that sent the request.
     */
    public String getRemoteAddr() {
        return parent.getRemoteHostAddress();
    }

    /**
     * Set the IP address of the remote client associated with this Request.
     */
    public void setRemoteAddr(String remoteIpAddress) {
        InetSocketAddress remoteAddress = new InetSocketAddress(remoteIpAddress, this.getRemotePort());
        parent.setRemoteAddress(remoteAddress);
    }

    /**
     * Returns the Internet Protocol (IP) address of the interface
     *  on which the request was received.
     */
    public String getLocalAddr() {
        InetSocketAddress localAddress = localAddress();
        if (localAddress.getAddress() == null) return null;
        return localAddress.getAddress().getHostAddress();
    }

    private InetSocketAddress localAddress() {
        int port = parent.getUri().getPort();
        if (port < 0)
            port  = 0;
        return new InetSocketAddress(parent.getUri().getHost(), port);
    }

    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(parent.context().keySet());
    }

    public Object getAttribute(String name) {
        return parent.context().get(name);
    }

    public void setAttribute(String name, Object value) {
        parent.context().put(name, value);
    }

    public boolean containsAttribute(String name) {
        return parent.context().containsKey(name);
    }

    public void removeAttribute(String name) {
        parent.context().remove(name);
    }

    public abstract String getParameter(String name);

    public abstract Enumeration<String> getParameterNames();

    public List<String> getParameterNamesAsList() {
        return new ArrayList<String>(parent.parameters().keySet());
    }

    public Enumeration<String> getParameterValues(String name) {
        return Collections.enumeration(parent.parameters().get(name));
    }

    public List<String> getParameterValuesAsList(String name) {
        return parent.parameters().get(name);
    }

    public Map<String,List<String>> getParameterMap() {
        return parent.parameters();
    }


    /**
     * Returns the hostName of remoteHost, or null if none
     */
    public String getRemoteHost() {
        return parent.getRemoteHostName();
    }

    /**
     * Returns the Internet Protocol (IP) port number of
     * the interface on which the request was received.
     */
    public int getLocalPort() {
        return localAddress().getPort();
    }

    /**
     * Returns the port of remote host
     */
    public int getRemotePort() {
        return parent.getRemotePort();
    }

    /**
     * Returns a unmodifiable map of untreatedParameters from the
     * parent request.
     */
    public Map<String, List<String>> getUntreatedParams() {
        return Collections.unmodifiableMap(untreatedParams);
    }


    /**
     * Returns the untreatedHeaders from
     * parent request
     */
    public HeaderFields getUntreatedHeaders() {
        return untreatedHeaders;
    }

    /**
     * Returns the untreatedCookies from
     * parent request
     */
    public List<Cookie> getUntreatedCookies() {
        if (untreatedCookies == null) {
            this.untreatedCookies = parent.decodeCookieHeader();
        }
        return Collections.unmodifiableList(untreatedCookies);
    }

    /**
     * Sets a header with the given name and value.
     * If the header had already been set, the new value overwrites the previous one.
     */
    public abstract void addHeader(String name, String value);

    public long getDateHeader(String name) {
        String value = getHeader(name);
        if (value == null)
            return -1L;

        Date date = null;
        for (int i = 0; (date == null) && (i < formats.length); i++) {
            try {
                date = formats[i].parse(value);
            } catch (ParseException e) {
            }
        }
        if (date == null) {
            return -1L;
        }

        return date.getTime();
    }

    public abstract String getHeader(String name);

    public abstract Enumeration<String> getHeaderNames();

    public abstract List<String> getHeaderNamesAsList();

    public abstract Enumeration<String> getHeaders(String name);

    public abstract List<String> getHeadersAsList(String name);

    public abstract void removeHeaders(String name);

    /**
     * Sets a header with the given name and value.
     *  If the header had already been set, the new value overwrites the previous one.
     *
     */
    public abstract void setHeaders(String name, String value);

    /**
     * Sets a header with the given name and value.
     *  If the header had already been set, the new value overwrites the previous one.
     *
     */
    public abstract void setHeaders(String name, List<String> values);

    public int getIntHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1;
        } else {
            return Integer.parseInt(value);
        }
    }


    public List<Cookie> getCookies() {
        return parent.decodeCookieHeader();
    }

    public void setCookies(List<Cookie> cookies) {
        parent.encodeCookieHeader(cookies);
    }

    public long getConnectedAt(TimeUnit unit) {
        return parent.getConnectedAt(unit);
    }

    public String getProtocol() {
        return getVersion().name();
    }

    /**
     * Returns the query string that is contained in the request URL.
     * Returns the undecoded value uri.getRawQuery()
     */
    public String getQueryString() {
        return getUri().getRawQuery();
    }

    /**
     *  Returns the login of the user making this request,
     *  if the user has been authenticated, or null if the user has not been authenticated.
     */
    public String getRemoteUser() {
        return remoteUser;
    }

    public String getRequestURI() {
        return getUri().getRawPath();
    }

    public String getRequestedSessionId() {
        return null;
    }

    public String getScheme() {
        return getUri().getScheme();
    }

    public void setScheme(String scheme, boolean isSecure) {
        String uri = getUri().toString();
        String arr [] = uri.split("://");
        URI newUri = URI.create(scheme + "://" + arr[1]);
        setUri(newUri);
    }

    public String getServerName() {
        return getUri().getHost();
    }

    public int getServerPort() {
        int port = getUri().getPort();
        if(port == -1) {
            if(isSecure()) {
                port = DEFAULT_HTTPS_PORT;
            }
            else {
                port = DEFAULT_HTTP_PORT;
            }
        }

        return port;
    }

    public abstract Principal getUserPrincipal();

    public boolean isSecure() {
        if(getScheme().equalsIgnoreCase(HTTPS_PREFIX)) {
            return true;
        }
        return false;
    }


    /**
     * Returns a boolean indicating whether the authenticated user
     *  is included in the specified logical "role".
     */
    public boolean isUserInRole(String role) {
        if(overrideIsUserInRole) {
            if(roles != null) {
                for (String role1 : roles) {
                    if (role1 != null && role1.trim().length() > 0) {
                        String userRole = role1.trim();
                        if (userRole.equals(role)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        else {
            return false;
        }
    }

    public void setOverrideIsUserInRole(boolean overrideIsUserInRole) {
        this.overrideIsUserInRole = overrideIsUserInRole;
    }

    public void setRemoteHost(String remoteAddr) { }

    public void setRemoteUser(String remoteUser) {
        this.remoteUser = remoteUser;
    }

    public abstract void setUserPrincipal(Principal principal);

    /**
     * @return The client certificate chain in ascending order of trust. The first certificate is the one sent from the client.
     *         Returns an empty list if the client did not provide a certificate.
     */
    public abstract List<X509Certificate> getClientCertificateChain();

    public void setUserRoles(String[] roles) {
        this.roles = roles;
    }

    /**
     * Returns the content-type for the request
     */
    public String getContentType() {
        return getHeader(HttpHeaders.Names.CONTENT_TYPE);
    }


    /**
     * Get character encoding
     */
    public String getCharacterEncoding() {
        return getCharsetFromContentType(this.getContentType());
    }

    /**
     * Set character encoding
     */
    public void setCharacterEncoding(String encoding) {
        String charEncoding = setCharsetFromContentType(this.getContentType(), encoding);
        if(charEncoding != null && !charEncoding.isEmpty()) {
            removeHeaders(HttpHeaders.Names.CONTENT_TYPE);
            setHeaders(HttpHeaders.Names.CONTENT_TYPE, charEncoding);
        }
    }

    /**
     * Can be called multiple times to add Cookies
     */
    public void addCookie(JDiscCookieWrapper cookie) {
        if(cookie != null) {
            List<Cookie> cookies = new ArrayList<Cookie>();
            //Get current set of cookies first
            List<Cookie> c = getCookies();
            if(c != null && !c.isEmpty()) {
                cookies.addAll(c);
            }
            cookies.add(cookie.getCookie());
            setCookies(cookies);
        }
    }

    public abstract void clearCookies();

    public JDiscCookieWrapper[] getWrappedCookies() {
        List<Cookie> cookies = getCookies();
        if(cookies == null)  {
            return null;
        }
        List<JDiscCookieWrapper> cookieWrapper = new ArrayList<>(cookies.size());
        for(Cookie cookie : cookies) {
            cookieWrapper.add(JDiscCookieWrapper.wrap(cookie));
        }

        return cookieWrapper.toArray(new JDiscCookieWrapper[cookieWrapper.size()]);
    }

    private String setCharsetFromContentType(String contentType,String charset) {
        String newContentType = "";
        if (contentType == null)
            return (null);
        int start = contentType.indexOf("charset=");
        if (start < 0) {
            //No charset present:
            newContentType = contentType + ";charset=" + charset;
            return newContentType;
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            newContentType = contentType.substring(0,start);
            newContentType = newContentType + "charset=" + charset;
            newContentType = newContentType + encoding.substring(end,encoding.length());
        }
        else {
            newContentType = contentType.substring(0,start);
            newContentType = newContentType + "charset=" + charset;
        }

        return (newContentType.trim());

    }

    private String getCharsetFromContentType(String contentType) {

        if (contentType == null)
            return (null);
        int start = contentType.indexOf("charset=");
        if (start < 0)
            return (null);
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0)
            encoding = encoding.substring(0, end);
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
                && (encoding.endsWith("\"")))
            encoding = encoding.substring(1, encoding.length() - 1);
        return (encoding.trim());

    }

    public static boolean isMultipart(DiscFilterRequest request) {
        if (request == null) {
            return false;
        }

        String contentType = request.getContentType();

        if (contentType == null) {
            return false;
        }

        String[] parts = Pattern.compile(";").split(contentType);
        if (parts.length == 0) {
            return false;
        }

        for (String part : parts) {
            if ("multipart/form-data".equals(part)) {
                return true;
            }
        }

        return false;
    }

    protected static ThreadLocalSimpleDateFormat formats[] = {
            new ThreadLocalSimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
                                            Locale.US),
            new ThreadLocalSimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz",
                                            Locale.US),
            new ThreadLocalSimpleDateFormat("EEE MMMM d HH:mm:ss yyyy",
                                            Locale.US) };

    /**
     * The set of SimpleDateFormat formats to use in getDateHeader().
     *
     * Notice that because SimpleDateFormat is not thread-safe, we can't declare
     * formats[] as a static variable.
     */
    protected static final class ThreadLocalSimpleDateFormat extends
            ThreadLocal<SimpleDateFormat> {
        private final String format;
        private final Locale locale;

        public ThreadLocalSimpleDateFormat(String format, Locale locale) {
            super();
            this.format = format;
            this.locale = locale;
        }

        // @see java.lang.ThreadLocal#initialValue()
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(format, locale);
        }

        public Date parse(String value) throws ParseException {
            return get().parse(value);
        }
    }

}
