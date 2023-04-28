// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.RoleListEntity;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    public SignedIdentityDocument getNodeIdentityDocument(String host, int documentVersion) {
        return getIdentityDocument(host, "node", documentVersion).orElseThrow();
    }

    @Override
    public Optional<SignedIdentityDocument> getTenantIdentityDocument(String host, int documentVersion) {
        return getIdentityDocument(host, "tenant", documentVersion);
    }

    @Override
    public List<String> getNodeRoles(String hostname) {
        try (var client = createHttpClient(sslContextSupplier.get(), hostnameVerifier)) {
            var uri = configserverUri
                    .resolve(IDENTITY_DOCUMENT_API)
                    .resolve("roles/")
                    .resolve(hostname);

            var request = RequestBuilder.get()
                    .setUri(uri)
                    .addHeader("Connection", "close")
                    .addHeader("Accept", "application/json")
                    .build();
            try (var response = client.execute(request)) {
                String responseContent = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode <= 299) {
                    var rolesEntity = objectMapper.readValue(responseContent, RoleListEntity.class);
                    return rolesEntity.roles();
                } else {
                    throw new RuntimeException(
                            String.format(
                                    "Failed to retrieve roles for host %s: %d - %s",
                                    hostname,
                                    statusCode,
                                    responseContent));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<SignedIdentityDocument> getIdentityDocument(String host, String type, int documentVersion) {

        try (CloseableHttpClient client = createHttpClient(sslContextSupplier.get(), hostnameVerifier)) {
            URI uri = configserverUri
                    .resolve(IDENTITY_DOCUMENT_API)
                    .resolve(type + '/')
                    .resolve(host);
            HttpUriRequest request = RequestBuilder.get()
                    .setUri(uri)
                    .addHeader("Connection", "close")
                    .addHeader("Accept", "application/json")
                    .addParameter("documentVersion", Integer.toString(documentVersion))
                    .build();
            try (CloseableHttpResponse response = client.execute(request)) {
                String responseContent = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode <= 299) {
                    SignedIdentityDocumentEntity entity = objectMapper.readValue(responseContent, SignedIdentityDocumentEntity.class);
                    return Optional.of(EntityBindingsMapper.toSignedIdentityDocument(entity));
                } else if (statusCode == 404) {
                    return Optional.empty();
                } else {
                    throw new RuntimeException(
                            String.format(
                                    "Failed to retrieve identity document for host %s: %d - %s",
                                    host,
                                    statusCode,
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
