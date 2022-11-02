// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.AsyncContextEvent;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.component.Graceful;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;

/**
 * HttpResponseStatisticsCollector collects statistics about HTTP response types aggregated by category
 * (1xx, 2xx, etc). It is similar to {@link org.eclipse.jetty.server.handler.StatisticsHandler}
 * with the distinction that this class collects response type statistics grouped
 * by HTTP method and only collects the numbers that are reported as metrics from Vespa.
 *
 * @author ollivir
 */
class HttpResponseStatisticsCollector extends HandlerWrapper implements Graceful {

    static final String requestTypeAttribute = "requestType";

    private final AtomicReference<FutureCallback> shutdown = new AtomicReference<>();
    private final List<String> monitoringHandlerPaths;
    private final List<String> searchHandlerPaths;
    private final Set<String> ignoredUserAgents;

    private final AtomicLong inFlight = new AtomicLong();
    private final ConcurrentMap<StatusCodeMetric, LongAdder> statistics = new ConcurrentHashMap<>();

    HttpResponseStatisticsCollector(ServerConfig.Metric cfg) {
        this(cfg.monitoringHandlerPaths(), cfg.searchHandlerPaths(), cfg.ignoredUserAgents());
    }

    HttpResponseStatisticsCollector(List<String> monitoringHandlerPaths, List<String> searchHandlerPaths,
                                    Collection<String> ignoredUserAgents) {
        this.monitoringHandlerPaths = monitoringHandlerPaths;
        this.searchHandlerPaths = searchHandlerPaths;
        this.ignoredUserAgents = Set.copyOf(ignoredUserAgents);
    }

    private final AsyncListener completionWatcher = new AsyncListener() {

        @Override
        public void onTimeout(AsyncEvent event) { }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }

        @Override
        public void onError(AsyncEvent event) { }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            HttpChannelState state = ((AsyncContextEvent) event).getHttpChannelState();
            Request request = state.getBaseRequest();

