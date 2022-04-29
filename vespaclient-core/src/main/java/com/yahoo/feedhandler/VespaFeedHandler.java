// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.protect.Error;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.Feeder;
import com.yahoo.feedapi.JsonFeeder;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.feedapi.SimpleFeedAccess;
import com.yahoo.feedapi.SingleSender;
import com.yahoo.feedapi.XMLFeeder;

import java.util.List;


/**
 * Feed documents from a com.yahoo.container.handler.Request.
 *
 * @author Thomas Gundersen
 * @author steinar
 */
public final class VespaFeedHandler extends VespaFeedHandlerBase {

    public static final String JSON_INPUT = "jsonInput";

    private VespaFeedHandler(FeedContext context) {
        super(context);
    }

    public static VespaFeedHandler createFromContext(FeedContext context) {
        return new VespaFeedHandler(context);
    }

    public FeedResponse handle(HttpRequest request, RouteMetricSet.ProgressCallback callback, int numThreads) {
        MessagePropertyProcessor.PropertySetter properties = getPropertyProcessor().buildPropertySetter(request);

        String route = properties.getRoute().toString();
        FeedResponse response = new FeedResponse(new RouteMetricSet(route, callback));

        SingleSender sender = new SingleSender(response, getSharedSender(route));
        sender.addMessageProcessor(properties);
        ThreadedFeedAccess feedAccess = new ThreadedFeedAccess(numThreads, sender);
        Feeder feeder = createFeeder(feedAccess, request);
        feeder.setAbortOnDocumentError(properties.getAbortOnDocumentError());
        feeder.setCreateIfNonExistent(properties.getCreateIfNonExistent());
        response.setAbortOnFeedError(properties.getAbortOnFeedError());

        List<String> errors = feeder.parse();
        for (String s : errors) {
            response.addXMLParseError(s);
        }

        sender.done();
        feedAccess.close();
        long millis = getTimeoutMillis(request);
        boolean completed = sender.waitForPending(millis);
        if (!completed) {
            response.addError(Error.TIMEOUT, "Timed out after " + millis + " ms waiting for responses");
        }
        response.done();
        return response;

    }

    private Feeder createFeeder(SimpleFeedAccess sender, HttpRequest request) {
        if ( ! Boolean.valueOf(request.getProperty(JSON_INPUT))) {
            return new XMLFeeder(getDocumentTypeManager(), sender, getRequestInputStream(request));
        }
        return new JsonFeeder(getDocumentTypeManager(), sender, getRequestInputStream(request));
    }

}
