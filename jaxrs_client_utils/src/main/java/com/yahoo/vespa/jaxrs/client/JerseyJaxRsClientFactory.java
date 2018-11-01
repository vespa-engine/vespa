// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;
import java.util.Collections;

/**
 * Factory for creating Jersey clients from a JAX-RS resource interface.
 *
 * @author Oyvind Bakksjo
 */
public class JerseyJaxRsClientFactory implements JaxRsClientFactory {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    // Client is a heavy-weight object with a finalizer so we create only one and re-use it
    private final Client client;

    public JerseyJaxRsClientFactory() {
        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public JerseyJaxRsClientFactory(SSLContext sslContext, HostnameVerifier hostnameVerifier, String userAgent) {
        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, sslContext, hostnameVerifier, userAgent);
    }

    public JerseyJaxRsClientFactory(int connectTimeoutMs, int readTimeoutMs) {
        this(connectTimeoutMs, readTimeoutMs, null, null, null);
    }

    public JerseyJaxRsClientFactory(int connectTimeoutMs, int readTimeoutMs, SSLContext sslContext,
                                    HostnameVerifier hostnameVerifier, String userAgent) {
        /*
         * Configure client with some workarounds for HTTP/JAX-RS/Jersey issues. See:
         *   https://jersey.java.net/apidocs/latest/jersey/org/glassfish/jersey/client/ClientProperties.html#SUPPRESS_HTTP_COMPLIANCE_VALIDATION
         *   https://jersey.java.net/apidocs/latest/jersey/org/glassfish/jersey/client/HttpUrlConnectorProvider.html#SET_METHOD_WORKAROUND
         */
        ClientBuilder builder = ClientBuilder.newBuilder()
                                             .property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutMs)
                                             .property(ClientProperties.READ_TIMEOUT, readTimeoutMs)
                                             .property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true) // Allow empty PUT. TODO: Fix API.
                                             .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true) // Allow e.g. PATCH method.
                                             .property(ClientProperties.FOLLOW_REDIRECTS, true);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        if (hostnameVerifier != null) {
            builder.hostnameVerifier(hostnameVerifier);
        }
        if (userAgent != null) {
            builder.register((ClientRequestFilter) context -> context.getHeaders().put(HttpHeaders.USER_AGENT, Collections.singletonList(userAgent)));
        }
        this.client = builder.build();
    }

    @Override
    public <T> T createClient(Class<T> apiClass, HostName hostName, int port, String pathPrefix, String scheme) {
        UriBuilder uriBuilder = UriBuilder.fromPath(pathPrefix).host(hostName.s()).port(port).scheme(scheme);
        WebTarget target = client.target(uriBuilder);
        return WebResourceFactory.newResource(apiClass, target);
    }

}
