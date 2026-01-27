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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Common HTTP request handler metrics code.
 *
 * @author jonmv
 * @author bjorncs
 */
public class HandlerMetricContextUtil {

    private record MetricContextKey(
            String handlerBinding, String handlerClassName, String endpoint, int port) {}

    private final ConcurrentHashMap<MetricContextKey, Metric.Context> metricContexts = new ConcurrentHashMap<>();
    private final Metric metric;
    private final String handlerClassName;

    public HandlerMetricContextUtil(Metric metric, String handlerClassName) {
        this.metric = metric;
        this.handlerClassName = handlerClassName;
    }

    public  void onHandle(Request request) {
        metric.add(ContainerMetrics.HANDLED_REQUESTS.baseName(), 1, contextFor(request));
    }

    public void onHandled(Request request) {
        metric.set(ContainerMetrics.HANDLED_LATENCY.baseName(), request.timeElapsed(TimeUnit.MILLISECONDS), contextFor(request));
    }

    public void onUnhandledException(Request request) {
        metric.add(ContainerMetrics.JDISC_HTTP_HANDLER_UNHANDLED_EXCEPTIONS.baseName(), 1, contextFor(request));
    }

    private Metric.Context contextFor(Request request) {
        return metricContexts.computeIfAbsent(
                new MetricContextKey(
                        handlerBinding(request).orElse(null),
                        handlerClassName,
                        request.headers().containsKey("Host") ? request.headers().get("Host").get(0) : null,
                        request.getUri().getPort()),
                key -> {
                    Map<String, String> dimensions = new HashMap<>();
                    if (key.handlerBinding != null) dimensions.put("handler", key.handlerBinding);
                    dimensions.put("handler-name", key.handlerClassName);
                    if (key.endpoint != null) dimensions.put("endpoint", key.endpoint);
                    dimensions.put("port", String.valueOf(key.port));
                    return metric.createContext(dimensions);
                }
        );
    }

    private static Optional<String> handlerBinding(Request request) {
        BindingMatch<?> match = request.getBindingMatch();
        if (match == null) return Optional.empty();
        UriPattern matched = match.matched();
        if (matched == null) return Optional.empty();
        return Optional.ofNullable(matched.toString());
    }
}
