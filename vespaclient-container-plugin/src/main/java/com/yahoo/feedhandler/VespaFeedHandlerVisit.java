// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import java.util.Collections;
import java.util.concurrent.Executor;
import javax.inject.Inject;

import com.yahoo.jdisc.Metric;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.search.handler.SearchHandler;

/**
 * @author thomasg
 */
public class VespaFeedHandlerVisit extends ThreadedHttpRequestHandler {

    private final SearchHandler searchHandler;

    @Inject
    public VespaFeedHandlerVisit(SearchHandler searchHandler, Executor executor, Metric metric) {
        super(executor, metric, true);
        this.searchHandler = searchHandler;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return searchHandler.handle(new HttpRequest(request.getJDiscRequest(), request.getData(), Collections.singletonMap("searchChain", "vespavisit")));
    }

}
