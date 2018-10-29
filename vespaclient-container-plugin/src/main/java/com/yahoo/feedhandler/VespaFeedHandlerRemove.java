// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.google.inject.Inject;
import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.protect.Error;
import com.yahoo.document.DocumentId;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.feedapi.SingleSender;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespaclient.config.FeederConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executor;

/**
 * @deprecated Legacy API. Will be removed in Vespa 7
 */
// TODO: Remove on Vespa 7
@Deprecated // OK
public class VespaFeedHandlerRemove extends VespaFeedHandlerBase {

    @Inject
    public VespaFeedHandlerRemove(FeederConfig feederConfig, 
                                  LoadTypeConfig loadTypeConfig,
                                  DocumentmanagerConfig documentmanagerConfig,
                                  SlobroksConfig slobroksConfig,
                                  ClusterListConfig clusterListConfig,
                                  Executor executor, 
                                  Metric metric) throws Exception {
        super(feederConfig, loadTypeConfig, documentmanagerConfig, slobroksConfig, clusterListConfig, executor, metric);
    }

    VespaFeedHandlerRemove(FeedContext context, Executor executor) throws Exception {
        super(context, executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getProperty("status") != null) {
            return new MetricResponse(context.getMetrics().getMetricSet());
        }

        MessagePropertyProcessor.PropertySetter properties = getPropertyProcessor().buildPropertySetter(request);
        String route = properties.getRoute().toString();
        FeedResponse response = new FeedResponse(new RouteMetricSet(route, null));
        long timeoutMillis = getTimeoutMillis(request);
        SingleSender sender = new SingleSender(response, (int) timeoutMillis, getSharedSender(route));
        sender.addMessageProcessor(properties);

        response.setAbortOnFeedError(properties.getAbortOnFeedError());

        if (request.hasProperty("id")) {
            sender.remove(new DocumentId(request.getProperty("id")));
        } else if (request.hasProperty("id[0]")) {
            int index = 0;
            while (request.hasProperty("id[" + index + "]")) {
                sender.remove(new DocumentId(request.getProperty("id[" + index + "]")));
                ++index;
            }
        }

        if (request.getData() != null) {
            try {
                String line;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getRequestInputStream(request), "UTF-8"));
                while ((line = reader.readLine()) != null) {
                    sender.remove(new DocumentId(line));
                }
            } catch (Exception e) {
                response.addError(e.getClass() + ": " + e.getCause());
            }
        }

        sender.done();
        boolean completed = sender.waitForPending(timeoutMillis);
        if ( ! completed)
            response.addError(Error.TIMEOUT, "Timed out after "+timeoutMillis+" ms waiting for responses");
        return response;
    }

}
