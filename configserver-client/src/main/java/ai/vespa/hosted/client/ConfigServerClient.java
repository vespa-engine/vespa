// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * @author jonmv
 */
public interface ConfigServerClient extends AutoCloseable {

    /** Creates a builder for sending the given method, using the specified host strategy. */
    RequestBuilder send(HostStrategy hosts, Method method);

    /** Builder for a request against a given set of hosts, using this config server client. */
    interface RequestBuilder {

        /** Sets the request path. */
        RequestBuilder at(String... pathSegments);

        /** Sets the request body as UTF-8 application/json. */
        RequestBuilder body(byte[] json);

        /** Sets the request body. */
        RequestBuilder body(HttpEntity entity);

        /** Sets the parameter key/values for the request. Number of arguments must be even. */
        RequestBuilder parameters(String... pairs);

        /** Overrides the default socket read timeout of the request. {@code Duration.ZERO} gives infinite timeout. */
        RequestBuilder timeout(Duration timeout);

        /** Overrides the default request config of the request. */
        RequestBuilder config(RequestConfig config);

        /**
         * Sets custom retry/failure logic for this.
         * <p>
         * Exactly one of the callbacks will be invoked, with a non-null argument.
         * Return a value to have that returned to the caller;
         * throw a {@link RetryException} to have the request retried; or
         * throw any other unchecked exception to have this propagate out to the caller.
         * The caller must close the provided response, if any.
         */
        <T> T handle(Function<ClassicHttpResponse, T> handler, Function<IOException, T> catcher) throws UncheckedIOException;

        /** Sets the response body mapper for this, for successful requests. */
        <T> T read(Function<byte[], T> mapper) throws UncheckedIOException, ConfigServerException;

        /** Discards the response, but throws if the response is unsuccessful. */
        void discard() throws UncheckedIOException, ConfigServerException;

        /** Returns the raw input stream of the response, if successful. The caller must close the returned stream. */
        InputStream stream() throws UncheckedIOException, ConfigServerException;

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

    /** An exception due to server error, a bad request, or similar. */
    class ConfigServerException extends RuntimeException {

        private final ErrorCode errorId;
        private final String message;

        public ConfigServerException(ErrorCode errorId, String message, String context) {
            super(context + ": " + message);
            this.errorId = errorId;
            this.message = message;
        }

        public ErrorCode errorId() { return errorId; }

        public String message() { return message; }

        public enum ErrorCode {
            APPLICATION_LOCK_FAILURE,
            BAD_REQUEST,
            ACTIVATION_CONFLICT,
            INTERNAL_SERVER_ERROR,
            INVALID_APPLICATION_PACKAGE,
            METHOD_NOT_ALLOWED,
            NOT_FOUND,
            OUT_OF_CAPACITY,
            REQUEST_TIMEOUT,
            UNKNOWN_VESPA_VERSION,
            PARENT_HOST_NOT_READY,
            CERTIFICATE_NOT_READY,
            LOAD_BALANCER_NOT_READY,
            INCOMPLETE_RESPONSE
        }

    }

}