// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.client.ErrorHandler;
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
import org.apache.http.util.EntityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public abstract class ClientBase implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(ClientBase.class.getName());

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final CloseableHttpClient client;
    private final ClientExceptionFactory exceptionFactory;
    private final ErrorHandler errorHandler;

    protected ClientBase(String userAgent,
                         Supplier<SSLContext> sslContextSupplier,
                         ClientExceptionFactory exceptionFactory,
                         HostnameVerifier hostnameVerifier,
                         ErrorHandler errorHandler) {
        this.exceptionFactory = exceptionFactory;
        this.errorHandler = errorHandler;
        this.client = createHttpClient(userAgent, sslContextSupplier, hostnameVerifier);
    }

    protected  <T> T execute(HttpUriRequest request, ResponseHandler<T> responseHandler) {
        try {
            return client.execute(request, responseHandler);
        } catch (IOException e) {
            try {
                reportError(request, e);
            } catch (Exception _ignored) {}
            throw new UncheckedIOException(e);
        }
    }

    private void reportError(HttpUriRequest request, Exception e) {
        errorHandler.reportError(() -> request.getURI().getHost(), e);
    }

    protected StringEntity toJsonStringEntity(Object entity) {
        try {
            return new StringEntity(objectMapper.writeValueAsString(entity), ContentType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected <T> T readEntity(HttpResponse response, Class<T> entityType) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (isSuccess(statusCode)) {
            if (entityType.equals(Void.class)) {
                return null;
            } else {
                return objectMapper.readValue(response.getEntity().getContent(), entityType);
            }
        } else {
            byte[] entity = EntityUtils.toByteArray(response.getEntity());
            try {
                ErrorResponseEntity errorEntity = objectMapper.readValue(entity, ErrorResponseEntity.class);
                throw exceptionFactory.createException(errorEntity.code, errorEntity.description);
            } catch (JsonMappingException e) {
                logger.log(Level.INFO, String.format("Response returned status %d, but error response not parseable: %s", statusCode, new String(entity)), e);
                throw new RuntimeException("Non JSON response from Athenz.");
            }
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
