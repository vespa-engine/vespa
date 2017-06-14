// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import java.util.concurrent.Executor;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.metrics.MetricManager;
import com.yahoo.metrics.MetricSet;
import com.yahoo.vespaclient.config.FeederConfig;

public class VespaFeedHandlerStatus extends ThreadedHttpRequestHandler {

    private MetricManager manager;

    public VespaFeedHandlerStatus(FeederConfig feederConfig, 
                                  LoadTypeConfig loadTypeConfig,
                                  DocumentmanagerConfig documentmanagerConfig, 
                                  SlobroksConfig slobroksConfig,
                                  ClusterListConfig clusterListConfig,
                                  Executor executor) {
        this(FeedContext.getInstance(feederConfig, loadTypeConfig, documentmanagerConfig, slobroksConfig, 
                                     clusterListConfig, new NullFeedMetric()), true, true, executor);
    }

    VespaFeedHandlerStatus(FeedContext context, boolean doLog, boolean makeSnapshots, Executor executor) {
        super(executor);
        manager = new MetricManager();
        final MetricSet metricSet = context.getMetrics().getMetricSet();
        metricSet.unregister();
        manager.registerMetric(metricSet);
        if (doLog) {
            manager.addMetricToConsumer("log", "routes.total.putdocument.count");
            manager.addMetricToConsumer("log", "routes.total.removedocument.count");
            manager.addMetricToConsumer("log", "routes.total.updatedocument.count");
            manager.addMetricToConsumer("log", "routes.total.getdocument.count");

            manager.addMetricToConsumer("log", "routes.total.putdocument.errors.total");
            manager.addMetricToConsumer("log", "routes.total.removedocument.errors.total");
            manager.addMetricToConsumer("log", "routes.total.updatedocument.errors.total");
            manager.addMetricToConsumer("log", "routes.total.getdocument.errors.total");

            manager.addMetricToConsumer("log", "routes.total.putdocument.latency");
            manager.addMetricToConsumer("log", "routes.total.removedocument.latency");
            manager.addMetricToConsumer("log", "routes.total.updatedocument.latency");
            manager.addMetricToConsumer("log", "routes.total.getdocument.latency");
        }

        if (doLog || makeSnapshots) {
            new Thread(manager).start();
        }
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            return new StatusResponse(manager, asInt(request.getProperty("verbosity"), 0), asInt(request.getProperty("snapshotperiod"), 0));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int asInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        return Integer.parseInt(value);
    }

    @Override
    public void destroy() {
        manager.stop();
    }

}