            observeEndOfRequest(request, null);
        }
    };

    @Override
    public void handle(String path, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        inFlight.incrementAndGet();

        try {
            Handler handler = getHandler();
            if (handler != null && shutdown.get() == null && isStarted()) {
                handler.handle(path, baseRequest, request, response);
            } else if ( ! baseRequest.isHandled()) {
                baseRequest.setHandled(true);
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE_503);
            }
        } finally {
            HttpChannelState state = baseRequest.getHttpChannelState();
            if (state.isSuspended()) {
                if (state.isInitial()) {
                    state.addListener(completionWatcher);
                }
            } else if (state.isInitial()) {
                observeEndOfRequest(baseRequest, response);
            }
        }
    }

    private boolean shouldLogMetricsFor(Request request) {
        String agent = request.getHeader(HttpHeader.USER_AGENT.toString());
        if (agent == null) return true;
        return ! ignoredUserAgents.contains(agent);
    }

    private void observeEndOfRequest(Request request, HttpServletResponse flushableResponse) throws IOException {
        if (shouldLogMetricsFor(request)) {
            var metrics = StatusCodeMetric.of(request, monitoringHandlerPaths, searchHandlerPaths);
            metrics.forEach(metric ->
                            statistics.computeIfAbsent(metric, __ -> new LongAdder())
                            .increment());
        }
        long live = inFlight.decrementAndGet();
        FutureCallback shutdownCb = shutdown.get();
        if (shutdownCb != null) {
            if (flushableResponse != null) {
                flushableResponse.flushBuffer();
            }
            if (live == 0) {
                shutdownCb.succeeded();
            }
        }
    }

    List<StatisticsEntry> takeStatistics() {
        var ret = new ArrayList<StatisticsEntry>();
        consume((metric, value) -> ret.add(new StatisticsEntry(metric, value)));
        return ret;
    }

    void reportSnapshot(Metric metricAggregator) {
        consume((metric, value) -> {
            Metric.Context ctx = metricAggregator.createContext(metric.dimensions.asMap());
            metricAggregator.add(metric.name, value, ctx);
        });
    }

    private void consume(ObjLongConsumer<StatusCodeMetric> consumer) {
        statistics.forEach((metric, adder) -> {
            long value = adder.sumThenReset();
            if (value > 0) consumer.accept(metric, value);
        });
    }

    @Override
    protected void doStart() throws Exception {
        shutdown.set(null);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        FutureCallback shutdownCb = shutdown.get();
        if ( ! shutdownCb.isDone()) {
            shutdownCb.failed(new TimeoutException());
        }
    }

    @Override
    public Future<Void> shutdown() {
        FutureCallback shutdownCb = new FutureCallback(false);
        shutdown.compareAndSet(null, shutdownCb);
        shutdownCb = shutdown.get();
        if (inFlight.get() == 0) {
            shutdownCb.succeeded();
        }
        return shutdownCb;
    }

    @Override
    public boolean isShutdown() {
        FutureCallback futureCallback = shutdown.get();
        return futureCallback != null && futureCallback.isDone();
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

        static Dimensions of(Request req, Collection<String> monitoringHandlerPaths,
                             Collection<String> searchHandlerPaths) {
            String requestType = requestType(req, monitoringHandlerPaths, searchHandlerPaths);
            return new Dimensions(protocol(req), scheme(req), method(req), requestType, statusCode(req));
        }

        Map<String, Object> asMap() {
            Map<String, Object> builder = new HashMap<>();
            builder.put(MetricDefinitions.PROTOCOL_DIMENSION, protocol);
            builder.put(MetricDefinitions.SCHEME_DIMENSION, scheme);
            builder.put(MetricDefinitions.METHOD_DIMENSION, method);
            builder.put(MetricDefinitions.REQUEST_TYPE_DIMENSION, requestType);
            builder.put(MetricDefinitions.STATUS_CODE_DIMENSION, (long) statusCode);
            return Map.copyOf(builder);
        }

        private static String protocol(Request req) {
            switch (req.getProtocol()) {
                case "HTTP/1":
                case "HTTP/1.0":
                case "HTTP/1.1":
                    return "http1";
                case "HTTP/2":
                case "HTTP/2.0":
                    return "http2";
                default:
                    return "other";
            }
        }

        private static String scheme(Request req) {
            switch (req.getScheme()) {
                case "http":
                case "https":
                    return req.getScheme();
                default:
                    return "other";
            }
        }

        private static String method(Request req) {
            switch (req.getMethod()) {
                case "GET":
                case "PATCH":
                case "POST":
                case "PUT":
                case "DELETE":
                case "OPTIONS":
                case "HEAD":
                    return req.getMethod();
                default:
                    return "other";
            }
        }

        private static int statusCode(Request req) { return req.getResponse().getStatus(); }

        private static String requestType(Request req, Collection<String> monitoringHandlerPaths,
                                          Collection<String> searchHandlerPaths) {
            HttpRequest.RequestType requestType = (HttpRequest.RequestType)req.getAttribute(requestTypeAttribute);
            if (requestType != null) return requestType.name().toLowerCase();
            // Deduce from path and method:
            String path = req.getRequestURI();
            for (String monitoringHandlerPath : monitoringHandlerPaths) {
                if (path.startsWith(monitoringHandlerPath)) return "monitoring";
            }
            for (String searchHandlerPath : searchHandlerPaths) {
                if (path.startsWith(searchHandlerPath)) return "read";
            }
            if ("GET".equals(req.getMethod())) return "read";
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

        static Collection<StatusCodeMetric> of(Request req, Collection<String> monitoringHandlerPaths,
                                   Collection<String> searchHandlerPaths) {
            Dimensions dimensions = Dimensions.of(req, monitoringHandlerPaths, searchHandlerPaths);
            return metricNames(req).stream()
                    .map(name -> new StatusCodeMetric(dimensions, name))
                    .collect(Collectors.toSet());
        }

        @SuppressWarnings("removal")
        private static Collection<String> metricNames(Request req) {
            int code = req.getResponse().getStatus();
            if (code < 200) return Set.of(MetricDefinitions.RESPONSES_1XX);
            else if (code < 300) return Set.of(MetricDefinitions.RESPONSES_2XX);
            else if (code < 400) return Set.of(MetricDefinitions.RESPONSES_3XX);
            else if (code < 500) return Set.of(MetricDefinitions.RESPONSES_4XX);
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
