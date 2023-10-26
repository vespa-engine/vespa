// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import org.eclipse.jetty.server.Request;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnector;

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

    Optional<RequestFilter> resolveRequestFilter(Request request, URI jdiscUri) {
        Optional<String> maybeFilterId = bindings.resolveRequestFilter(jdiscUri, getConnector(request).listenPort());
        if (maybeFilterId.isPresent()) {
            metric.add(MetricDefinitions.FILTERING_REQUEST_HANDLED, 1L, createMetricContext(request, maybeFilterId.get()));
            request.setAttribute(RequestUtils.JDISC_REQUEST_CHAIN, maybeFilterId.get());
        } else if (!bindings.strictFiltering()) {
            metric.add(MetricDefinitions.FILTERING_REQUEST_UNHANDLED, 1L, createMetricContext(request, null));
        } else {
            String syntheticFilterId = RejectingRequestFilter.SYNTHETIC_FILTER_CHAIN_ID;
            metric.add(MetricDefinitions.FILTERING_REQUEST_HANDLED, 1L, createMetricContext(request, syntheticFilterId));
            request.setAttribute(RequestUtils.JDISC_REQUEST_CHAIN, syntheticFilterId);
            return Optional.of(RejectingRequestFilter.INSTANCE);
        }
        return maybeFilterId.map(bindings::getRequestFilter);
    }

    Optional<ResponseFilter> resolveResponseFilter(Request request, URI jdiscUri) {
        Optional<String> maybeFilterId = bindings.resolveResponseFilter(jdiscUri, getConnector(request).listenPort());
        if (maybeFilterId.isPresent()) {
            metric.add(MetricDefinitions.FILTERING_RESPONSE_HANDLED, 1L, createMetricContext(request, maybeFilterId.get()));
            request.setAttribute(RequestUtils.JDISC_RESPONSE_CHAIN, maybeFilterId.get());
        } else {
            metric.add(MetricDefinitions.FILTERING_RESPONSE_UNHANDLED, 1L, createMetricContext(request, null));
        }
        return maybeFilterId.map(bindings::getResponseFilter);
    }

    private Metric.Context createMetricContext(Request request, String filterId) {
        Map<String, String> extraDimensions = filterId != null
                ? Map.of(MetricDefinitions.FILTER_CHAIN_ID_DIMENSION, filterId)
                : Map.of();
        return getConnector(request).createRequestMetricContext(request, extraDimensions);
    }

    private static class RejectingRequestFilter extends NoopSharedResource implements RequestFilter {

        private static final RejectingRequestFilter INSTANCE = new RejectingRequestFilter();
        private static final String SYNTHETIC_FILTER_CHAIN_ID = "strict-reject";

        @Override
        public void filter(HttpRequest request, ResponseHandler handler) {
            Response response = new Response(Response.Status.FORBIDDEN);
            response.headers().add("Content-Type", "text/plain");
            try (FastContentWriter writer = ResponseDispatch.newInstance(response).connectFastWriter(handler)) {
                writer.write("Request did not match any request filter chain");
            }
        }
    }

}
