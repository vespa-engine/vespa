// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Default implementation of {@link IdentityDocumentClient}
 *
 * @author bjorncs
 */
public class DefaultIdentityDocumentClient implements IdentityDocumentClient {

    private static final String IDENTITY_DOCUMENT_API = "/athenz/v1/provider/identity-document/";
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Supplier<SSLContext> sslContextSupplier;
    private final HostnameVerifier hostnameVerifier;
    private final URI configserverUri;

    public DefaultIdentityDocumentClient(URI configserverUri,
                                         SSLContext sslContext,
                                         HostnameVerifier hostnameVerifier) {
        this.configserverUri = configserverUri;
        this.sslContextSupplier = () -> sslContext;
        this.hostnameVerifier = hostnameVerifier;
    }

    public DefaultIdentityDocumentClient(URI configserverUri,
                                         ServiceIdentityProvider identityProvider,
                                         HostnameVerifier hostnameVerifier) {
        this.configserverUri = configserverUri;
        this.sslContextSupplier = identityProvider::getIdentitySslContext;
        this.hostnameVerifier = hostnameVerifier;
    }

    @Override
    public SignedIdentityDocument getNodeIdentityDocument(String host) {
        return getIdentityDocument(host, "node");
    }

    @Override
    public SignedIdentityDocument getTenantIdentityDocument(String host) {
        return getIdentityDocument(host, "tenant");
    }

    private SignedIdentityDocument getIdentityDocument(String host, String type) {

        try (CloseableHttpClient client = createHttpClient(sslContextSupplier.get(), hostnameVerifier)) {
            URI uri = configserverUri
                    .resolve(IDENTITY_DOCUMENT_API)
                    .resolve(type + '/')
                    .resolve(host);
            HttpUriRequest request = RequestBuilder.get()
                    .setUri(uri)
                    .addHeader("Connection", "close")
                    .addHeader("Accept", "application/json")
                    .build();
            try (CloseableHttpResponse response = client.execute(request)) {
                String responseContent = EntityUtils.toString(response.getEntity());
                if (HttpStatus.isSuccess(response.getStatusLine().getStatusCode())) {
                    SignedIdentityDocumentEntity entity = objectMapper.readValue(responseContent, SignedIdentityDocumentEntity.class);
                    return EntityBindingsMapper.toSignedIdentityDocument(entity);
                } else {
                    throw new RuntimeException(
                            String.format(
                                    "Failed to retrieve identity document for host %s: %d - %s",
                                    host,
                                    response.getStatusLine().getStatusCode(),
                                    responseContent));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CloseableHttpClient createHttpClient(SSLContext sslContext,
                                                        HostnameVerifier hostnameVerifier) {
        return HttpClientBuilder.create()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, /*requestSentRetryEnabled*/true))
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(hostnameVerifier)
                .setUserAgent("default-identity-document-client")
                .setDefaultRequestConfig(RequestConfig.custom()
                                                    .setConnectTimeout((int)Duration.ofSeconds(10).toMillis())
                                                    .setConnectionRequestTimeout((int)Duration.ofSeconds(10).toMillis())
                                                    .setSocketTimeout((int)Duration.ofSeconds(20).toMillis())
                                                    .build())
                .build();
    }

}
