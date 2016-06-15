// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;

/**
 * @author bakksjo
 */
public class JerseyJaxRsClientFactory implements JaxRsClientFactory {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public JerseyJaxRsClientFactory() {
        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public JerseyJaxRsClientFactory(final int connectTimeoutMs, final int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Contains some workarounds for HTTP/JAX-RS/Jersey issues. See:
     *   https://jersey.java.net/apidocs/latest/jersey/org/glassfish/jersey/client/ClientProperties.html#SUPPRESS_HTTP_COMPLIANCE_VALIDATION
     *   https://jersey.java.net/apidocs/latest/jersey/org/glassfish/jersey/client/HttpUrlConnectorProvider.html#SET_METHOD_WORKAROUND
     */
    @Override
    public <T> T createClient(final Class<T> apiClass, final HostName hostName, final int port, final String pathPrefix) {
        final UriBuilder uriBuilder = UriBuilder.fromPath(pathPrefix).host(hostName.s()).port(port).scheme("http");
        final Client webClient = ClientBuilder.newClient()
                .property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutMs)
                .property(ClientProperties.READ_TIMEOUT, readTimeoutMs)
                .property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true) // Allow empty PUT. TODO: Fix API.
                .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true) // Allow e.g. PATCH method.
                .property(ClientProperties.FOLLOW_REDIRECTS, true);
        final WebTarget target = webClient.target(uriBuilder);
        // TODO: Check if this fills up non-heap memory with loaded classes.
        return WebResourceFactory.newResource(apiClass, target);
    }
}
