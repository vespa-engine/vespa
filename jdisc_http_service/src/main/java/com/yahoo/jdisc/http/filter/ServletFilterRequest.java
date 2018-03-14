// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.servlet.ServletRequest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Servlet implementation for JDisc filter requests.
 *
 * @since 5.27
 */
class ServletFilterRequest extends DiscFilterRequest {

    private final ServletRequest parent;

    public ServletFilterRequest(ServletRequest parent) {
        super(parent);
        this.parent = parent;
    }

    ServletRequest getServletRequest() {
        return parent;
    }

    public void setUri(URI uri) {
        parent.setUri(uri);
    }

    @Override
    public String getMethod() {
        return parent.getRequest().getMethod();
    }

    @Override
    public void setRemoteAddr(String remoteIpAddress) {
        throw new UnsupportedOperationException(
                "Setting remote address is not supported for " + this.getClass().getName());
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> names = new HashSet<>(Collections.list(super.getAttributeNames()));
        names.addAll(Collections.list(parent.getRequest().getAttributeNames()));
        return Collections.enumeration(names);
    }

    @Override
    public Object getAttribute(String name) {
        Object jdiscAttribute = super.getAttribute(name);
        return jdiscAttribute != null ?
                jdiscAttribute :
                parent.getRequest().getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        parent.getRequest().setAttribute(name, value);
    }

    @Override
    public boolean containsAttribute(String name) {
        return super.containsAttribute(name)
                || parent.getRequest().getAttribute(name) != null;
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        parent.getRequest().removeAttribute(name);
    }

    @Override
    public String getParameter(String name) {
        return parent.getParameter(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return parent.getParameterNames();
    }

    @Override
    public void addHeader(String name, String value) {
        parent.addHeader(name, value);
    }

    @Override
    public String getHeader(String name) {
        return parent.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return parent.getHeaderNames();
    }

    public List<String> getHeaderNamesAsList() {
        return Collections.list(getHeaderNames());
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return parent.getHeaders(name);
    }

    @Override
    public List<String> getHeadersAsList(String name) {
        return Collections.list(getHeaders(name));
    }

    @Override
    public void setHeaders(String name, String value) {
        parent.setHeaders(name, value);
    }

    @Override
    public void setHeaders(String name, List<String> values) {
        parent.setHeaders(name, values);
    }

    @Override
    public Principal getUserPrincipal() {
        return parent.getUserPrincipal();
    }

    @Override
    public void setUserPrincipal(Principal principal) {
        parent.setUserPrincipal(principal);
    }

    @Override
    public List<X509Certificate> getClientCertificateChain() {
        return Optional.ofNullable(parent.getRequest().getAttribute(ServletRequest.SERVLET_REQUEST_X509CERT))
                .map(X509Certificate[].class::cast)
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    @Override
    public void removeHeaders(String name) {
        parent.removeHeaders(name);
    }

    @Override
    public void clearCookies() {
        parent.removeHeaders(HttpHeaders.Names.COOKIE);
    }

    @Override
    public void setCharacterEncoding(String encoding) {
        super.setCharacterEncoding(encoding);
        try {
            parent.setCharacterEncoding(encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encoding not supported: " + encoding, e);
        }
    }
}
