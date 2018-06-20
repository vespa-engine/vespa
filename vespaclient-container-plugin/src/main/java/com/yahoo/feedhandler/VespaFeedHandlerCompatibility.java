// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import java.util.concurrent.Executor;
import javax.inject.Inject;

import com.yahoo.jdisc.Metric;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;

public class VespaFeedHandlerCompatibility extends ThreadedHttpRequestHandler {

    private final VespaFeedHandlerGet getHandler;
    private final VespaFeedHandler feedHandler;

    @Inject
    public VespaFeedHandlerCompatibility(Executor executor, Metric metric, VespaFeedHandlerGet getHandler,
                                         VespaFeedHandler feedHandler) {
        super(executor, metric);
        this.getHandler = getHandler;
        this.feedHandler = feedHandler;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        boolean hasType = request.hasProperty("type");
        // If we have an ID and no document type, redirect to Get
        if (request.hasProperty("id") && !hasType) {
            return getHandler.handle(request);
        } else {
            return feedHandler.handle(request);
        }
    }

}
