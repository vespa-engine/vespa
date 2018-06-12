// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.google.inject.Inject;
import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.protect.Error;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.RemoveLocationMessage;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.feedapi.SingleSender;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespaclient.config.FeederConfig;

import java.util.concurrent.Executor;

@Deprecated
public class VespaFeedHandlerRemoveLocation extends VespaFeedHandlerBase {

    @Inject
    public VespaFeedHandlerRemoveLocation(FeederConfig feederConfig, 
                                          LoadTypeConfig loadTypeConfig, 
                                          DocumentmanagerConfig documentmanagerConfig,
                                          SlobroksConfig slobroksConfig,
                                          ClusterListConfig clusterListConfig,
                                          Executor executor, Metric metric) throws Exception {
        super(feederConfig, loadTypeConfig, documentmanagerConfig, slobroksConfig, clusterListConfig, executor, metric);
    }

    VespaFeedHandlerRemoveLocation(FeedContext context, Executor executor) throws Exception {
        super(context, executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        MessagePropertyProcessor.PropertySetter properties = getPropertyProcessor().buildPropertySetter(request);
        FeedResponse response;

        if (request.getProperty("route") == null) {
            if (context.getClusterList().getStorageClusters().size() == 0) {
                return new FeedResponse(null).addError("No storage clusters configured and no alternate route specified.");
            } else if (context.getClusterList().getStorageClusters().size() > 1) {
                return new FeedResponse(null).addError("More than one storage cluster configured and no route specified.");
            } else {
                properties.setRoute(Route.parse(context.getClusterList().getStorageClusters().get(0).getName()));
            }
        }

        response = new FeedResponse(new RouteMetricSet(properties.getRoute().toString(), null));

        SingleSender sender = new SingleSender(response, getSharedSender(properties.getRoute().toString()));
        sender.addMessageProcessor(properties);

        String user = request.getProperty("user");
        String group = request.getProperty("group");
        String selection = request.getProperty("selection");

        boolean oneFound = (user != null) ^ (group != null) ^ (selection != null);

        if (!oneFound) {
            response.addError("Exactly one of \"user\", \"group\" or \"selection\" must be specified for removelocation");
            return response;
        }

        if (user != null) {
            selection = "id.user=" + user;
        }
        if (group != null) {
            selection = "id.group=\"" + group + "\"";
        }

        sender.send(new RemoveLocationMessage(selection));
        sender.done();
        long millis = getTimeoutMillis(request);
        boolean completed = sender.waitForPending(millis);
        if ( ! completed)
            response.addError(Error.TIMEOUT, "Timed out after "+millis+" ms waiting for responses");
        return response;
    }

}
