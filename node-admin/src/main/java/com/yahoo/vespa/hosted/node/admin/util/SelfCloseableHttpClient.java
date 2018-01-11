package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.log.LogLevel;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author freva
 */
class SelfCloseableHttpClient implements AutoCloseable {

    private static final Logger log = Logger.getLogger(SelfCloseableHttpClient.class.getName());

    private final CloseableHttpClient httpClient;

    SelfCloseableHttpClient() {
        this(SSLConnectionSocketFactory.getSocketFactory());
    }

    SelfCloseableHttpClient(SSLConnectionSocketFactory sslConnectionSocketFactory) {
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslConnectionSocketFactory)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        cm.setMaxTotal(200); // Increase max total connections to 200, which should be enough

        // Have experienced hang in socket read, which may have been because of
        // system defaults, therefore set explicit timeouts. Set arbitrarily to
        // 15s > 10s used by Orchestrator lock timeout.
        int timeoutMs = 15_000;
        RequestConfig requestBuilder = RequestConfig.custom()
                .setConnectTimeout(timeoutMs) // establishment of connection
                .setConnectionRequestTimeout(timeoutMs) // connection from connection manager
                .setSocketTimeout(timeoutMs) // waiting for data
                .build();

        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder)
                .disableAutomaticRetries()
                .setUserAgent("node-admin")
                .setConnectionManager(cm).build();
    }

    public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
        return httpClient.execute(request);
    }

    @Override
    public void finalize() throws Throwable {
        close();
        super.finalize();
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Ignoring exception thrown when closing http client", e);
        }
    }
}
