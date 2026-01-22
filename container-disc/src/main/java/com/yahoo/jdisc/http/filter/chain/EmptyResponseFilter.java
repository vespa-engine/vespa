// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.chain;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.ResponseFilter;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public final class EmptyResponseFilter extends NoopSharedResource implements ResponseFilter {

    public static final ResponseFilter INSTANCE = new EmptyResponseFilter();

    private EmptyResponseFilter() {
        // hide
    }

    @Override
    public void filter(Response response, Request request) {

    }
}
