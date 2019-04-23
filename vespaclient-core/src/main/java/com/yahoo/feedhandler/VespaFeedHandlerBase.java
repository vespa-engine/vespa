// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.docproc.DocprocService;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.feedapi.SharedSender;
import com.yahoo.search.query.ParameterParser;


import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;

public abstract class VespaFeedHandlerBase extends ThreadedHttpRequestHandler {

    protected FeedContext context;
    private final long defaultTimeoutMillis;

    VespaFeedHandlerBase(FeedContext context, Executor executor) {
        this(context, executor, context.getPropertyProcessor().getDefaultTimeoutMillis());
    }

    private VespaFeedHandlerBase(FeedContext context, Executor executor, long defaultTimeoutMillis) {
        super(executor, context.getMetricAPI());
        this.context = context;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    SharedSender getSharedSender(String route) {
        return context.getSharedSender(route);
    }

    DocprocService getDocprocChain(HttpRequest request) {
        return context.getPropertyProcessor().getDocprocChain(request);
    }

    ComponentRegistry<DocprocService> getDocprocServiceRegistry(HttpRequest request) {
        return context.getPropertyProcessor().getDocprocServiceRegistry(request);
    }

    MessagePropertyProcessor getPropertyProcessor() {
        return context.getPropertyProcessor();
    }

    /**
     * @param request Request object to get the POST data stream from
     * @return An InputStream that either is a GZIP wrapper or simply the
     *         original data stream.
     * @throws IllegalArgumentException if GZIP stream creation failed
     */
    InputStream getRequestInputStream(HttpRequest request) {
        if ("gzip".equals(request.getHeader("Content-Encoding"))) {
            try {
                return new GZIPInputStream(request.getData());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to create GZIP input stream from content", e);
            }
        } else {
            return request.getData();
        }
    }

    protected DocumentTypeManager getDocumentTypeManager() {
        return context.getDocumentTypeManager();
    }

    protected long getTimeoutMillis(HttpRequest request) {
        return ParameterParser.asMilliSeconds(request.getProperty("timeout"), defaultTimeoutMillis);
    }
    
}
