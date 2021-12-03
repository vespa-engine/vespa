// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of TypedFilterChain for DiscFilterResponse
 *
 * @author Tejal Knot
 */
public class SecurityResponseFilterChain extends AbstractResource implements ResponseFilter {

    private final List<SecurityResponseFilter> filters = new ArrayList<>();

    private SecurityResponseFilterChain(Iterable<? extends SecurityResponseFilter> filters) {
        for (SecurityResponseFilter filter : filters) {
            this.filters.add(filter);
        }
    }

    @Override
    public void filter(Response response, Request request) {
        DiscFilterResponse discFilterResponse = new DiscFilterResponse(response);
        RequestView requestView = new RequestViewImpl(request);
        filter(requestView, discFilterResponse);
    }

    public void filter(RequestView requestView, DiscFilterResponse response) {
        for (SecurityResponseFilter filter : filters) {
            filter.filter(response, requestView);
        }
    }

    public static ResponseFilter newInstance(SecurityResponseFilter... filters) {
        return newInstance(Arrays.asList(filters));
    }

    public static ResponseFilter newInstance(List<? extends SecurityResponseFilter> filters) {
        return new SecurityResponseFilterChain(filters);
    }

    /** Returns an unmodifiable view of the filters in this */
    public List<SecurityResponseFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    static class RequestViewImpl implements RequestView {

        private final Request request;
        private final Optional<HttpRequest.Method> method;

        public RequestViewImpl(Request request) {
            this.request = request;
            method = request instanceof HttpRequest ?
                     Optional.of(((HttpRequest) request).getMethod()):
                     Optional.empty();
        }

        @Override
        public Object getAttribute(String name) {
            return request.context().get(name);
        }

        @Override
        public List<String> getHeaders(String name) {
            List<String> headers = request.headers().get(name);
            return headers == null ? Collections.emptyList() : Collections.unmodifiableList(headers);
        }

        @Override
        public Optional<String> getFirstHeader(String name) {
            return getHeaders(name).stream().findFirst();
        }

        @Override
        public Optional<HttpRequest.Method> getMethod() {
            return method;
        }

        @Override
        public URI getUri() {
            return request.getUri();
        }

    }

}
