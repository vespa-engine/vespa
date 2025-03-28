// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;

/**
 * A {@link RequestLog} implementation that collects statistics like HTTP response types aggregated by category.
 * Implemented as a {@link RequestLog} instead of {@link org.eclipse.jetty.server.handler.EventsHandler} to ensure
 * that 400 responses from e.g. invalid request headers are also included.
 *
 * @author ollivir
 * @author bjorncs
 */
class MetricAggregatingRequestLog implements RequestLog {

    static final String requestTypeAttribute = "requestType";

    private final List<String> monitoringHandlerPaths;
    private final List<String> searchHandlerPaths;
    private final Set<String> ignoredUserAgents;
    private final boolean reporterEnabled;

    private final ConcurrentMap<StatusCodeMetric, LongAdder> statistics = new ConcurrentHashMap<>();

    MetricAggregatingRequestLog(ServerConfig.Metric cfg) {
        this(cfg.monitoringHandlerPaths(), cfg.searchHandlerPaths(), cfg.ignoredUserAgents(), cfg.reporterEnabled());
    }

    MetricAggregatingRequestLog(List<String> monitoringHandlerPaths, List<String> searchHandlerPaths,
                                List<String> ignoredUserAgents, boolean reporterEnabled) {
        this.monitoringHandlerPaths = monitoringHandlerPaths;
        this.searchHandlerPaths = searchHandlerPaths;
        this.ignoredUserAgents = Set.copyOf(ignoredUserAgents);
        this.reporterEnabled = reporterEnabled;
    }

    static MetricAggregatingRequestLog getBean(JettyHttpServer server) { return getBean(server.server()); }
    static MetricAggregatingRequestLog getBean(Server server) { return server.getBean(MetricAggregatingRequestLog.class); }

    @Override public void log(Request req, Response resp) { onResponse(req, resp.getStatus()); }

    void onResponse(Request request, int status) {
        if (shouldLogMetricsFor(request)) {
            var metrics = StatusCodeMetric.of(request, status, monitoringHandlerPaths, searchHandlerPaths);
            metrics.forEach(metric -> statistics.computeIfAbsent(metric, __ -> new LongAdder()).increment());
        }
    }

    List<StatisticsEntry> takeStatistics() {
        if (reporterEnabled)
            throw new IllegalStateException("Cannot take consistent snapshot while reporter is enabled");
        var ret = new ArrayList<StatisticsEntry>();
        consume((metric, value) -> ret.add(new StatisticsEntry(metric, value)));
        return ret;
    }

    void reportSnapshot(Metric metricAggregator) {
        if (!reporterEnabled) throw new IllegalStateException("Reporter is not enabled");
        consume((metric, value) -> {
            Metric.Context ctx = metricAggregator.createContext(metric.dimensions.asMap());
            metricAggregator.add(metric.name, value, ctx);
        });
    }

    private boolean shouldLogMetricsFor(Request request) {
        String agent = request.getHeaders().get(HttpHeader.USER_AGENT);
        if (agent == null) return true;
        return ! ignoredUserAgents.contains(agent);
    }

    private void consume(ObjLongConsumer<StatusCodeMetric> consumer) {
        statistics.forEach((metric, adder) -> {
            long value = adder.sumThenReset();
            if (value > 0) consumer.accept(metric, value);
        });
    }

    static class Dimensions {
        final String protocol;
        final String scheme;
        final String method;
        final String requestType;
        final int statusCode;

        private Dimensions(String protocol, String scheme, String method, String requestType, int statusCode) {
            this.protocol = protocol;
            this.scheme = scheme;
            this.method = method;
            this.requestType = requestType;
            this.statusCode = statusCode;
        }

        static Dimensions of(Request req, int statusCode, List<String> monitoringHandlerPaths,
                             List<String> searchHandlerPaths) {
            String requestType = requestType(req, monitoringHandlerPaths, searchHandlerPaths);
            // note: some request members may not be populated for invalid requests, e.g. invalid request-line.
            return new Dimensions(protocol(req), scheme(req), method(req), requestType, statusCode);
        }

