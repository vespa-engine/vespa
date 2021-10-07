// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespa.http.client.config.FeedParams.DataFormat;
import com.yahoo.vespa.http.client.core.Headers;

import java.util.Optional;

/**
 * Wrapper for the feed feederSettings read from HTTP request.
 *
 * @author Steinar Knutsen
 */
public class FeederSettings {

    private static final Route DEFAULT_ROUTE = Route.parse("default");
    public final boolean drain; // TODO: Implement drain=true
    public final Route route;
    public final DataFormat dataFormat;
    public final String priority;
    public final Integer traceLevel;

    public FeederSettings(HttpRequest request) {
        this.drain = Optional.ofNullable(request.getHeader(Headers.DRAIN)).map(Boolean::parseBoolean).orElse(false);
        this.route = Optional.ofNullable(request.getHeader(Headers.ROUTE)).map(Route::parse).orElse(DEFAULT_ROUTE);
        // TODO: Change default to JSON on Vespa 8:
        this.dataFormat = Optional.ofNullable(request.getHeader(Headers.DATA_FORMAT)).map(DataFormat::valueOf).orElse(DataFormat.XML_UTF8);
        this.priority = request.getHeader(Headers.PRIORITY);
        this.traceLevel = Optional.ofNullable(request.getHeader(Headers.TRACE_LEVEL)).map(Integer::valueOf).orElse(null);
    }

}
