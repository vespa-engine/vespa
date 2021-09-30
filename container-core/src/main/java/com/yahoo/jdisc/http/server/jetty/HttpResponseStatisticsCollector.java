// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.HttpRequest;
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
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * HttpResponseStatisticsCollector collects statistics about HTTP response types aggregated by category
 * (1xx, 2xx, etc). It is similar to {@link org.eclipse.jetty.server.handler.StatisticsHandler}
 * with the distinction that this class collects response type statistics grouped
 * by HTTP method and only collects the numbers that are reported as metrics from Vespa.
 *
 * @author ollivir
 */
public class HttpResponseStatisticsCollector extends HandlerWrapper implements Graceful {

    static final String requestTypeAttribute = "requestType";

    private final AtomicReference<FutureCallback> shutdown = new AtomicReference<>();
    private final List<String> monitoringHandlerPaths;
    private final List<String> searchHandlerPaths;

    public enum HttpMethod {
        GET, PATCH, POST, PUT, DELETE, OPTIONS, HEAD, OTHER
    }

    public enum HttpScheme {
        HTTP, HTTPS, OTHER
    }

    public enum HttpProtocol {
        HTTP1, HTTP2, OTHER
    }

    private static final String[] HTTP_RESPONSE_GROUPS = {
            MetricDefinitions.RESPONSES_1XX,
            MetricDefinitions.RESPONSES_2XX,
            MetricDefinitions.RESPONSES_3XX,
            MetricDefinitions.RESPONSES_4XX,
            MetricDefinitions.RESPONSES_5XX,
            MetricDefinitions.RESPONSES_401,
            MetricDefinitions.RESPONSES_403
    };

    private final AtomicLong inFlight = new AtomicLong();
    private final LongAdder[][][][][] statistics; // TODO Rewrite me to a smarter data structure

    public HttpResponseStatisticsCollector(List<String> monitoringHandlerPaths, List<String> searchHandlerPaths) {
        this.monitoringHandlerPaths = monitoringHandlerPaths;
        this.searchHandlerPaths = searchHandlerPaths;
        statistics = new LongAdder[HttpProtocol.values().length][HttpScheme.values().length][HttpMethod.values().length][][];
        for (int protocol = 0; protocol < HttpProtocol.values().length; protocol++) {
            for (int scheme = 0; scheme < HttpScheme.values().length; ++scheme) {
                for (int method = 0; method < HttpMethod.values().length; method++) {
                    statistics[protocol][scheme][method] = new LongAdder[HTTP_RESPONSE_GROUPS.length][];
                    for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                        statistics[protocol][scheme][method][group] = new LongAdder[HttpRequest.RequestType.values().length];
                        for (int requestType = 0; requestType < HttpRequest.RequestType.values().length; requestType++) {
                            statistics[protocol][scheme][method][group][requestType] = new LongAdder();
                        }
                    }
                }
            }
        }
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

    private void observeEndOfRequest(Request request, HttpServletResponse flushableResponse) throws IOException {
        int group = groupIndex(request);
        if (group >= 0) {
            HttpProtocol protocol = getProtocol(request);
            HttpScheme scheme = getScheme(request);
            HttpMethod method = getMethod(request);
            HttpRequest.RequestType requestType = getRequestType(request);

            statistics[protocol.ordinal()][scheme.ordinal()][method.ordinal()][group][requestType.ordinal()].increment();
            if (group == 5 || group == 6) { // if 401/403, also increment 4xx
                statistics[protocol.ordinal()][scheme.ordinal()][method.ordinal()][3][requestType.ordinal()].increment();
            }
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

    private int groupIndex(Request request) {
        int index = request.getResponse().getStatus();
        if (index == 401) {
            return 5;
        }
        if (index == 403) {
            return 6;
        }

        index = index / 100 - 1; // 1xx = 0, 2xx = 1 etc.
        if (index < 0 || index >= statistics[0][0].length) {
            return -1;
        } else {
            return index;
        }
    }

    private HttpScheme getScheme(Request request) {
        switch (request.getScheme()) {
            case "http":
                return HttpScheme.HTTP;
            case "https":
                return HttpScheme.HTTPS;
            default:
                return HttpScheme.OTHER;
        }
    }

    private HttpMethod getMethod(Request request) {
        switch (request.getMethod()) {
        case "GET":
            return HttpMethod.GET;
        case "PATCH":
            return HttpMethod.PATCH;
        case "POST":
            return HttpMethod.POST;
        case "PUT":
            return HttpMethod.PUT;
        case "DELETE":
            return HttpMethod.DELETE;
        case "OPTIONS":
            return HttpMethod.OPTIONS;
        case "HEAD":
            return HttpMethod.HEAD;
        default:
            return HttpMethod.OTHER;
        }
    }

    private HttpProtocol getProtocol(Request request) {
        switch (request.getProtocol()) {
            case "HTTP/1":
            case "HTTP/1.0":
            case "HTTP/1.1":
                return HttpProtocol.HTTP1;
            case "HTTP/2":
            case "HTTP/2.0":
                return HttpProtocol.HTTP2;
            default:
                return HttpProtocol.OTHER;
        }
    }

    private HttpRequest.RequestType getRequestType(Request request) {
        HttpRequest.RequestType requestType = (HttpRequest.RequestType)request.getAttribute(requestTypeAttribute);
        if (requestType != null) return requestType;

        // Deduce from path and method:
        String path = request.getRequestURI();
        for (String monitoringHandlerPath : monitoringHandlerPaths) {
            if (path.startsWith(monitoringHandlerPath)) return HttpRequest.RequestType.MONITORING;
        }
        for (String searchHandlerPath : searchHandlerPaths) {
            if (path.startsWith(searchHandlerPath)) return HttpRequest.RequestType.READ;
        }
        if ("GET".equals(request.getMethod())) {
            return HttpRequest.RequestType.READ;
        } else {
            return HttpRequest.RequestType.WRITE;
        }
    }

    public List<StatisticsEntry> takeStatistics() {
        var ret = new ArrayList<StatisticsEntry>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            int protocolIndex = protocol.ordinal();
            for (HttpScheme scheme : HttpScheme.values()) {
                int schemeIndex = scheme.ordinal();
                for (HttpMethod method : HttpMethod.values()) {
                    int methodIndex = method.ordinal();
                    for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                        for (HttpRequest.RequestType type : HttpRequest.RequestType.values()) {
                            long value = statistics[protocolIndex][schemeIndex][methodIndex][group][type.ordinal()].sumThenReset();
                            if (value > 0) {
                                ret.add(new StatisticsEntry(protocol.name().toLowerCase(), scheme.name().toLowerCase(), method.name(), HTTP_RESPONSE_GROUPS[group], type.name().toLowerCase(), value));
                            }
                        }
                    }
                }
            }
        }
        return ret;
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

    public static class StatisticsEntry {

        public final String protocol;
        public final String scheme;
        public final String method;
        public final String name;
        public final String requestType;
        public final long value;

        public StatisticsEntry(String protocol, String scheme, String method, String name, String requestType, long value) {
            this.protocol = protocol;
            this.scheme = scheme;
            this.method = method;
            this.name = name;
            this.requestType = requestType;
            this.value = value;
        }

        @Override
        public String toString() {
            return "protocol: " + protocol +
                   ", scheme: " + scheme +
                   ", method: " + method +
                   ", name: " + name +
                   ", requestType: " + requestType +
                   ", value: " + value;
        }

    }

}
