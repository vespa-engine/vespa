// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common HTTP request handler metrics code.
 *
 * @author jonmv
 * @author bjorncs
 */
public class HandlerMetricContextUtil {

    private static final String HANDLER_START_TIME_ATTRIBUTE = HandlerMetricContextUtil.class.getName() + ".startTime";

    private record MetricContextKey(String handlerClassName, int port) {}

    private final ConcurrentHashMap<MetricContextKey, Metric.Context> metricContexts = new ConcurrentHashMap<>();
    private final Metric metric;
    private final String handlerClassName;

    public HandlerMetricContextUtil(Metric metric, String handlerClassName) {
        this.metric = metric;
        this.handlerClassName = handlerClassName;
    }

    public void onHandle(Request request) {
        request.context().put(HANDLER_START_TIME_ATTRIBUTE, System.currentTimeMillis());
        metric.add(ContainerMetrics.HANDLED_REQUESTS.baseName(), 1, contextFor(request));
    }

    public void onHandled(Request request) {
        var startTime = (Long) request.context().get(HANDLER_START_TIME_ATTRIBUTE);
        long latencyMs = startTime != null ? System.currentTimeMillis() - startTime : 0;
        metric.set(ContainerMetrics.HANDLED_LATENCY.baseName(), latencyMs, contextFor(request));
    }

    public void onUnhandledException(Request request) {
        metric.add(ContainerMetrics.JDISC_HTTP_HANDLER_UNHANDLED_EXCEPTIONS.baseName(), 1, contextFor(request));
    }

    private Metric.Context contextFor(Request request) {
        return metricContexts.computeIfAbsent(
                new MetricContextKey(handlerClassName, request.getUri().getPort()),
                key -> {
                    Map<String, String> dimensions = new HashMap<>();
                    dimensions.put("handler-name", key.handlerClassName);
                    dimensions.put("port", String.valueOf(key.port));
                    return metric.createContext(dimensions);
                }
        );
    }
}
