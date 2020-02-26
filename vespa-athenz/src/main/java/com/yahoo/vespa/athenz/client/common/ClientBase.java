// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.client.common.bindings.ErrorResponseEntity;
import com.yahoo.vespa.athenz.identity.ServiceIdentitySslSocketFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * @author bjorncs
 */
public abstract class ClientBase implements AutoCloseable {

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final CloseableHttpClient client;
    private final ClientExceptionFactory exceptionFactory;

    protected ClientBase(String userAgent,
                         Supplier<SSLContext> sslContextSupplier,
                         ClientExceptionFactory exceptionFactory,
                         HostnameVerifier hostnameVerifier) {
        this.exceptionFactory = exceptionFactory;
        this.client = createHttpClient(userAgent, sslContextSupplier, hostnameVerifier);
    }

    protected  <T> T execute(HttpUriRequest request, ResponseHandler<T> responseHandler) {
        try {
            return client.execute(request, responseHandler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected StringEntity toJsonStringEntity(Object entity) {
        try {
            return new StringEntity(objectMapper.writeValueAsString(entity), ContentType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected <T> T readEntity(HttpResponse response, Class<T> entityType) throws IOException {
        if (isSuccess(response.getStatusLine().getStatusCode())) {
            if (entityType.equals(Void.class)) {
                return null;
            } else {
                return objectMapper.readValue(response.getEntity().getContent(), entityType);
            }
        } else {
            ErrorResponseEntity errorEntity = objectMapper.readValue(response.getEntity().getContent(), ErrorResponseEntity.class);
            throw exceptionFactory.createException(errorEntity.code, errorEntity.description);
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode>=200 && statusCode<300;
    }

    private static CloseableHttpClient createHttpClient(String userAgent, Supplier<SSLContext> sslContextSupplier, HostnameVerifier hostnameVerifier) {
        return HttpClientBuilder.create()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, /*requestSentRetryEnabled*/true))
                .setUserAgent(userAgent)
                .setSSLSocketFactory(new SSLConnectionSocketFactory(new ServiceIdentitySslSocketFactory(sslContextSupplier), hostnameVerifier))
                .setMaxConnPerRoute(8)
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout((int) Duration.ofSeconds(10).toMillis())
                                                 .setConnectionRequestTimeout((int)Duration.ofSeconds(10).toMillis())
                                                 .setSocketTimeout((int)Duration.ofSeconds(20).toMillis())
                                                 .build())
                .build();
    }

    @Override
    public void close()  {
        try {
            this.client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected interface ClientExceptionFactory {
        RuntimeException createException(int errorCode, String description);
    }
}
