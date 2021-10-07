// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Interface to execute basic HTTP/HTTPS request against config server(s)
 *
 * @author freva
 */
public interface ConfigServerApi extends AutoCloseable {

    /**
     * The result of sending a request to a config server results in a jackson response or exception.  If a response
     * is returned, an instance of this interface is conferred to discard the result and try the next config server,
     * unless it was the last attempt.
     *
     * @param <T> the type of the returned jackson response
     */
    interface RetryPolicy<T> {
        boolean tryNextConfigServer(URI configServerEndpoint, T response);
    }

    class Params<T> {
        private Optional<Duration> connectionTimeout = Optional.empty();

        private RetryPolicy<T> retryPolicy = (configServerEndpoint, response) -> false;

        public Params() {}

        /** Set the socket connect and read timeouts. */
        public Params<T> setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = Optional.of(connectionTimeout);
            return this;
        }

        public Optional<Duration> getConnectionTimeout() { return connectionTimeout; }

        /** Set the retry policy to use against the config servers. */
        public Params<T> setRetryPolicy(RetryPolicy<T> retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public RetryPolicy<T> getRetryPolicy() { return retryPolicy; }
    }

    <T> T get(String path, Class<T> wantedReturnType, Params<T> params);
    default <T> T get(String path, Class<T> wantedReturnType) {
        return get(path, wantedReturnType, new Params<>());
    }

    <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType, Params<T> params);
    default <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return post(path, bodyJsonPojo, wantedReturnType, new Params<>());
    }

    <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType, Params<T> params);
    default <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return put(path, bodyJsonPojo, wantedReturnType, new Params<>());
    }

    <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType, Params<T> params);
    default <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return patch(path, bodyJsonPojo, wantedReturnType, new Params<>());
    }

    <T> T delete(String path, Class<T> wantedReturnType, Params<T> params);
    default <T> T delete(String path, Class<T> wantedReturnType) {
        return delete(path, wantedReturnType, new Params<>());
    }

    /** Close the underlying HTTP client and any threads this class might have started. */
    @Override
    void close();
}
