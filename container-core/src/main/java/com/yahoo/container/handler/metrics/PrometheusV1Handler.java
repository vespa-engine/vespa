package com.yahoo.container.handler.metrics;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.StringResponse;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.container.handler.metrics.MetricsV2Handler.consumerQuery;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * @author Oracien
 */
public class PrometheusV1Handler extends HttpHandlerBase{

    public static final String V1_PATH = "/prometheus/v1";
    static final String VALUES_PATH = V1_PATH + "/values";

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;

    private final String metricsProxyUri;
    private final HttpClient httpClient = createHttpClient();

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
            String prometheusText = httpClient.execute(new HttpGet(uri), new BasicResponseHandler());
            return new StringResponse(prometheusText);
        } catch (IOException e) {
            log.warning("Unable to retrieve metrics from " + metricsProxyUri + ": " + Exceptions.toMessageString(e));
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.create()
                .setUserAgent("application-prometheus-receiver")
                .setDefaultRequestConfig(RequestConfig.custom()
                                            .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                                            .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                                            .build())
                .build();
    }
}
