// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.servlet.ServletRequest;

import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * JDisc implementation of a filter request.
 *
 * @since 5.27
 */
public class JdiscFilterRequest extends DiscFilterRequest {

    private final HttpRequest parent;

    public JdiscFilterRequest(HttpRequest parent) {
        super(parent);
        this.parent = parent;
    }

    public HttpRequest getParentRequest() {
        return parent;
    }

    public void setUri(URI uri) {
        parent.setUri(uri);
    }

    @Override
    public String getMethod() {
        return parent.getMethod().name();
    }

    @Override
    public String getParameter(String name) {
        if(parent.parameters().containsKey(name)) {
            return parent.parameters().get(name).get(0);
        }
        else {
            return null;
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parent.parameters().keySet());
    }

    @Override
    public void addHeader(String name, String value) {
        parent.headers().add(name, value);
    }

    @Override
    public String getHeader(String name) {
        List<String> values = parent.headers().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(values.size() - 1);
    }

    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(parent.headers().keySet());
    }

    public List<String> getHeaderNamesAsList() {
        return new ArrayList<String>(parent.headers().keySet());
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return Collections.enumeration(getHeadersAsList(name));
    }

    public List<String> getHeadersAsList(String name) {
        List<String> values = parent.headers().get(name);
        if(values == null) {
            return Collections.<String>emptyList();
        }
        return parent.headers().get(name);
    }

    @Override
    public void removeHeaders(String name) {
        parent.headers().remove(name);
    }

    @Override
    public void setHeaders(String name, String value) {
        parent.headers().put(name, value);
    }

    @Override
    public void setHeaders(String name, List<String> values) {
        parent.headers().put(name, values);
    }

    @Override
    public Principal getUserPrincipal() {
        return parent.getUserPrincipal();
    }

    @Override
    public void setUserPrincipal(Principal principal) {
        this.parent.setUserPrincipal(principal);
    }

    @Override
    public Optional<X509Certificate[]> getClientCertificateChain() {
        return Optional.ofNullable((X509Certificate[]) parent.context().get(ServletRequest.JDISC_REQUEST_X509CERT));
    }

    @Override
    public void clearCookies() {
        parent.headers().remove(HttpHeaders.Names.COOKIE);
    }

}
