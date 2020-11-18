// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.servlet.ServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.jdisc.http.server.jetty.JDiscHttpServlet.getConnector;

/**
 * Resolve request/response filter (chain) based on {@link FilterBindings}.
 *
 * @author bjorncs
 */
class FilterResolver {

    private final FilterBindings bindings;
    private final Metric metric;

    FilterResolver(FilterBindings bindings, Metric metric) {
        this.bindings = bindings;
        this.metric = metric;
    }

    Optional<RequestFilter> resolveRequestFilter(HttpServletRequest servletRequest, URI jdiscUri) {
        Optional<String> maybeFilterId = bindings.resolveRequestFilter(jdiscUri, getConnector(servletRequest).listenPort());
        if (maybeFilterId.isPresent()) {
            metric.add(MetricDefinitions.FILTERING_REQUEST_HANDLED, 1L, createMetricContext(servletRequest, maybeFilterId.get()));
            servletRequest.setAttribute(ServletRequest.JDISC_REQUEST_CHAIN, maybeFilterId.get());
        } else {
            metric.add(MetricDefinitions.FILTERING_REQUEST_UNHANDLED, 1L, createMetricContext(servletRequest, null));
        }
        return maybeFilterId.map(bindings::getRequestFilter);
    }

    Optional<ResponseFilter> resolveResponseFilter(HttpServletRequest servletRequest, URI jdiscUri) {
        Optional<String> maybeFilterId = bindings.resolveResponseFilter(jdiscUri, getConnector(servletRequest).listenPort());
        if (maybeFilterId.isPresent()) {
            metric.add(MetricDefinitions.FILTERING_RESPONSE_HANDLED, 1L, createMetricContext(servletRequest, maybeFilterId.get()));
            servletRequest.setAttribute(ServletRequest.JDISC_RESPONSE_CHAIN, maybeFilterId.get());
        } else {
            metric.add(MetricDefinitions.FILTERING_RESPONSE_UNHANDLED, 1L, createMetricContext(servletRequest, null));
        }
        return maybeFilterId.map(bindings::getResponseFilter);
    }

    private Metric.Context createMetricContext(HttpServletRequest request, String filterId) {
        Map<String, String> extraDimensions = filterId != null
                ? Map.of(MetricDefinitions.FILTER_CHAIN_ID_DIMENSION, filterId)
                : Map.of();
        return JDiscHttpServlet.getConnector(request).createRequestMetricContext(request, extraDimensions);
    }
}
