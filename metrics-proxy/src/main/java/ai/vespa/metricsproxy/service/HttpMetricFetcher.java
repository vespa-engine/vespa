// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client to get metrics or health data from a service
 *
 * @author hmusum
 * @author bjorncs
 */
public abstract class HttpMetricFetcher {

    private final static Logger log = Logger.getLogger(HttpMetricFetcher.class.getName());
    public final static String STATE_PATH = "/state/v1/";
    // The call to apache will do 3 retries. As long as we check the services in series, we can't have this too high.
    public static volatile int CONNECTION_TIMEOUT = 5000;
    private final static int SOCKET_TIMEOUT = 60000;
    final static int BUFFER_SIZE = 0x40000; // 256k
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
        log.log(Level.FINE, () -> "Fetching metrics from " + u + " with timeout " + CONNECTION_TIMEOUT);
    }

    @SuppressWarnings("deprecation")
    CloseableHttpResponse getResponse() throws IOException {
        log.log(Level.FINE, () -> "Connecting to url " + url + " for service '" + service + "'");
        return httpClient.execute(new HttpGet(url));
    }

    public String toString() {
        return this.getClass().getSimpleName() + " using " + url;
    }

    String errMsgNoResponse(Exception e) {
        return "Unable to get response from service '" + service + "': " +
                Exceptions.toMessageString(e);
    }

    void handleException(Exception e, Object data, int timesFetched) {
        logMessage("Unable to parse json '" + data + "' for service '" + service + "': ", e, timesFetched);
    }

    private void logMessage(String message, Exception e, int timesFetched) {
        if (service.isAlive() && timesFetched > 5) {
            log.log(Level.INFO, message, e);
        } else {
            log.log(Level.FINE, message, e);
        }
    }

    void logMessageNoResponse(String message, int timesFetched) {
        if (timesFetched > 5) {
            log.log(Level.WARNING, message);
        } else {
            log.log(Level.INFO, message);
        }
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.custom()
                .connectTimeout(Timeout.ofMilliseconds(CONNECTION_TIMEOUT))
                .socketTimeout(Timeout.ofMilliseconds(CONNECTION_TIMEOUT))
                .apacheBuilder()
                .setUserAgent("metrics-proxy-http-client")
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(SOCKET_TIMEOUT))
                        .setResponseTimeout(Timeout.ofMilliseconds(SOCKET_TIMEOUT))
                        .build())
                .build();
    }

}
