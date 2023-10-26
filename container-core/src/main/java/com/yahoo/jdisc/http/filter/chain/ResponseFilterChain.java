// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.chain;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ResourcePool;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public final class ResponseFilterChain extends AbstractResource implements ResponseFilter {

    private final List<ResponseFilter> filters = new ArrayList<>();
    private final ResourcePool filterReferences = new ResourcePool();

    private ResponseFilterChain(Iterable<? extends ResponseFilter> filters) {
        for (ResponseFilter filter : filters) {
            this.filters.add(filter);
            filterReferences.retain(filter);
        }
    }

    @Override
    public void filter(Response response, Request request) {
        for (ResponseFilter filter : filters) {
            filter.filter(response, request);
        }
    }

    @Override
    protected void destroy() {
        filterReferences.release();
    }

    public static ResponseFilter newInstance(ResponseFilter... filters) {
        return newInstance(Arrays.asList(filters));
    }

    public static ResponseFilter newInstance(List<? extends ResponseFilter> filters) {
        if (filters.size() == 0) {
            return EmptyResponseFilter.INSTANCE;
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new ResponseFilterChain(filters);
    }
}
