// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.Response.Status.NOT_FOUND;

/**
 * A handler that proxies status.html health checks
 *
 * @author bjorncs
 */
class HealthCheckProxyHandler extends HandlerWrapper {

    private static final Logger log = Logger.getLogger(HealthCheckProxyHandler.class.getName());

    private static final String HEALTH_CHECK_PATH = "/status.html";

    private final Map<Integer, ProxyTarget> portToProxyTargetMapping;

    HealthCheckProxyHandler(List<JDiscServerConnector> connectors) {
        this.portToProxyTargetMapping = createPortToProxyTargetMapping(connectors);
    }

    private static Map<Integer, ProxyTarget> createPortToProxyTargetMapping(List<JDiscServerConnector> connectors) {
        var mapping = new HashMap<Integer, ProxyTarget>();
        for (JDiscServerConnector connector : connectors) {
            ConnectorConfig.HealthCheckProxy proxyConfig = connector.connectorConfig().healthCheckProxy();
            if (proxyConfig.enable()) {
                mapping.put(connector.listenPort(), createProxyTarget(proxyConfig.port(), connectors));
                log.info(String.format("Port %1$d is configured as a health check proxy for port %2$d. " +
                                               "HTTP requests to '%3$s' on %1$d are proxied as HTTPS to %2$d.",
                                       connector.listenPort(), proxyConfig.port(), HEALTH_CHECK_PATH));
            }
        }
        return mapping;
    }

    private static ProxyTarget createProxyTarget(int targetPort, List<JDiscServerConnector> connectors) {
        JDiscServerConnector targetConnector = connectors.stream()
                .filter(connector -> connector.listenPort() == targetPort)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Could not find any connector with listen port " + targetPort));
        SslContextFactory.Server sslContextFactory =
                Optional.ofNullable(targetConnector.getConnectionFactory(SslConnectionFactory.class))
                        .map(connFactory -> (SslContextFactory.Server) connFactory.getSslContextFactory())
                        .orElseThrow(() -> new IllegalArgumentException("Health check proxy can only target https port"));
        return new ProxyTarget(targetPort, sslContextFactory);
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        ProxyTarget proxyTarget = portToProxyTargetMapping.get(request.getLocalPort());
        if (proxyTarget != null) {
            if (servletRequest.getRequestURI().equals(HEALTH_CHECK_PATH)) {
                try (CloseableHttpResponse proxyResponse = proxyTarget.requestStatusHtml()) {
                    servletResponse.setStatus(proxyResponse.getStatusLine().getStatusCode());
                    servletResponse.setHeader("Vespa-Health-Check-Proxy-Target", Integer.toString(proxyTarget.port));
                    HttpEntity entity = proxyResponse.getEntity();
                    if (entity != null) {
                        Header contentType = entity.getContentType();
                        if (contentType != null) {
                            servletResponse.addHeader("Content-Type", contentType.getValue());
                        }
                        try (ServletOutputStream output = servletResponse.getOutputStream()) {
                            entity.getContent().transferTo(output);
                        }
                    }
                } catch (Exception e) {
                    String message = "Unable to proxy health check request: " + e.getMessage();
                    log.log(Level.WARNING, e, () -> message);
                    servletResponse.sendError(Response.Status.INTERNAL_SERVER_ERROR, message);
                }
            } else {
                servletResponse.sendError(NOT_FOUND);
            }
        } else {
            _handler.handle(target, request, servletRequest, servletResponse);
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (ProxyTarget target : portToProxyTargetMapping.values()) {
            target.close();
        }
        super.doStop();
    }

    private static class ProxyTarget implements AutoCloseable {
        final int port;
        final SslContextFactory.Server sslContextFactory;
        volatile CloseableHttpClient client;

        ProxyTarget(int port, SslContextFactory.Server sslContextFactory) {
            this.port = port;
            this.sslContextFactory = sslContextFactory;
        }

        CloseableHttpResponse requestStatusHtml() throws IOException {
            try {
                HttpGet request = new HttpGet("https://localhost:" + port + HEALTH_CHECK_PATH);
                return client().execute(request);
            } catch (SSLException e) {
                log.log(Level.SEVERE, "SSL connection failed. Closing existing client, a new client will be created on next request", e);
                close();
                throw e;
            }
        }

        // Client construction must be delayed to ensure that the SslContextFactory is started before calling getSslContext().
        private CloseableHttpClient client() {
            if (client == null) {
                synchronized (this) {
                    if (client == null) {
                        client = HttpClientBuilder.create()
                                .disableAutomaticRetries()
                                .setMaxConnPerRoute(4)
                                .setSSLContext(getSslContext(sslContextFactory))
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .setUserTokenHandler(context -> null) // https://stackoverflow.com/a/42112034/1615280
                                .setUserAgent("health-check-proxy-client")
                                .setDefaultRequestConfig(
                                        RequestConfig.custom()
                                                .setConnectTimeout((int) Duration.ofSeconds(4).toMillis())
                                                .setConnectionRequestTimeout((int) Duration.ofSeconds(4).toMillis())
                                                .setSocketTimeout((int) Duration.ofSeconds(8).toMillis())
                                                .build())
                                .build();
                    }
                }
            }
            return client;
        }

        private SSLContext getSslContext(SslContextFactory.Server sslContextFactory) {
            if (sslContextFactory.getNeedClientAuth()) {
                log.info(String.format("Port %d requires client certificate. HTTPS client will use the target server connector's ssl context.", port));
                // A client certificate is only required if the server connector's ssl context factory is configured with "need-auth".
                // We use the server's ssl context (truststore + keystore) if a client certificate is required.
                // This will only work if the server certificate's CA is in the truststore.
                return sslContextFactory.getSslContext();
            } else {
                log.info(String.format(
                        "Port %d does not require a client certificate. HTTPS client will use a custom ssl context accepting all certificates.", port));
                // No client certificate required. The client is configured with a trust manager that accepts all certificates.
                try {
                    return SSLContexts.custom().loadTrustMaterial(new TrustAllStrategy()).build();
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (client != null) {
                    client.close();
                    client = null;
                }
            }
        }
    }
}
