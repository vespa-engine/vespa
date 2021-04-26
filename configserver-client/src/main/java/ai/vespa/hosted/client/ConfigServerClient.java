// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
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
import java.util.function.BiFunction;
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
    static void retryAll(IOException e) {
        throw new RetryException(e);
    }

    /** Throws a a {@link RetryException} if {@code statusCode == 503}, or a {@link ResponseException} unless {@code 200 <= statusCode < 300}. */
    static void throwOnError(ClassicHttpResponse response, ClassicHttpRequest request) {
        if (response.getCode() < HttpStatus.SC_OK || response.getCode() >= HttpStatus.SC_REDIRECTION) {
            ResponseException e = ResponseException.of(response, request);
            if (response.getCode() == HttpStatus.SC_SERVICE_UNAVAILABLE)
                throw new RetryException(e);

            throw e;
        }
    }

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

        /** Sets the request path. */
        default RequestBuilder at(String... pathSegments) { return at(List.of(pathSegments)); }

        /** Sets the request path. */
        RequestBuilder at(List<String> pathSegments);

        /** Sets the request body as UTF-8 application/json. */
        RequestBuilder body(byte[] json);

        /** Sets the request body. */
        RequestBuilder body(HttpEntity entity);

        /** Sets the parameter key/values for the request. Number of arguments must be even. */
        default RequestBuilder parameters(String... pairs) {
            return parameters(Arrays.asList(pairs));
        }

        /** Sets the parameter key/values for the request. Number of arguments must be even. */
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
         RequestBuilder handling(BiConsumer<ClassicHttpResponse, ClassicHttpRequest> handler);

        /** Reads and maps the response, or throws if unsuccessful. */
        <T> T read(Function<byte[], T> mapper);

        /** Discards the response, but throws if unsuccessful. */
        void discard();

        /** Returns the raw response input stream, or throws if unsuccessful. The caller must close the returned stream. */
        InputStream stream();

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

        public ResponseException(String message, Throwable cause) {
            super(message, cause);
        }

        public static ResponseException of(ClassicHttpResponse response, ClassicHttpRequest request) {
            String detail;
            Throwable thrown = null;
            try {
                detail = request.getEntity() == null ? " and no body"
                                                     : " and body '" + EntityUtils.toString(request.getEntity()) + "'";
            }
            catch (IOException | ParseException e) {
                detail = ". Reading body failed";
                thrown = e;
            }
            return new ResponseException(request + " failed with status " + response.getCode() + detail, thrown);
        }

    }

}