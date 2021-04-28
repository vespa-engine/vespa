// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * @author jonmv
 */
public interface ConfigServerClient extends Closeable {

    RequestConfig defaultRequestConfig = RequestConfig.custom()
                                                      .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                                                      .setConnectTimeout(Timeout.ofSeconds(5))
                                                      .setRedirectsEnabled(false)
                                                      .build();

    /** Wraps with a {@link RetryException} and rethrows. */
    Consumer<IOException> retryAll = (e) -> {
        throw new RetryException(e);
    };

    /** Throws a a {@link RetryException} if {@code statusCode == 503}, or a {@link ResponseException} unless {@code 200 <= statusCode < 300}. */
    ResponseVerifier throwOnError = new DefaultResponseVerifier() { };

    /** Reads the response body, throwing an {@link UncheckedIOException} if this fails, or {@code null} if there is none. */
    static byte[] getBytes(ClassicHttpResponse response) {
        try {
            return response.getEntity() == null ? null : EntityUtils.toByteArray(response.getEntity());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Creates a builder for sending the given method, using the specified host strategy. */
    RequestBuilder send(HostStrategy hosts, Method method);

    /** Builder for a request against a given set of hosts, using this config server client. */
    interface RequestBuilder {

        /** Appends to the request path. */
        default RequestBuilder at(String... pathSegments) { return at(List.of(pathSegments)); }

        /** Appends to the request path. */
        RequestBuilder at(List<String> pathSegments);

        /** Sets the request body as UTF-8 application/json. */
        RequestBuilder body(byte[] json);

        /** Sets the request body. */
        RequestBuilder body(HttpEntity entity);

        /** Sets query parameters without a value, like {@code ?debug&recursive}. */
        default RequestBuilder emptyParameters(String... keys) {
            return emptyParameters(Arrays.asList(keys));
        }

        /** Sets query parameters without a value, like {@code ?debug&recursive}. */
        RequestBuilder emptyParameters(List<String> keys);

        /** Sets the parameter key/values for the request. Number of arguments must be even. Null values are omitted. */
        default RequestBuilder parameters(String... pairs) {
            return parameters(Arrays.asList(pairs));
        }

        /** Sets the parameter key/values for the request. Number of arguments must be even. Null values are omitted. */
        RequestBuilder parameters(List<String> pairs);

        /** Overrides the default socket read timeout of the request. {@code Duration.ZERO} gives infinite timeout. */
        RequestBuilder timeout(Duration timeout);

        /** Overrides the default request config of the request. */
        RequestBuilder config(RequestConfig config);

        /**
         * Sets the catch clause for {@link IOException}s during execution of this.
         * The default is to wrap the IOException in a {@link RetryException} and rethrow this;
         * this makes the client retry the request, as long as there are remaining entries in the {@link HostStrategy}.
         * If the catcher returns normally, the {@link IOException} is unchecked and thrown instead.
         */
         RequestBuilder catching(Consumer<IOException> catcher);

        /**
         * Sets the (error) response handler for this request. The default is {@link #throwOnError}.
         * When the handler returns normally, the response is treated as a success, and passed on to a response mapper.
         */
         RequestBuilder throwing(ResponseVerifier handler);

        /** Reads the response as a {@link String}, or throws if unsuccessful. */
        String read();

        /** Reads and maps the response, or throws if unsuccessful. */
        <T> T read(Function<byte[], T> mapper);

        /** Discards the response, but throws if unsuccessful. */
        void discard();

        /** Returns the raw response input stream, or throws if unsuccessful. The caller must close the returned stream. */
        HttpInputStream stream();

        /** Uses the response and request, if successful, to generate a mapped response. */
        <T> T handle(ResponseHandler<T> handler);

    }


    class HttpInputStream extends ForwardingInputStream {

        private final ClassicHttpResponse response;

        protected HttpInputStream(ClassicHttpResponse response) throws IOException {
            super(response.getEntity() != null ? response.getEntity().getContent()
                                               : InputStream.nullInputStream());
            this.response = response;
        }

        public int statusCode() { return response.getCode(); }

        public String contentType() { return response.getEntity().getContentType(); }

        @Override
        public void close() throws IOException {
            super.close();
            response.close();
        }

    }


    /** Reads a successful response and request to compute a result. */
    @FunctionalInterface
    interface ResponseHandler<T> {

        /** Called with successful responses, as per {@link ResponseVerifier}. The caller must close the response. */
        T handle(ClassicHttpResponse response, ClassicHttpRequest request) throws IOException;

    }


    /** Verifies a response, throwing on error responses, possibly indicating retries. */
    @FunctionalInterface
    interface ResponseVerifier {

        /** Whether this status code means the response is an error response. */
        default boolean isError(int statusCode) {
            return statusCode < HttpStatus.SC_OK || HttpStatus.SC_REDIRECTION <= statusCode;
        }

        /** Whether this status code means we should retry. Has no effect if this is not also an error. */
        default boolean shouldRetry(int statusCode) {
            return statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE;
        }

        /** Verifies the given response, consuming it and throwing if it is an error; or leaving it otherwise. */
        default void verify(ClassicHttpResponse response, ClassicHttpRequest request) throws IOException {
            if (isError(response.getCode())) {
                try (response) {
                    byte[] body = response.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(response.getEntity());
                    RuntimeException exception = toException(response.getCode(), body, request);
                    throw shouldRetry(response.getCode()) ? new RetryException(exception) : exception;
                }
            }
        }

        /** Throws the appropriate exception, for the given status code and body. */
        RuntimeException toException(int statusCode, byte[] body, ClassicHttpRequest request);

    }


    interface DefaultResponseVerifier extends ResponseVerifier {

        @Override
        default RuntimeException toException(int statusCode, byte[] body, ClassicHttpRequest request) {
            return new ResponseException(request + " failed with status " + statusCode + " and body '" + new String(body, UTF_8) + "'");
        }

    }


    /** What host(s) to try for a request, in what order. A host may be specified multiple times, for retries.  */
    @FunctionalInterface
    interface HostStrategy extends Iterable<URI> {

        /** Attempts each request once against each listed host. */
        static HostStrategy ordered(List<URI> hosts) {
            return List.copyOf(hosts)::iterator;
        }

        /** Attempts each request once against each listed host, in random order. */
        static HostStrategy shuffling(List<URI> hosts) {
            return () -> {
                List<URI> copy = new ArrayList<>(hosts);
                Collections.shuffle(copy);
                return copy.iterator();
            };
        }

        /** Attempts each request against the host the specified number of times. */
        static HostStrategy repeating(URI host, int count) {
            return ordered(IntStream.range(0, count).mapToObj(__ -> host).collect(toUnmodifiableList()));
        }

    }


    /** Exception wrapper that signals retries should be attempted. */
    final class RetryException extends RuntimeException {

        public RetryException(IOException cause) {
            super(requireNonNull(cause));
        }

        public RetryException(RuntimeException cause) {
            super(requireNonNull(cause));
        }

    }


    /** An exception due to server error, a bad request, or similar, which resulted in a non-OK HTTP response. */
    class ResponseException extends RuntimeException {

        public ResponseException(String message) {
            super(message);
        }

    }

}