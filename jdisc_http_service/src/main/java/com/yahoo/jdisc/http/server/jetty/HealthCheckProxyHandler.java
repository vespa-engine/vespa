// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
            }
        }
        return mapping;
    }

    private static ProxyTarget createProxyTarget(int targetPort, List<JDiscServerConnector> connectors) {
        JDiscServerConnector targetConnector = connectors.stream()
                .filter(connector -> connector.listenPort() == targetPort)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Could not find any connector with listen port " + targetPort));
        SslContextFactory sslContextFactory =
                Optional.ofNullable(targetConnector.getConnectionFactory(SslConnectionFactory.class))
                        .map(SslConnectionFactory::getSslContextFactory)
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
        final SslContextFactory sslContextFactory;
        volatile CloseableHttpClient client;

        ProxyTarget(int port, SslContextFactory sslContextFactory) {
            this.port = port;
            this.sslContextFactory = sslContextFactory;
        }

        CloseableHttpResponse requestStatusHtml() throws IOException {
            HttpGet request = new HttpGet("https://localhost:" + port + HEALTH_CHECK_PATH);
            request.setHeader("Connection", "Close");
            return client().execute(request);
        }

        // Client construction must be delayed to ensure that the SslContextFactory is started before calling getSslContext().
        private CloseableHttpClient client() {
            if (client == null) {
                synchronized (this) {
                    if (client == null) {
                        client = HttpClientBuilder.create()
                                .disableAutomaticRetries()
                                .setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE)
                                .setSSLContext(sslContextFactory.getSslContext())
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .setUserTokenHandler(context -> null) // https://stackoverflow.com/a/42112034/1615280
                                .setUserAgent("health-check-proxy-client")
                                .build();
                    }
                }
            }
            return client;
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (client != null) {
                    client.close();
                }
            }
        }
    }
}
