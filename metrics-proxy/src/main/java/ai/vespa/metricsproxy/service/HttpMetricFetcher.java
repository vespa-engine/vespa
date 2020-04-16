// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

/**
 * HTTP client to get metrics or health data from a service
 *
 * @author hmusum
 * @author bjorncs
 */
public abstract class HttpMetricFetcher {

    private final static Logger log = Logger.getLogger(HttpMetricFetcher.class.getPackage().getName());
    public final static String STATE_PATH = "/state/v1/";
    // The call to apache will do 3 retries. As long as we check the services in series, we can't have this too high.
    public static int CONNECTION_TIMEOUT = 5000;
    private final static int SOCKET_TIMEOUT = 60000;
    private final URI url;
    protected final VespaService service;
    private static final CloseableHttpClient httpClient = createHttpClient();


    /**
     * @param service the service to fetch metrics from
     * @param port    the port to use
     */
    HttpMetricFetcher(VespaService service, int port, String path) {
        this.service = service;

        String u = "http://localhost:" + port + path;
        this.url = URI.create(u);
        log.log(LogLevel.DEBUG, "Fetching metrics from " + u + " with timeout " + CONNECTION_TIMEOUT);
    }

    String getJson() throws IOException {
        log.log(LogLevel.DEBUG, "Connecting to url " + url + " for service '" + service + "'");
        return httpClient.execute(new HttpGet(url), new BasicResponseHandler());
    }

    public String toString() {
        return this.getClass().getSimpleName() + " using " + url;
    }

    String errMsgNoResponse(IOException e) {
        return "Unable to get response from service '" + service + "': " +
                Exceptions.toMessageString(e);
    }

    void handleException(Exception e, String data, int timesFetched) {
        logMessage("Unable to parse json '" + data + "' for service '" + service + "': " +
                           Exceptions.toMessageString(e), timesFetched);
    }

    private void logMessage(String message, int timesFetched) {
        if (service.isAlive() && timesFetched > 5) {
            log.log(LogLevel.INFO, message);
        } else {
            log.log(LogLevel.DEBUG, message);
        }
    }

    void logMessageNoResponse(String message, int timesFetched) {
        if (timesFetched > 5) {
            log.log(LogLevel.WARNING, message);
        } else {
            log.log(LogLevel.INFO, message);
        }
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.create()
                .setUserAgent("metrics-proxy-http-client")
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout(CONNECTION_TIMEOUT)
                                                 .setSocketTimeout(SOCKET_TIMEOUT)
                                                 .build())
                .build();
    }

}
