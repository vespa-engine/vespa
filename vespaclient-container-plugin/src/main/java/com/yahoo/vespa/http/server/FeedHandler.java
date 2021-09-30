// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.collections.Tuple2;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.http.client.core.Headers;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Accept feeds from outside of the Vespa cluster.
 *
 * @author Steinar Knutsen
 */
public class FeedHandler extends LoggingRequestHandler {

    protected final ReplyHandler feedReplyHandler;
    private static final List<Integer> serverSupportedVersions = Collections.unmodifiableList(Arrays.asList(3));
    private static final Pattern USER_AGENT_PATTERN = Pattern.compile("vespa-http-client \\((.+)\\)");
    private final FeedHandlerV3 feedHandlerV3;
    private final DocumentApiMetrics metricsHelper;

    @Inject
    public FeedHandler(ContainerThreadPool threadpool,
                       Metric metric,
                       DocumentmanagerConfig documentManagerConfig,
                       SessionCache sessionCache,
                       MetricReceiver metricReceiver) {
        super(threadpool.executor(), metric);
        metricsHelper = new DocumentApiMetrics(metricReceiver, "vespa.http.server");
        feedHandlerV3 = new FeedHandlerV3(threadpool.executor(), metric, documentManagerConfig, sessionCache, metricsHelper);
        feedReplyHandler = new FeedReplyReader(metric, metricsHelper);
    }

    private Tuple2<HttpResponse, Integer> checkProtocolVersion(HttpRequest request) {
        return doCheckProtocolVersion(request.getJDiscRequest().headers().get(Headers.VERSION));
    }

    static Tuple2<HttpResponse, Integer> doCheckProtocolVersion(List<String> clientSupportedVersions) {
        List<String> washedClientVersions = splitVersions(clientSupportedVersions);

        if (washedClientVersions == null || washedClientVersions.isEmpty()) {
            return new Tuple2<>(new ErrorHttpResponse(
                    Headers.HTTP_NOT_ACCEPTABLE,
                    "Request did not contain " + Headers.VERSION
                    + "header. Server supports protocol versions "
                    + serverSupportedVersions), -1);
        }

        //select the highest version supported by both parties
        //this could be extended when we support a gazillion versions - but right now: keep it simple.
        int version;
        if (washedClientVersions.contains("3")) {
            version = 3;
        } else {
            return new Tuple2<>(new ErrorHttpResponse(
                    Headers.HTTP_NOT_ACCEPTABLE,
                    "Could not parse " + Headers.VERSION
                    + "header of request (values: " + washedClientVersions +
                    "). Server supports protocol versions "
                    + serverSupportedVersions), -1);
        }
        return new Tuple2<>(null, version);
    }

    private static List<String> splitVersions(List<String> clientSupportedVersions) {
        List<String> splittedVersions = new ArrayList<>();
        for (String v : clientSupportedVersions) {
            if (v == null || v.trim().isEmpty()) {
                continue;
            }
            if (!v.contains(",")) {
                splittedVersions.add(v.trim());
                continue;
            }
            for (String part : v.split(",")) {
                part = part.trim();
                if (!part.isEmpty()) {
                    splittedVersions.add(part);
                }
            }
        }
        return splittedVersions;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        metricsHelper.reportHttpRequest(findClientVersion(request).orElse(null));
        Tuple2<HttpResponse, Integer> protocolVersion = checkProtocolVersion(request);

        if (protocolVersion.first != null) {
            return protocolVersion.first;
        }
        return feedHandlerV3.handle(request);
    }

    @Override
    protected void writeErrorResponseOnOverload(Request request, ResponseHandler responseHandler) {
        int responseCode = request.headers().getFirst(Headers.SILENTUPGRADE) != null ? 299 : 429;
        responseHandler.handleResponse(new Response(responseCode)).close(null);
    }

    private static Optional<String> findClientVersion(HttpRequest request) {
        String versionHeader = request.getHeader(Headers.CLIENT_VERSION);
        if (versionHeader != null) {
            return Optional.of(versionHeader);
        }
        return Optional.ofNullable(request.getHeader("User-Agent"))
                .map(USER_AGENT_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1));
    }

    // Protected for testing
    protected static InputStream unzipStreamIfNeeded(InputStream inputStream, HttpRequest httpRequest)
            throws IOException {
        String contentEncodingHeader = httpRequest.getHeader("content-encoding");
        if ("gzip".equals(contentEncodingHeader)) {
            return new GZIPInputStream(inputStream);
        } else {
            return inputStream;
        }
    }

    @Override
    protected void destroy() {
        feedHandlerV3.destroy();
        // We are forking this to avoid that accidental dereferrencing causes any random thread doing destruction.
        // This caused a deadlock when the single Messenger thread in MessageBus was the last one referring this
        // and started destructing something that required something only the messenger thread could provide.
        Thread destroyer = new Thread(() -> {
            internalDestroy();
        });
        destroyer.setDaemon(true);
        destroyer.start();
    }

    private void internalDestroy() {
        super.destroy();
    }
}
