package com.yahoo.container.handler.metrics;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.StringResponse;
import com.yahoo.vespa.jdk8compat.List;
import com.yahoo.yolean.Exceptions;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;

public class PrometheusV1Handler extends HttpHandlerBase {

    public static final String V1_PATH = "/prometheus/v1";
    static final String VALUES_PATH = V1_PATH + "/values";

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;

    private final String metricsProxyUri;
    private final HttpClient httpClient = createHttpClient();


    @Inject
    protected PrometheusV1Handler(Executor executor, MetricsProxyApiConfig config) {
        super(executor);
        metricsProxyUri = "http://localhost:+" + config.metricsPort() + config.metricsApiPath();
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.create()
                .setUserAgent("application-prometheus-retriever")
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                        .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                        .build())
                .build();
    }

    static String consumerQuery(String consumer) {
        return (consumer == null || consumer.isEmpty()) ? "" : "?consumer=" + consumer;
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
            String metricsString = httpClient.execute(new HttpGet(uri), new BasicResponseHandler());
            return new StringResponse(metricsString);
        } catch (IOException e) {
            log.warning(String.format("Unable to retrieve prometheus metrics from %s: %s", metricsProxyUri, Exceptions.toMessageString(e)));
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
