// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
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
import java.util.function.Supplier;

/**
 * Default implementation of {@link IdentityDocumentClient}
 *
 * @author bjorncs
 */
public class DefaultIdentityDocumentClient implements IdentityDocumentClient {

    private static final String IDENTITY_DOCUMENT_API = "/athenz/v1/provider/identity-document/";
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
                    com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocument entity =
                            objectMapper.readValue(
                                    responseContent,
                                    com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocument.class);
                    return new SignedIdentityDocument(
                            toEntityDocument(entity.identityDocument),
                            entity.signature,
                            entity.signingKeyVersion,
                            VespaUniqueInstanceId.fromDottedString(entity.providerUniqueId),
                            entity.dnsSuffix,
                            (AthenzService) AthenzIdentities.from(entity.providerService),
                            entity.ztsEndpoint,
                            entity.documentVersion);
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

    private static IdentityDocument toEntityDocument(
            com.yahoo.vespa.athenz.identityprovider.api.bindings.IdentityDocument identityDocument) {
        return new IdentityDocument(
                identityDocument.providerUniqueId.toVespaUniqueInstanceId(),
                identityDocument.configServerHostname,
                identityDocument.instanceHostname,
                identityDocument.createdAt,
                identityDocument.ipAddresses);
    }

    private static CloseableHttpClient createHttpClient(SSLContext sslContext,
                                                        HostnameVerifier hostnameVerifier) {
        return HttpClientBuilder.create()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, /*requestSentRetryEnabled*/true))
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(hostnameVerifier)
                .setUserAgent("default-identity-document-client")
                .build();
    }

}
