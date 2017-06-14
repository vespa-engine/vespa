// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.Presentation;

import java.util.concurrent.Executor;

/**
 * Forward requests to search handler after adding "Red Hat" as query and "demo"
 * as renderer ID.
 */
public class DemoHandler extends LoggingRequestHandler {

    private final SearchHandler searchHandler;

    /**
     * Constructor for use in injection. The requested objects are subclasses of
     * component or have dedicated providers, so the container will know how to
     * create this handler.
     *
     * @param executor
     *            threadpool, provided by Vespa
     * @param accessLog
     *            access log for incoming queries, provided by Vespa
     * @param searchHandler
     *            the Vespa search handler, also automatically injected
     */
    @Inject
    public DemoHandler(Executor executor, AccessLog accessLog,
                       SearchHandler searchHandler) {
        super(executor, accessLog, null, true);
        this.searchHandler = searchHandler;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        HttpRequest searchRequest = new HttpRequest.Builder(request)
                .put(Model.QUERY_STRING, "Red Hat")
                .put(Presentation.FORMAT, "demo").createDirectRequest();
        HttpResponse r = searchHandler.handle(searchRequest);
        return r;
    }
}
