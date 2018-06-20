// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.handler.Request;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.servlet.ServletOrJdiscHttpRequest;

import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an {@link Request} into a {@link DiscFilterRequest}. Only a few methods are supported.
 * Changes are not propagated; updated request instance must be retrieved through {@link #getUpdatedRequest()}.
 *
 * @author bjorncs
 */
public class ApplicationRequestToDiscFilterRequestWrapper extends DiscFilterRequest {

    private final Request request;
    private final List<X509Certificate> clientCertificateChain;
    private Principal userPrincipal;

    public ApplicationRequestToDiscFilterRequestWrapper(Request request) {
        this(request, Collections.emptyList());
    }

    public ApplicationRequestToDiscFilterRequestWrapper(Request request, List<X509Certificate> clientCertificateChain) {
        super(new ServletOrJdiscHttpRequest() {
            @Override
            public void copyHeaders(HeaderFields target) {
                request.getHeaders().forEach(target::add);
            }

            @Override
            public Map<String, List<String>> parameters() {
                return Collections.emptyMap();
            }

            @Override
            public URI getUri() {
                return URI.create(request.getUri());
            }

            @Override
            public HttpRequest.Version getVersion() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRemoteHostAddress() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRemoteHostName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getRemotePort() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setRemoteAddress(SocketAddress remoteAddress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, Object> context() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Cookie> decodeCookieHeader() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void encodeCookieHeader(List<Cookie> cookies) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getConnectedAt(TimeUnit unit) {
                throw new UnsupportedOperationException();
            }
        });
        this.request = request;
        this.userPrincipal = request.getUserPrincipal().orElse(null);
        this.clientCertificateChain = clientCertificateChain;
    }

    public Request getUpdatedRequest() {
        Request updatedRequest = new Request(this.request.getUri(), this.request.getBody(), this.request.getMethod(), this.userPrincipal);
        this.request.getHeaders().forEach(updatedRequest.getHeaders()::put);
        return updatedRequest;
    }

    @Override
    public String getMethod() {
        return request.getMethod().name();
    }

    @Override
    public void setUri(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getParameter(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addHeader(String name, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHeader(String name) {
        return request.getHeaders().getFirst(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getHeaderNamesAsList() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getHeadersAsList(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeHeaders(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHeaders(String name, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHeaders(String name, List<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Principal getUserPrincipal() {
        return this.userPrincipal;
    }

    @Override
    public void setUserPrincipal(Principal principal) {
        this.userPrincipal = principal;
    }

    @Override
    public List<X509Certificate> getClientCertificateChain() {
        return clientCertificateChain;
    }

    @Override
    public void clearCookies() {
        throw new UnsupportedOperationException();
    }
}
