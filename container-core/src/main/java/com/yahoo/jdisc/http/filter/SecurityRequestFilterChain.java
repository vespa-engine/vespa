// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import com.yahoo.jdisc.http.HttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of TypedFilterChain for DiscFilterRequest
 *
 * @author tejalk
 */
public final class SecurityRequestFilterChain extends AbstractResource implements RequestFilter {

    private final List<SecurityRequestFilter> filters = new ArrayList<>();

    private SecurityRequestFilterChain(Iterable<? extends SecurityRequestFilter> filters) {
        for (SecurityRequestFilter filter : filters) {
            this.filters.add(filter);
        }
    }

    @Override
    public void filter(HttpRequest request, ResponseHandler responseHandler) {
        DiscFilterRequest discFilterRequest = new JdiscFilterRequest(request);
        filter(discFilterRequest, responseHandler);
    }

    public void filter(DiscFilterRequest request, ResponseHandler responseHandler) {
        ResponseHandlerGuard guard = new ResponseHandlerGuard(responseHandler);
        for (int i = 0, len = filters.size(); i < len && !guard.isDone(); ++i) {
            filters.get(i).filter(request, guard);
        }
    }

    public static RequestFilter newInstance(SecurityRequestFilter... filters) {
        return newInstance(Arrays.asList(filters));
    }

    public static RequestFilter newInstance(List<? extends SecurityRequestFilter> filters) {
        return new SecurityRequestFilterChain(filters);
    }

    private static class ResponseHandlerGuard implements ResponseHandler {

        private final ResponseHandler responseHandler;
        private boolean done = false;

        public ResponseHandlerGuard(ResponseHandler handler) {
            this.responseHandler = handler;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            done = true;
            return responseHandler.handleResponse(response);
        }

        public boolean isDone() {
            return done;
        }
    }

    /** Returns an unmodifiable view of the filters in this */
    public List<SecurityRequestFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

}
