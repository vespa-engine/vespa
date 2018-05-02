// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceIdentityCredentials;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceRefreshInformation;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceRegisterInformation;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

/**
 * Default implementation of {@link ZtsClient}
 *
 * @author bjorncs
 * @author mortent
 */
public class DefaultZtsClient implements ZtsClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final URI ztsUrl;
    private final CloseableHttpClient client;

    public DefaultZtsClient(URI ztsUrl, SSLContext sslContext) {
        this.ztsUrl = ztsUrl;
        this.client = HttpClientBuilder.create()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, /*requestSentRetryEnabled*/true))
                .setSslcontext(sslContext)
                .build();
    }

    @Override
    public InstanceIdentity registerInstance(AthenzService providerIdentity,
                                             AthenzService instanceIdentity,
                                             String instanceId,
                                             String attestationData,
                                             boolean requestServiceToken,
                                             Pkcs10Csr csr) {
        InstanceRegisterInformation payload =
                new InstanceRegisterInformation(providerIdentity, instanceIdentity, attestationData, csr, requestServiceToken);
        HttpUriRequest request = RequestBuilder.post()
                .setUri(ztsUrl.resolve("instance/"))
                .setEntity(toJsonStringEntity(payload))
                .build();
        try (CloseableHttpResponse response = client.execute(request)) {
            return getInstanceIdentity(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InstanceIdentity refreshInstance(AthenzService providerIdentity,
                                            AthenzService instanceIdentity,
                                            String instanceId,
                                            boolean requestServiceToken,
                                            Pkcs10Csr csr) {
        InstanceRefreshInformation payload = new InstanceRefreshInformation(csr, requestServiceToken);
        URI uri = ztsUrl.resolve(
                String.format("instance/%s/%s/%s/%s",
                              providerIdentity.getFullName(),
                              instanceIdentity.getDomain().getName(),
                              instanceIdentity.getName(),
                              instanceId));
        HttpUriRequest request = RequestBuilder.post()
                .setUri(uri)
                .setEntity(toJsonStringEntity(payload))
                .build();
        try (CloseableHttpResponse response = client.execute(request)) {
            return getInstanceIdentity(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InstanceIdentity getInstanceIdentity(HttpResponse response) throws IOException {
        if (HttpStatus.isSuccess(response.getStatusLine().getStatusCode())) {
            InstanceIdentityCredentials entity =
                    objectMapper.readValue(response.getEntity().getContent(), InstanceIdentityCredentials.class);
            return entity.getServiceToken() != null
                    ? new InstanceIdentity(entity.getX509Certificate(), new NToken(entity.getServiceToken()))
                    : new InstanceIdentity(entity.getX509Certificate());
        } else {
            String message = EntityUtils.toString(response.getEntity());
            throw new ZtsClientException(
                    String.format("Unable to get identity. http code/message: %d/%s",
                                  response.getStatusLine().getStatusCode(), message));
        }
    }

    private static StringEntity toJsonStringEntity(Object entity) {
        try {
            return new StringEntity(objectMapper.writeValueAsString(entity), ContentType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
