// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.metrics;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.StringResponse;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static com.yahoo.container.handler.metrics.MetricsV2Handler.consumerQuery;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Oracien
 */
public class PrometheusV1Handler extends HttpHandlerBase {

    public static final String V1_PATH = "/prometheus/v1";
    static final String VALUES_PATH = V1_PATH + "/values";

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;

    private final String metricsProxyUri;
    private final CloseableHttpClient httpClient = createHttpClient();

    @Inject
    public PrometheusV1Handler(Executor executor,
                               MetricsProxyApiConfig config) {
        super(executor);
        metricsProxyUri = "http://localhost:" + config.metricsPort() + config.prometheusApiPath();
    }

    @Override
    protected Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V1_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(valuesResponse(consumer));
        return Optional.empty();
    }

    private HttpResponse valuesResponse(String consumer) {
        try {
            String uri = metricsProxyUri + consumerQuery(consumer);
            String prometheusText = httpClient.execute(new HttpGet(uri), new BasicHttpClientResponseHandler());
            return new StringResponse(prometheusText);
        } catch (IOException e) {
            log.warning("Unable to retrieve metrics from " + metricsProxyUri + ": " + Exceptions.toMessageString(e));
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.custom().connectTimeout(HTTP_CONNECT_TIMEOUT, MILLISECONDS).apacheBuilder()
                .setUserAgent("application-prometheus-receiver")
                .setDefaultRequestConfig(RequestConfig.custom()
                                            .setResponseTimeout(HTTP_SOCKET_TIMEOUT, MILLISECONDS)
                                            .build())
                .build();
    }

    @Override
    public void destroy(){
        super.destroy();
        try {
            httpClient.close();
        }
        catch (IOException e) {
            log.log(Level.WARNING, "Failed closing http client", e);
        }
    }

}