        Map<String, Object> asMap() {
            Map<String, Object> builder = new HashMap<>();
            builder.put(MetricDefinitions.PROTOCOL_DIMENSION, protocol);
            builder.put(MetricDefinitions.METHOD_DIMENSION, method);
            builder.put(MetricDefinitions.REQUEST_TYPE_DIMENSION, requestType);
            builder.put(MetricDefinitions.STATUS_CODE_DIMENSION, (long) statusCode);
            return Map.copyOf(builder);
        }

        private static String protocol(Request req) {
            var protocol = req.getConnectionMetaData().getProtocol();
            if (protocol == null) return "none";
            return switch (protocol) {
                case "HTTP/1", "HTTP/1.0", "HTTP/1.1" -> "http1";
                case "HTTP/2", "HTTP/2.0" -> "http2";
                default -> "other";
            };
        }

        private static String scheme(Request req) {
            var scheme = req.getHttpURI().getScheme();
            if (scheme == null) return "none";
            return switch (scheme) {
                case "http", "https" -> scheme;
                default -> "other";
            };
        }

        private static String method(Request req) {
            var method = req.getMethod();
            if (method == null) return "none";
            return switch (method) {
                case "GET", "PATCH", "POST", "PUT", "DELETE", "OPTIONS", "HEAD" -> method;
                default -> "other";
            };
        }

        private static String requestType(Request req, List<String> monitoringHandlerPaths,
                                          List<String> searchHandlerPaths) {
            HttpRequest.RequestType requestType = (HttpRequest.RequestType)req.getAttribute(requestTypeAttribute);
            if (requestType != null) return requestType.name().toLowerCase();
            // Deduce from path and method:
            String path = req.getHttpURI().getPath();
            if (path == null) return "none";
            for (String monitoringHandlerPath : monitoringHandlerPaths) {
                if (path.startsWith(monitoringHandlerPath)) return "monitoring";
            }
            for (String searchHandlerPath : searchHandlerPaths) {
                if (path.startsWith(searchHandlerPath)) return "read";
            }
            var method = req.getMethod();
            if (method == null) return "none";
            else if ("GET".equals(method)) return "read";
            else return "write";
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Dimensions that = (Dimensions) o;
            return statusCode == that.statusCode && Objects.equals(protocol, that.protocol)
                    && Objects.equals(scheme, that.scheme) && Objects.equals(method, that.method)
                    && Objects.equals(requestType, that.requestType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocol, scheme, method, requestType, statusCode);
        }

    }

    static class StatusCodeMetric {
        final Dimensions dimensions;
        final String name;

        private StatusCodeMetric(Dimensions dimensions, String name) {
            this.dimensions = dimensions;
            this.name = name;
        }

        static Set<StatusCodeMetric> of(Request req, int statusCode, List<String> monitoringHandlerPaths,
                                   List<String> searchHandlerPaths) {
            Dimensions dimensions = Dimensions.of(req, statusCode, monitoringHandlerPaths, searchHandlerPaths);
            return metricNames(statusCode).stream()
                    .map(name -> new StatusCodeMetric(dimensions, name))
                    .collect(Collectors.toSet());
        }

        private static Set<String> metricNames(int statusCode) {
            if (statusCode < 200) return Set.of(MetricDefinitions.RESPONSES_1XX);
            else if (statusCode < 300) return Set.of(MetricDefinitions.RESPONSES_2XX);
            else if (statusCode < 400) return Set.of(MetricDefinitions.RESPONSES_3XX);
            else if (statusCode < 500) return Set.of(MetricDefinitions.RESPONSES_4XX);
            else return Set.of(MetricDefinitions.RESPONSES_5XX);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatusCodeMetric that = (StatusCodeMetric) o;
            return Objects.equals(dimensions, that.dimensions) && Objects.equals(name, that.name);
        }

        @Override public int hashCode() { return Objects.hash(dimensions, name); }
    }

    static class StatisticsEntry {
        final Dimensions dimensions;
        final String name;
        final long value;

        StatisticsEntry(StatusCodeMetric metric, long value) {
            this.dimensions = metric.dimensions;
            this.name = metric.name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatisticsEntry that = (StatisticsEntry) o;
            return value == that.value && Objects.equals(dimensions, that.dimensions) && Objects.equals(name, that.name);
        }

        @Override public int hashCode() { return Objects.hash(dimensions, name, value); }
    }
}
