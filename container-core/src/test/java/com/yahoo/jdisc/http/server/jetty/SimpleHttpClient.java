// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.entity.GzipCompressingEntity;
import org.apache.hc.client5.http.entity.mime.FormBodyPart;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.hamcrest.Matcher;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        HttpClientBuilder builder = HttpClientBuilder.create()
                .disableAutomaticRetries()
                .disableConnectionState(); // Reuse SSL connection when client authentication is enabled
        if (!useCompression) {
            builder.disableContentCompression();
        }
        if (sslContext != null) {
            SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    toArray(enabledProtocols),
                    toArray(enabledCiphers),
                    new DefaultHostnameVerifier());
            PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslConnectionFactory)
                    .setDnsResolver(new SystemDefaultDnsResolver() {
                        @Override
                        public InetAddress[] resolve(String host) throws UnknownHostException {
                            // Returns single address instead of multiple (to avoid multiple connection attempts)
                            return new InetAddress[] { InetAddress.getByName(host) };
                        }
                    })
                    .build();
            builder.setConnectionManager(connManager);
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

    public RequestExecutor newGet(String path) {
        return newRequest(new HttpGet(newUri(path)));
    }

    public RequestExecutor newPost(String path) {
        return newRequest(new HttpPost(newUri(path)));
    }

    public RequestExecutor newRequest(HttpUriRequest request) {
        return new RequestExecutor().setRequest(request);
    }

    public ResponseValidator execute(HttpUriRequest request) throws IOException {
        return newRequest(request).execute();
    }

    public ResponseValidator get(String path) throws IOException {
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
            this.entity = new ByteArrayEntity(content, ContentType.DEFAULT_BINARY);
            return this;
        }

        public RequestExecutor setMultipartContent(final FormBodyPart... parts) {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.stream(parts).forEach(part -> builder.addPart(part.getName(), part.getBody()));
            this.entity = builder.build();
            return this;
        }

        @SuppressWarnings("deprecation")
        public ResponseValidator execute() throws IOException {
            if (entity != null) {
                request.setEntity(entity);
            }
            try (CloseableHttpResponse response = delegate.execute(request)){
                return new ResponseValidator(response);
            }
        }
    }

    public static class ResponseValidator {

        private final HttpResponse response;
        private final String content;

        public ResponseValidator(CloseableHttpResponse response) throws IOException {
            try {
                this.response = response;

                HttpEntity entity = response.getEntity();
                this.content = entity == null ? null : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            } catch (ParseException e) {
                throw new IOException(e);
            }
        }

        public ResponseValidator expectStatusCode(Matcher<Integer> matcher) {
            assertThat(response.getCode(), matcher);
            return this;
        }

        public void expectHeader(String headerName, Matcher<String> matcher) {
            Header firstHeader = response.getFirstHeader(headerName);
            String headerValue = firstHeader != null ? firstHeader.getValue() : null;
            assertThat(headerValue, matcher);
            assertNotNull(firstHeader);
        }

        public void expectNoHeader(String headerName) {
            Header firstHeader = response.getFirstHeader(headerName);
            assertThat(firstHeader, is(nullValue()));
        }

        public ResponseValidator expectContent(final Matcher<String> matcher) {
            assertThat(content, matcher);
            return this;
        }

    }

}
