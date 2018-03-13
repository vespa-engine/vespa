// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.servlet;

import com.google.common.collect.ImmutableMap;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.yahoo.jdisc.http.core.HttpServletRequestUtils.getConnection;

/**
 * Mutable wrapper to use a {@link javax.servlet.http.HttpServletRequest}
 * with JDisc security filters.
 * <p>
 * You might find it tempting to remove e.g. the getParameter... methods,
 * but keep in mind that this IS-A servlet request and must provide the
 * full api of such a request for use outside the "JDisc filter world".
 *
 * @since 5.27
 */
public class ServletRequest extends HttpServletRequestWrapper implements ServletOrJdiscHttpRequest {
    public static final String JDISC_REQUEST_PRINCIPAL = "jdisc.request.principal";
    public static final String JDISC_REQUEST_X509CERT = "jdisc.request.X509Certificate";
    public static final String SERVLET_REQUEST_X509CERT = "javax.servlet.request.X509Certificate";

    private final HttpServletRequest request;
    private final HeaderFields headerFields;
    private final Set<String> headerBlacklist = new HashSet<>();
    private final Map<String, Object> context = new HashMap<>();
    private final Map<String, List<String>> parameters = new HashMap<>();
    private final long connectedAt;

    private URI uri;
    private String remoteHostAddress;
    private String remoteHostName;
    private int remotePort;

    public ServletRequest(HttpServletRequest request, URI uri) {
        super(request);
        this.request = request;

        this.uri = uri;

        super.getParameterMap().forEach(
                (key, values) -> parameters.put(key, Arrays.asList(values)));

        remoteHostAddress = request.getRemoteAddr();
        remoteHostName = request.getRemoteHost();
        remotePort = request.getRemotePort();
        connectedAt = getConnection(request).getCreatedTimeStamp();

        headerFields = new HeaderFields();
        Enumeration<String> parentHeaders = request.getHeaderNames();
        while (parentHeaders.hasMoreElements()) {
            String name = parentHeaders.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headerFields.add(name, values.nextElement());
            }
        }
     }

    public HttpServletRequest getRequest() {
        return request;
    }

    @Override
    public Map<String, List<String>> parameters() {
        return parameters;
    }

    /* We cannot just return the parameter map from the request, as the map
     * may have been modified by the JDisc filters. */
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameterMap = new HashMap<>();
        parameters().forEach(
                (key, values) ->
                        parameterMap.put(key, values.toArray(new String[values.size()]))
        );
        return ImmutableMap.copyOf(parameterMap);
    }

    @Override
    public String getParameter(String name) {
        return parameters().containsKey(name) ?
                parameters().get(name).get(0) :
                null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        List<String> values = parameters().get(name);
        return values != null ?
                values.toArray(new String[values.size()]) :
                null;
    }

    @Override
    public void copyHeaders(HeaderFields target) {
        target.addAll(headerFields);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (headerBlacklist.contains(name))
            return null;

        /* We don't need to merge headerFields and the servlet request's headers
         * because setHeaders() replaces the old value. There is no 'addHeader(s)'. */
        List<String> headerFields = this.headerFields.get(name);
        return headerFields == null || headerFields.isEmpty() ?
                super.getHeaders(name) :
                Collections.enumeration(headerFields);
    }

    @Override
    public String getHeader(String name) {
        if (headerBlacklist.contains(name))
            return null;

        String headerField = headerFields.getFirst(name);
        return headerField != null ?
                headerField :
                super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
        names.addAll(headerFields.keySet());
        names.removeAll(headerBlacklist);
        return Collections.enumeration(names);
    }

    public void addHeader(String name, String value) {
        headerFields.add(name, value);
        headerBlacklist.remove(name);
    }

    public void setHeaders(String name, String value) {
        headerFields.put(name, value);
        headerBlacklist.remove(name);
    }

    public void setHeaders(String name, List<String> values) {
        headerFields.put(name, values);
        headerBlacklist.remove(name);
    }

    public void removeHeaders(String name) {
        headerFields.remove(name);
        headerBlacklist.add(name);
    }

    @Override
    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    @Override
    public HttpRequest.Version getVersion() {
        String protocol = request.getProtocol();
        try {
            return HttpRequest.Version.fromString(protocol);
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new RuntimeException("Servlet request protocol '" + protocol +
                                               "' could not be mapped to a JDisc http version.", e);
        }
    }

    @Override
    public String getRemoteHostAddress() {
        return remoteHostAddress;
    }

    @Override
    public String getRemoteHostName() {
        return remoteHostName;
    }

    @Override
    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public void setRemoteAddress(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress) {
            remoteHostAddress = ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
            remoteHostName = ((InetSocketAddress) remoteAddress).getAddress().getHostName();
            remotePort = ((InetSocketAddress) remoteAddress).getPort();
        } else
            throw new RuntimeException("Unknown SocketAddress class: " + remoteHostAddress.getClass().getName());

    }

    @Override
    public Map<String, Object> context() {
        return context;
    }

    @Override
    public javax.servlet.http.Cookie[] getCookies() {
        return decodeCookieHeader().stream().
                map(jdiscCookie -> new javax.servlet.http.Cookie(jdiscCookie.getName(), jdiscCookie.getValue())).
                toArray(javax.servlet.http.Cookie[]::new);
    }

    @Override
    public List<Cookie> decodeCookieHeader() {
        Enumeration<String> cookies = getHeaders(HttpHeaders.Names.COOKIE);
        if (cookies == null)
            return Collections.emptyList();

        List<Cookie> ret = new LinkedList<>();
        while(cookies.hasMoreElements())
            ret.addAll(Cookie.fromCookieHeader(cookies.nextElement()));

        return ret;
    }

    @Override
    public void encodeCookieHeader(List<Cookie> cookies) {
        setHeaders(HttpHeaders.Names.COOKIE, Cookie.toCookieHeader(cookies));
    }

    @Override
    public long getConnectedAt(TimeUnit unit) {
        return unit.convert(connectedAt, TimeUnit.MILLISECONDS);
    }

    @Override
    public Principal getUserPrincipal() {
        // NOTE: The principal from the underlying servlet request is ignored. JDisc filters are the source-of-truth.
        return (Principal) request.getAttribute(JDISC_REQUEST_PRINCIPAL);
    }

    public void setUserPrincipal(Principal principal) {
        request.setAttribute(JDISC_REQUEST_PRINCIPAL, principal);
    }
}
