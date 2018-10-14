// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespa.http.client.config.FeedParams.DataFormat;
import com.yahoo.vespa.http.client.core.Headers;

/**
 * Wrapper for the feed feederSettings read from HTTP request.
 *
 * @author Steinar Knutsen
 */
public class FeederSettings {

    private static final Route DEFAULT_ROUTE = Route.parse("default");
    public final boolean drain;
    public final Route route;
    public final boolean denyIfBusy;
    public final DataFormat dataFormat;
    public final String priority;
    public final Integer traceLevel;

    public FeederSettings(HttpRequest request) {
        {
            String tmpDrain = request.getHeader(Headers.DRAIN);
            if (tmpDrain != null) {
                drain = Boolean.parseBoolean(tmpDrain);
            } else {
                drain = false;
            }
        }
        {
            String tmpRoute = request.getHeader(Headers.ROUTE);
            if (tmpRoute != null) {
                route = Route.parse(tmpRoute);
            } else {
                route = DEFAULT_ROUTE;
            }
        }
        {
            String tmpDenyIfBusy = request.getHeader(Headers.DENY_IF_BUSY);
            if (tmpDenyIfBusy != null) {
                denyIfBusy = Boolean.parseBoolean(tmpDenyIfBusy);
            } else {
                denyIfBusy = false;
            }
        }
        {
            String tmpDataFormat = request.getHeader(Headers.DATA_FORMAT);
            if (tmpDataFormat != null) {
                dataFormat = DataFormat.valueOf(tmpDataFormat);
            } else {
                dataFormat = DataFormat.XML_UTF8;
            }
        }
        {
            String tmpDataFormat = request.getHeader(Headers.PRIORITY);
            if (tmpDataFormat != null) {
                priority = tmpDataFormat;
            } else {
                priority = null;
            }
        }
        {
            String tmpDataFormat = request.getHeader(Headers.TRACE_LEVEL);
            if (tmpDataFormat != null) {
                traceLevel = Integer.valueOf(tmpDataFormat);
            } else {
                traceLevel = null;
            }
        }
    }

}
