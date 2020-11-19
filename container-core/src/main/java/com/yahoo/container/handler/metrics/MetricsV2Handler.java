// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.metrics;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;

/**
 * @author gjoranv
 */
public class MetricsV2Handler extends HttpHandlerBase {

    public static final String V2_PATH = "/metrics/v2";
    static final String VALUES_PATH = V2_PATH + "/values";

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;

    private final String metricsProxyUri;
    private final HttpClient httpClient = createHttpClient();

    @Inject
    public MetricsV2Handler(Executor executor,
                            MetricsProxyApiConfig config) {
        super(executor);
        metricsProxyUri = "http://localhost:" + config.metricsPort() + config.metricsApiPath();
    }

    @Override
    protected Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V2_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(valuesResponse(consumer));
        return Optional.empty();
    }

    private JsonResponse valuesResponse(String consumer) {
        try {
            String uri = metricsProxyUri + consumerQuery(consumer);
            String metricsJson = httpClient.execute(new HttpGet(uri), new BasicResponseHandler());
            return new JsonResponse(OK, metricsJson);
        } catch (IOException e) {
            log.warning("Unable to retrieve metrics from " + metricsProxyUri + ": " + Exceptions.toMessageString(e));
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.create()
                .setUserAgent("application-metrics-retriever")
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                                                 .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                                                 .build())
                .build();
    }

    static String consumerQuery(String consumer) {
        return (consumer == null || consumer.isEmpty()) ? "" : "?consumer=" + consumer;
    }

}
