// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.google.inject.Inject;
import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.feedapi.DocprocMessageProcessor;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.Feeder;
import com.yahoo.feedapi.JsonFeeder;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.feedapi.SingleSender;
import com.yahoo.feedapi.XMLFeeder;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespaclient.config.FeederConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;
import java.util.logging.Logger;


/**
 * Feed documents from a com.yahoo.container.handler.Request.
 *
 * @author Thomas Gundersen
 * @author steinar
 */
public final class VespaFeedHandler extends VespaFeedHandlerBase {

    private final static Logger log = Logger.getLogger(VespaFeedHandler.class.getName());
    public static final String JSON_INPUT = "jsonInput";

    private AtomicInteger busyThreads = new AtomicInteger(0);
    private final int maxBusyThreads;


    @Inject
    public VespaFeedHandler(FeederConfig feederConfig, 
                            LoadTypeConfig loadTypeConfig, 
                            DocumentmanagerConfig documentmanagerConfig,
                            SlobroksConfig slobroksConfig,
                            ClusterListConfig clusterListConfig,
                            Executor executor, 
                            Metric metric) throws Exception {
        super(feederConfig, loadTypeConfig, documentmanagerConfig, slobroksConfig, clusterListConfig, executor, metric);
        this.maxBusyThreads = feederConfig.maxbusythreads();
    }

    VespaFeedHandler(FeedContext context, Executor executor) throws Exception {
        super(context, executor);
        this.maxBusyThreads = 32;
    }

    public static VespaFeedHandler createFromContext(FeedContext context, Executor executor) throws Exception {
        return new VespaFeedHandler(context, executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return handle(request, (RouteMetricSet.ProgressCallback)null);
    }

    public HttpResponse handle(HttpRequest request, RouteMetricSet.ProgressCallback callback) {
        if (request.getProperty("status") != null) {
            return new MetricResponse(context.getMetrics().getMetricSet());
        }
        try {
            int busy = busyThreads.incrementAndGet();
            if (busy > maxBusyThreads) {
                log.warning("too many threads [" + busy + "] busy, returning SERVICE UNAVAILABLE");
                return new EmptyResponse(com.yahoo.jdisc.http.HttpResponse.Status.SERVICE_UNAVAILABLE);
            }
            boolean asynchronous = request.getBooleanProperty("asynchronous");

            MessagePropertyProcessor.PropertySetter properties = getPropertyProcessor().buildPropertySetter(request);

            String route = properties.getRoute().toString();
            FeedResponse response = new FeedResponse(new RouteMetricSet(route, callback));

            SingleSender sender = new SingleSender(response, getSharedSender(route), !asynchronous);
            sender.addMessageProcessor(properties);
            sender.addMessageProcessor(new DocprocMessageProcessor(getDocprocChain(request), getDocprocServiceRegistry(request)));

            Feeder feeder = createFeeder(sender, request);
            feeder.setAbortOnDocumentError(properties.getAbortOnDocumentError());
            feeder.setCreateIfNonExistent(properties.getCreateIfNonExistent());
            response.setAbortOnFeedError(properties.getAbortOnFeedError());

            List<String> errors = feeder.parse();
            for (String s : errors) {
                response.addXMLParseError(s);
            }
            if (errors.size() > 0 && feeder instanceof XMLFeeder) {
                response.addXMLParseError("If you are trying to feed JSON, set the Content-Type header to application/json.");
            }

            sender.done();

            if (asynchronous) {
                return response;
            }
            long millis = getTimeoutMillis(request);
            boolean completed = sender.waitForPending(millis);
            if (!completed) {
                response.addError("Timed out after " + millis + " ms waiting for responses");
            }
            response.done();
            return response;
        } finally {
            busyThreads.decrementAndGet();
        }
    }

    private Feeder createFeeder(SingleSender sender, HttpRequest request) {
        String contentType = request.getHeader("Content-Type");
        if (Boolean.valueOf(request.getProperty(JSON_INPUT)) || (contentType != null && contentType.startsWith("application/json"))) {
            return new JsonFeeder(getDocumentTypeManager(), sender, getRequestInputStream(request));
        } else {
            return new XMLFeeder(getDocumentTypeManager(), sender, getRequestInputStream(request));
        }
    }

}
