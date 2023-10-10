// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.chain;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.application.ResourcePool;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public final class RequestFilterChain extends AbstractResource implements RequestFilter {

    private final List<RequestFilter> filters = new ArrayList<>();
    private final ResourcePool filterReferences = new ResourcePool();

    private RequestFilterChain(Iterable<? extends RequestFilter> filters) {
        for (RequestFilter filter : filters) {
            this.filters.add(filter);
            filterReferences.retain(filter);
        }
    }

    @Override
    public void filter(HttpRequest request, ResponseHandler responseHandler) {
        ResponseHandlerGuard guard = new ResponseHandlerGuard(responseHandler);
        for (int i = 0, len = filters.size(); i < len && !guard.isDone(); ++i) {
            filters.get(i).filter(request, guard);
        }
    }

    @Override
    protected void destroy() {
        filterReferences.release();
    }

    public static RequestFilter newInstance(RequestFilter... filters) {
        return newInstance(Arrays.asList(filters));
    }

    public static RequestFilter newInstance(List<? extends RequestFilter> filters) {
        if (filters.size() == 0) {
            return EmptyRequestFilter.INSTANCE;
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new RequestFilterChain(filters);
    }
}
