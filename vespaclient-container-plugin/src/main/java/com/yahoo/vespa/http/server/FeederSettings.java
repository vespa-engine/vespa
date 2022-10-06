// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.messagebus.routing.Route;

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
    public final FeedParams.DataFormat dataFormat;
    public final Integer traceLevel;

    public FeederSettings(HttpRequest request) {
        this.drain = Optional.ofNullable(request.getHeader(Headers.DRAIN)).map(Boolean::parseBoolean).orElse(false);
        this.route = Optional.ofNullable(request.getHeader(Headers.ROUTE)).map(Route::parse).orElse(DEFAULT_ROUTE);
        this.dataFormat = Optional.ofNullable(request.getHeader(Headers.DATA_FORMAT)).map(FeedParams.DataFormat::valueOf).orElse(FeedParams.DataFormat.JSON_UTF8);
        this.traceLevel = Optional.ofNullable(request.getHeader(Headers.TRACE_LEVEL)).map(Integer::valueOf).orElse(null);
    }

}
