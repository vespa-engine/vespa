// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import ai.vespa.util.http.VespaClientBuilderFactory;
import com.yahoo.vespa.applicationmodel.HostName;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;
import java.util.List;

/**
 * Factory for creating Jersey based Vespa clients from a JAX-RS resource interface.
 *
 * @deprecated Use Apache httpclient based client factory instead (VespaHttpClientBuilder).
 * @author bjorncs
 */
@Deprecated(forRemoval = true)
public class VespaJerseyJaxRsClientFactory implements JaxRsClientFactory, AutoCloseable {

    @SuppressWarnings("removal")
    private final VespaClientBuilderFactory clientBuilder = new VespaClientBuilderFactory();
    // Client is a heavy-weight object with a finalizer so we create only one and re-use it
    private final Client client;

    public VespaJerseyJaxRsClientFactory(String userAgent) {
        this.client = clientBuilder.newBuilder()
                .property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true) // Allow empty PUT
                .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true) // Allow e.g. PATCH method.
                .property(ClientProperties.FOLLOW_REDIRECTS, true)
                .register((ClientRequestFilter) context -> context.getHeaders().put(HttpHeaders.USER_AGENT, List.of(userAgent)))
                .build();
    }

    @Override
    public <T> T createClient(Params<T> params) {
        WebTarget target = client.target(params.uri());
        target.property(ClientProperties.CONNECT_TIMEOUT, (int) params.connectTimeout().toMillis());
        target.property(ClientProperties.READ_TIMEOUT, (int) params.readTimeout().toMillis());
        return WebResourceFactory.newResource(params.apiClass(), target);
    }

    @Override
    public <T> T createClient(Class<T> apiClass, HostName hostName, int port, String pathPrefix, String scheme) {
        UriBuilder uriBuilder = UriBuilder.fromPath(pathPrefix).host(hostName.s()).port(port).scheme(scheme);
        return createClient(new Params<>(apiClass, uriBuilder.build()));
    }

    @Override
    public void close() {
        clientBuilder.close();
    }
}
