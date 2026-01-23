// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.chain;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public final class EmptyRequestFilter extends NoopSharedResource implements RequestFilter {

    public static final RequestFilter INSTANCE = new EmptyRequestFilter();

    private EmptyRequestFilter() {
        // hide
    }

    @Override
    public void filter(HttpRequest request, ResponseHandler handler) {

    }
}
