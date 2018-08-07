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
import java.util.HashMap;
import java.util.Map;
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

    private static final String[] HTTP_RESPONSE_GROUPS = { Metrics.RESPONSES_1XX, Metrics.RESPONSES_2XX, Metrics.RESPONSES_3XX,
            Metrics.RESPONSES_4XX, Metrics.RESPONSES_5XX };

    private final AtomicLong inFlight = new AtomicLong();
    private final LongAdder statistics[][];

    public HttpResponseStatisticsCollector() {
        super();
        statistics = new LongAdder[HttpMethod.values().length][];
        for (int method = 0; method < statistics.length; method++) {
            statistics[method] = new LongAdder[HTTP_RESPONSE_GROUPS.length];
            for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                statistics[method][group] = new LongAdder();
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
            HttpMethod method = getMethod(request);
            statistics[method.ordinal()][group].increment();
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
        if (request.isHandled()) {
            int index = (request.getResponse().getStatus() / 100) - 1; // 1xx = 0, 2xx = 1 etc.
            if (index < 0 || index > statistics.length) {
                return -1;
            } else {
                return index;
            }
        } else {
            return 3; // 4xx
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

    public Map<String, Map<String, Long>> takeStatisticsByMethod() {
        Map<String, Map<String, Long>> ret = new HashMap<>();

        for (HttpMethod method : HttpMethod.values()) {
            int methodIndex = method.ordinal();
            Map<String, Long> methodStats = new HashMap<>();
            ret.put(method.toString(), methodStats);

            for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                long value = statistics[methodIndex][group].sumThenReset();
                methodStats.put(HTTP_RESPONSE_GROUPS[group], value);
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
}
