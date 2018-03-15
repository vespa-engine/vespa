// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.google.inject.Inject;
import com.yahoo.clientmetrics.ClientMetrics;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.docproc.DocprocService;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.feedapi.SharedSender;
import com.yahoo.jdisc.Metric;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespaclient.config.FeederConfig;
import org.brotli.dec.BrotliInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;

public abstract class VespaFeedHandlerBase extends ThreadedHttpRequestHandler {

    protected FeedContext context;
    private final long defaultTimeoutMillis;

    @Inject
    public VespaFeedHandlerBase(FeederConfig feederConfig,
                                LoadTypeConfig loadTypeConfig,
                                DocumentmanagerConfig documentmanagerConfig,
                                SlobroksConfig slobroksConfig,
                                ClusterListConfig clusterListConfig,
                                Executor executor,
                                Metric metric) throws Exception {
        this(FeedContext.getInstance(feederConfig, loadTypeConfig, documentmanagerConfig, 
                                     slobroksConfig, clusterListConfig, metric), 
             executor, (long)feederConfig.timeout() * 1000);
    }

    public VespaFeedHandlerBase(FeedContext context, Executor executor) throws Exception {
        this(context, executor, context.getPropertyProcessor().getDefaultTimeoutMillis());
    }

    public VespaFeedHandlerBase(FeedContext context, Executor executor, long defaultTimeoutMillis) throws Exception {
        super(executor, context.getMetricAPI());
        this.context = context;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    public SharedSender getSharedSender(String route) {
        return context.getSharedSender(route);
    }

    public DocprocService getDocprocChain(HttpRequest request) {
        return context.getPropertyProcessor().getDocprocChain(request);
    }

    public ComponentRegistry<DocprocService> getDocprocServiceRegistry(HttpRequest request) {
        return context.getPropertyProcessor().getDocprocServiceRegistry(request);
    }

    public MessagePropertyProcessor getPropertyProcessor() {
        return context.getPropertyProcessor();
    }

    /**
     * @param request Request object to get the POST data stream from
     * @return An InputStream that either is a GZIP wrapper or simply the
     *         original data stream.
     * @throws IllegalArgumentException if GZIP stream creation failed
     */
    public InputStream getRequestInputStream(HttpRequest request) {
        if ("br".equals(request.getHeader("Content-Encoding"))) {
            try {
                return new BrotliInputStream(request.getData());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to create Brotli input stream from content", e);
            }
        }
        else if ("gzip".equals(request.getHeader("Content-Encoding"))) {
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

    public ClientMetrics getMetrics() {
        return context.getMetrics();
    }

    protected long getTimeoutMillis(HttpRequest request) {
        return ParameterParser.asMilliSeconds(request.getProperty("timeout"), defaultTimeoutMillis);
    }
    
}
