// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Common HTTP request handler metrics code.
 *
 * @author jonmv
 */
public class HandlerMetricContextUtil {

    public static void onHandle(Request request, Metric metric, Class<?> handlerClass) {
        metric.add(ContainerMetrics.HANDLED_REQUESTS.baseName(), 1, contextFor(request, metric, handlerClass));
    }

    public static void onHandled(Request request, Metric metric, Class<?> handlerClass) {
        metric.set(ContainerMetrics.HANDLED_LATENCY.baseName(), request.timeElapsed(TimeUnit.MILLISECONDS), contextFor(request, metric, handlerClass));
    }

    public static Metric.Context contextFor(Request request, Metric metric, Class<?> handlerClass) {
        return contextFor(request, Map.of(), metric, handlerClass);
    }

    public static Metric.Context contextFor(Request request, Map<String, String> extraDimensions, Metric metric, Class<?> handlerClass) {
        BindingMatch<?> match = request.getBindingMatch();
        if (match == null) return null;
        UriPattern matched = match.matched();
        if (matched == null) return null;
        String name = matched.toString();
        String endpoint = request.headers().containsKey("Host") ? request.headers().get("Host").get(0) : null;

        Map<String, String> dimensions = new HashMap<>(extraDimensions.size() + 5);
        dimensions.put("handler", name);
        if (endpoint != null) {
            dimensions.put("endpoint", endpoint);
        }
        URI uri = request.getUri();
        dimensions.put("scheme", uri.getScheme());
        dimensions.put("port", Integer.toString(uri.getPort()));
        dimensions.put("handler-name", handlerClass.getName());
        dimensions.putAll(extraDimensions);
        return metric.createContext(dimensions);
    }

}
