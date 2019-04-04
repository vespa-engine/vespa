// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.server.jetty.JettyHttpServer.Metrics;
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
 * HttpResponseStatisticsCollector collects statistics about HTTP response types aggregated by category (1xx, 2xx, etc). It is similar to
 * {@link org.eclipse.jetty.server.handler.StatisticsHandler} with the distinction that this class collects response type statistics grouped
 * by HTTP method and only collects the numbers that are reported as metrics from Vespa.
 *
 * @author ollivir
 */
public class HttpResponseStatisticsCollector extends HandlerWrapper implements Graceful {
    private final AtomicReference<FutureCallback> shutdown = new AtomicReference<>();

    public static enum HttpMethod {
        GET, PATCH, POST, PUT, DELETE, OPTIONS, HEAD, OTHER
    }

    public enum HttpScheme {
        HTTP, HTTPS, OTHER
    }

    private static final String[] HTTP_RESPONSE_GROUPS = { Metrics.RESPONSES_1XX, Metrics.RESPONSES_2XX, Metrics.RESPONSES_3XX,
            Metrics.RESPONSES_4XX, Metrics.RESPONSES_5XX, Metrics.RESPONSES_401, Metrics.RESPONSES_403};

    private final AtomicLong inFlight = new AtomicLong();
    private final LongAdder statistics[][][];

    public HttpResponseStatisticsCollector() {
        super();
        statistics = new LongAdder[HttpScheme.values().length][HttpMethod.values().length][];
        for (int scheme = 0; scheme < HttpScheme.values().length; ++scheme) {
            for (int method = 0; method < HttpMethod.values().length; method++) {
                statistics[scheme][method] = new LongAdder[HTTP_RESPONSE_GROUPS.length];
                for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                    statistics[scheme][method][group] = new LongAdder();
                }
            }
        }
    }

    private final AsyncListener completionWatcher = new AsyncListener() {
        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            event.getAsyncContext().addListener(this);
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
        }

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

        /* The control flow logic here is mostly a copy from org.eclipse.jetty.server.handler.StatisticsHandler.handle(..) */
        try {
            Handler handler = getHandler();
            if (handler != null && shutdown.get() == null && isStarted()) {
                handler.handle(path, baseRequest, request, response);
            } else if (!baseRequest.isHandled()) {
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
            HttpScheme scheme = getScheme(request);
            HttpMethod method = getMethod(request);
            statistics[scheme.ordinal()][method.ordinal()][group].increment();
            if (group == 5 || group == 6) { // if 401/403, also increment 4xx
                statistics[scheme.ordinal()][method.ordinal()][3].increment();
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
        if (index < 0 || index >= statistics[0].length) {
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

    public List<StatisticsEntry> takeStatistics() {
        var ret = new ArrayList<StatisticsEntry>();
        for (HttpScheme scheme : HttpScheme.values()) {
            int schemeIndex = scheme.ordinal();
            for (HttpMethod method : HttpMethod.values()) {
                int methodIndex = method.ordinal();
                for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                    long value = statistics[schemeIndex][methodIndex][group].sumThenReset();
                    if (value > 0) {
                        ret.add(new StatisticsEntry(scheme.name().toLowerCase(), method.name(), HTTP_RESPONSE_GROUPS[group], value));
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
        if (shutdown != null && !shutdownCb.isDone()) {
            shutdownCb.failed(new TimeoutException());
        }
    }

    @Override
    public Future<Void> shutdown() {
        /* This shutdown callback logic is a copy from org.eclipse.jetty.server.handler.StatisticsHandler */

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
        public final String scheme;
        public final String method;
        public final String name;
        public final long value;


        public StatisticsEntry(String scheme, String method, String name, long value) {
            this.scheme = scheme;
            this.method = method;
            this.name = name;
            this.value = value;
        }
    }
}
