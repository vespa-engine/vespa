// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.HttpRequestRetryHandler;
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
 * @author mortent
 * @author bjorncs
 */
class ZtsClient {

    private static final String INSTANCE_API_PATH = "/zts/v1/instance";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler(3, /*requestSentRetryEnabled*/true);

    /**
     * Send instance register request to ZTS, get InstanceIdentity
     */
     InstanceIdentity sendInstanceRegisterRequest(InstanceRegisterInformation instanceRegisterInformation,
                                                         URI uri) {
        try(CloseableHttpClient client = HttpClientBuilder.create().setRetryHandler(retryHandler).build()) {
            HttpUriRequest postRequest = RequestBuilder.post()
                    .setUri(uri.resolve(INSTANCE_API_PATH))
                    .setEntity(toJsonStringEntity(instanceRegisterInformation))
                    .build();
            return getInstanceIdentity(client, postRequest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InstanceIdentity sendInstanceRefreshRequest(String providerService,
                                                String instanceDomain,
                                                String instanceServiceName,
                                                String instanceId,
                                                InstanceRefreshInformation instanceRefreshInformation,
                                                URI ztsEndpoint,
                                                SSLContext sslContext) {
        try (CloseableHttpClient client = createHttpClientWithTlsAuth(sslContext, retryHandler)) {
            URI uri = ztsEndpoint
                    .resolve(INSTANCE_API_PATH + '/')
                    .resolve(providerService + '/')
                    .resolve(instanceDomain + '/')
                    .resolve(instanceServiceName + '/')
                    .resolve(instanceId);
            HttpUriRequest postRequest = RequestBuilder.post()
                    .setUri(uri)
                    .setEntity(toJsonStringEntity(instanceRefreshInformation))
                    .build();
            return getInstanceIdentity(client, postRequest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InstanceIdentity getInstanceIdentity(CloseableHttpClient client, HttpUriRequest postRequest)
            throws IOException {
        try (CloseableHttpResponse response = client.execute(postRequest)) {
            if(HttpStatus.isSuccess(response.getStatusLine().getStatusCode())) {
                return objectMapper.readValue(response.getEntity().getContent(), InstanceIdentity.class);
            } else {
                String message = EntityUtils.toString(response.getEntity());
                throw new RuntimeException(String.format("Unable to get identity. http code/message: %d/%s",
                                                         response.getStatusLine().getStatusCode(), message));
            }
        }
    }

    private StringEntity toJsonStringEntity(Object value) throws JsonProcessingException {
        return new StringEntity(objectMapper.writeValueAsString(value), ContentType.APPLICATION_JSON);
    }

    private static CloseableHttpClient createHttpClientWithTlsAuth(SSLContext sslContext,
                                                                   HttpRequestRetryHandler retryHandler) {
            return HttpClientBuilder.create()
                    .setRetryHandler(retryHandler)
                    .setSslcontext(sslContext)
                    .build();
    }
}
