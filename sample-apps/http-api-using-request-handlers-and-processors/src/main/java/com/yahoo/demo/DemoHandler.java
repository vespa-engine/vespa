// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.processing.handler.ProcessingHandler;

import java.util.concurrent.Executor;

/**
 * Annotate an incoming request with the URI string used to query from the
 * network, and pass the request on to the processing handler.
 */
public class DemoHandler extends ThreadedHttpRequestHandler {

    /** The name used by the processing handler to choose output renderer. */
    private static final String FORMAT = "format";

    /** The property name for the incoming URI as a string. */
    public static final String REQUEST_URI = "request.uri";

    private final ProcessingHandler processingHandler;

    /**
     * Constructor for use in injection. The requested objects are subclasses of
     * component or have dedicated providers, so the container will know how to
     * create this handler.
     *
     * @param executor
     *            threadpool, provided by the container
     * @param processingHandler
     *            the processing handler, also automatically injected
     */
    @Inject
    public DemoHandler(Executor executor, ProcessingHandler processingHandler) {
        super(executor, null, true);
        this.processingHandler = processingHandler;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        // We have implemented #handle(HttpRequest, ContentChannel) to be
        // able to use the ProcessingHandler, so this will never be called.
        // An implementation is needed, though, as the method is abstract.
        throw new UnsupportedOperationException("See #handle(HttpRequest, ContentChannel)");
    }

    @Override
    public HttpResponse handle(HttpRequest request, ContentChannel channel) {
        HttpRequest.Builder builder =
                new HttpRequest.Builder(request).put(REQUEST_URI, request.getUri().toString());
        setFormat(builder, request);
        return processingHandler.handle(builder.createDirectRequest(),
                channel);
    }

    /**
     * Set the output format to the renderer with id = "demo" in services.xml if
     * no explicit format parameter is present. This allows using e.g. the
     * default processing renderer by adding <code>&amp;format=default</code> to
     * the HTTP request.
     *
     * @param builder
     *            the mutable builder instance used for creating the forwarding request
     * @param request
     *            the incoming HTTP request
     */
    private void setFormat(HttpRequest.Builder builder, HttpRequest request) {
        if ( ! request.hasProperty(FORMAT)) {
            builder.put(FORMAT, "demo");
        }
    }
}
