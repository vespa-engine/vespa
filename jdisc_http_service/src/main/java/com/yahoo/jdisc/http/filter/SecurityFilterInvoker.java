// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.google.common.annotations.Beta;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.servlet.ServletRequest;

import com.yahoo.jdisc.http.servlet.ServletResponse;
import com.yahoo.jdisc.http.server.jetty.FilterInvoker;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Only intended for internal vespa use.
 *
 * Runs JDisc security filter without using JDisc request/response.
 * Only intended to be used in a servlet context, as the error messages are tailored for that.
 *
 * Assumes that SecurityResponseFilters mutate DiscFilterResponse in the thread they are invoked from.
 *
 * @author Tony Vaagenes
 */
@Beta
public class SecurityFilterInvoker implements FilterInvoker {

    /**
     * Returns the servlet request to be used in any servlets invoked after this.
     */
    @Override
    public HttpServletRequest invokeRequestFilterChain(RequestFilter requestFilterChain,
                                                       URI uri, HttpServletRequest httpRequest,
                                                       ResponseHandler responseHandler) {

        SecurityRequestFilterChain securityChain = cast(SecurityRequestFilterChain.class, requestFilterChain).
                orElseThrow(SecurityFilterInvoker::newUnsupportedOperationException);

        ServletRequest wrappedRequest = new ServletRequest(httpRequest, uri);
        securityChain.filter(new ServletFilterRequest(wrappedRequest), responseHandler);
        return wrappedRequest;
    }

    @Override
    public void invokeResponseFilterChain(
            ResponseFilter responseFilterChain,
            URI uri,
            HttpServletRequest request,
            HttpServletResponse response) {

        SecurityResponseFilterChain securityChain = cast(SecurityResponseFilterChain.class, responseFilterChain).
                orElseThrow(SecurityFilterInvoker::newUnsupportedOperationException);

        ServletFilterResponse wrappedResponse = new ServletFilterResponse(new ServletResponse(response));
        securityChain.filter(new ServletRequestView(uri, request), wrappedResponse);
    }

    private static UnsupportedOperationException newUnsupportedOperationException() {
        return new UnsupportedOperationException(
                "Filter type not supported. If a request is handled by servlets or jax-rs, then any filters invoked for that request must be security filters.");
    }

    private <T> Optional<T> cast(Class<T> securityFilterChainClass, Object filter) {
        return (securityFilterChainClass.isInstance(filter))?
                Optional.of(securityFilterChainClass.cast(filter)):
                Optional.empty();
    }

    private static class ServletRequestView implements RequestView {
        private final HttpServletRequest request;
        private final URI uri;

        public ServletRequestView(URI uri, HttpServletRequest request) {
            this.request = request;
            this.uri = uri;
        }

        @Override
        public Object getAttribute(String name) {
            return request.getAttribute(name);
        }


        @Nonnull @Override
        public List<String> getHeaders(String name) {
            return Collections.unmodifiableList(Collections.list(request.getHeaders(name)));
        }

        @Override
        public Optional<String> getFirstHeader(String name) {
            return getHeaders(name).stream().findFirst();
        }

        @Override
        public Optional<Method> getMethod() {
            return Optional.of(Method.valueOf(request.getMethod()));
        }

        @Override
        public URI getUri() {
            return uri;
        }
    }

}
