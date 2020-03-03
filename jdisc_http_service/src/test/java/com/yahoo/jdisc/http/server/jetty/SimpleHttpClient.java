// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * A simple http client for testing
 *
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public class SimpleHttpClient implements AutoCloseable {

    private final CloseableHttpClient delegate;
    private final String scheme;
    private final int listenPort;

    public SimpleHttpClient(SSLContext sslContext, int listenPort, boolean useCompression) {
        this(sslContext, null, null, listenPort, useCompression);
    }

    public SimpleHttpClient(SSLContext sslContext, List<String> enabledProtocols, List<String> enabledCiphers,
                            int listenPort, boolean useCompression) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        if (!useCompression) {
            builder.disableContentCompression();
        }
        if (sslContext != null) {
            SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    toArray(enabledProtocols),
                    toArray(enabledCiphers),
                    new DefaultHostnameVerifier());
            builder.setSSLSocketFactory(sslConnectionFactory);

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("https", sslConnectionFactory)
                    .build();
            builder.setConnectionManager(new BasicHttpClientConnectionManager(registry));
            scheme = "https";
        } else {
            scheme = "http";
        }
        this.delegate = builder.build();
        this.listenPort = listenPort;
    }

    private static String[] toArray(List<String> list) {
        return list != null ? list.toArray(new String[0]) : null;
    }

    public URI newUri(final String path) {
        return URI.create(scheme + "://localhost:" + listenPort + path);
    }

    public RequestExecutor newGet(final String path) {
        return newRequest(new HttpGet(newUri(path)));
    }

    public RequestExecutor newPost(final String path) {
        return newRequest(new HttpPost(newUri(path)));
    }

    public RequestExecutor newRequest(final HttpUriRequest request) {
        return new RequestExecutor().setRequest(request);
    }

    public ResponseValidator execute(final HttpUriRequest request) throws IOException {
        return newRequest(request).execute();
    }

    public ResponseValidator get(final String path) throws IOException {
        return newGet(path).execute();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public class RequestExecutor {

        private HttpUriRequest request;
        private HttpEntity entity;

        public RequestExecutor setRequest(final HttpUriRequest request) {
            this.request = request;
            return this;
        }

        public RequestExecutor addHeader(final String name, final String value) {
            this.request.addHeader(name, value);
            return this;
        }

        public RequestExecutor setContent(final String content) {
            this.entity = new StringEntity(content, StandardCharsets.UTF_8);
            return this;
        }

        public RequestExecutor setGzipContent(String content) {
            this.entity = new GzipCompressingEntity(new StringEntity(content, StandardCharsets.UTF_8));
            return this;
        }

        public RequestExecutor setBinaryContent(final byte[] content) {
            this.entity = new ByteArrayEntity(content);
            return this;
        }

        public RequestExecutor setMultipartContent(final FormBodyPart... parts) {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.stream(parts).forEach(part -> builder.addPart(part.getName(), part.getBody()));
            this.entity = builder.build();
            return this;
        }

        public ResponseValidator execute() throws IOException {
            if (entity != null) {
                ((HttpPost)request).setEntity(entity);
            }
            try (CloseableHttpResponse response = delegate.execute(request)){
                return new ResponseValidator(response);
            }
        }
    }

    public static class ResponseValidator {

        private final HttpResponse response;
        private final String content;

        public ResponseValidator(final HttpResponse response) throws IOException {
            this.response = response;

            final HttpEntity entity = response.getEntity();
            this.content = entity == null ? null :
                           EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }

        public ResponseValidator expectStatusCode(final Matcher<Integer> matcher) {
            MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), matcher);
            return this;
        }

        public ResponseValidator expectHeader(final String headerName, final Matcher<String> matcher) {
            final Header firstHeader = response.getFirstHeader(headerName);
            final String headerValue = firstHeader != null ? firstHeader.getValue() : null;
            MatcherAssert.assertThat(headerValue, matcher);
            assertThat(firstHeader, is(not(nullValue())));
            return this;
        }

        public ResponseValidator expectNoHeader(final String headerName) {
            final Header firstHeader = response.getFirstHeader(headerName);
            assertThat(firstHeader, is(nullValue()));
            return this;
        }

        public ResponseValidator expectContent(final Matcher<String> matcher) throws IOException {
            MatcherAssert.assertThat(content, matcher);
            return this;
        }

    }
}
