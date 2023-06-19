// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.collections.ListMap;
import com.yahoo.container.handler.Coverage;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.jdisc.ExtendedResponse;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.processing.execution.Execution.Trace.LogValue;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.yolean.trace.TraceNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Wrap the result of a query as an HTTP response.
 *
 * @author Steinar Knutsen
 */
public class HttpSearchResponse extends ExtendedResponse {

    private final Result result;
    private final Query query;
    private final Renderer<Result> rendererCopy;
    private final Metric metric;
    private final Timing timing;
    private final HitCounts hitCounts;
    private final TraceNode trace;

    public HttpSearchResponse(int status, Result result, Query query, Renderer<Result> renderer) {
        this(status, result, query, renderer, null, null);
    }

    HttpSearchResponse(int status, Result result, Query query, Renderer<Result> renderer, TraceNode trace, Metric metric) {
        super(status);
        this.query = query;
        this.result = result;
        this.rendererCopy = renderer;
        this.metric = metric;
        this.timing = SearchResponse.createTiming(query, result);
        this.hitCounts = SearchResponse.createHitCounts(query, result);
        this.trace = trace;
        populateHeaders(headers(), result.getHeaders(false));
    }

    /**
     * Copy custom HTTP headers from the search result over to the HTTP response.
     *
     * @param outputHeaders the headers which will be sent to a client
     * @param searchHeaders the headers from the search result, or null
     */
    private static void populateHeaders(HeaderFields outputHeaders,
            ListMap<String, String> searchHeaders) {
        if (searchHeaders == null) {
            return;
        }
        for (Map.Entry<String, List<String>> header : searchHeaders.entrySet()) {
            for (String value : header.getValue()) {
                outputHeaders.add(header.getKey(), value);
            }
        }
    }

    public CompletableFuture<Boolean> asyncRender(OutputStream stream) {
        return asyncRender(result, query, rendererCopy, stream);
    }

    public static CompletableFuture<Boolean> asyncRender(Result result,
                                                         Query query,
                                                         Renderer<Result> renderer,
                                                         OutputStream stream) {
        SearchResponse.trimHits(result);
        SearchResponse.removeEmptySummaryFeatureFields(result);
        return renderer.renderResponse(stream, result, query.getModel().getExecution(), query);
    }


    @Override
    public void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler) throws IOException {
        if (rendererCopy instanceof AsynchronousSectionedRenderer<Result> renderer) {
            renderer.setNetworkWiring(networkChannel, handler);
        }
        try {
            try {
                long nanoStart = System.nanoTime();
                CompletableFuture<Boolean> promise = asyncRender(output);
                if (metric != null) {
                    promise.whenComplete((__, ___) -> new RendererLatencyReporter(nanoStart).run());
                }
            } finally {
                if (!(rendererCopy instanceof AsynchronousSectionedRenderer)) {
                    output.flush();
                }
            }
        } finally {
            if (networkChannel != null && !(rendererCopy instanceof AsynchronousSectionedRenderer)) {
                networkChannel.close(handler);
            }
        }
    }

    @Override
    public void populateAccessLogEntry(final AccessLogEntry accessLogEntry) {
        super.populateAccessLogEntry(accessLogEntry);
        if (trace != null) {
            accessLogEntry.setTrace(trace);
        }
        populateAccessLogEntry(accessLogEntry, getHitCounts());
    }

    /* package-private */
    static void populateAccessLogEntry(AccessLogEntry jdiscRequestAccessLogEntry, HitCounts hitCounts) {
        // This entry will be logged at Jetty level. Here we just populate with tidbits from this context.

        jdiscRequestAccessLogEntry.setHitCounts(hitCounts);
    }

    @Override
    public String getParsedQuery() {
        return query.toString();
    }

    @Override
    public Timing getTiming() {
        return timing;
    }

    @Override
    public Coverage getCoverage() {
        return result.getCoverage(false);
    }

    @Override
    public HitCounts getHitCounts() {
        return hitCounts;
    }

    /**
     * Returns MIME type of this response
     */
    @Override
    public String getContentType() {
        return rendererCopy.getMimeType();
    }

    /**
     * Returns expected character encoding of this response
     */
    @Override
    public String getCharacterEncoding() {
        String encoding = result.getQuery().getModel().getEncoding();
        return (encoding != null) ? encoding : rendererCopy.getEncoding();
    }

    /** Returns the query wrapped by this */
    public Query getQuery() { return query; }

    /** Returns the result wrapped by this */
    public Result getResult() { return result; }

    @Override
    public Iterable<LogValue> getLogValues() {
        QueryContext context = query.getContext(false);
        return context == null ? Collections::emptyIterator : context::logValueIterator;
    }

    private class RendererLatencyReporter implements Runnable {

        final long nanoStart;

        RendererLatencyReporter(long nanoStart) { this.nanoStart = nanoStart; }

        @Override
        public void run() {
            long latencyNanos = System.nanoTime() - nanoStart;
            Metric.Context ctx = metric.createContext(Map.of(
                    SearchHandler.RENDERER_DIMENSION, rendererCopy.getClassName(),
                    SearchHandler.MIME_DIMENSION, rendererCopy.getMimeType()));
            metric.set(SearchHandler.RENDER_LATENCY_METRIC, latencyNanos, ctx);
        }
    }

}
