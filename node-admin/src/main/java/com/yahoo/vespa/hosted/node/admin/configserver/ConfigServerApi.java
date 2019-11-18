// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import java.time.Duration;
import java.util.Optional;

/**
 * Interface to execute basic HTTP/HTTPS request against config server(s)
 *
 * @author freva
 */
public interface ConfigServerApi extends AutoCloseable {
    class Params {
        private Optional<Duration> connectionTimeout;

        /** Set the socket connect and read timeouts. */
        public Params setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = Optional.of(connectionTimeout);
            return this;
        }

        public Optional<Duration> getConnectionTimeout() { return connectionTimeout; }
    }

    <T> T get(String path, Class<T> wantedReturnType, Params params);
    default <T> T get(String path, Class<T> wantedReturnType) {
        return get(path, wantedReturnType, null);
    }

    <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType, Params params);
    default <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return post(path, bodyJsonPojo, wantedReturnType, null);
    }

    <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType, Params params);
    default <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return put(path, bodyJsonPojo, wantedReturnType, null);
    }

    <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType, Params params);
    default <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return patch(path, bodyJsonPojo, wantedReturnType, null);
    }

    <T> T delete(String path, Class<T> wantedReturnType, Params params);
    default <T> T delete(String path, Class<T> wantedReturnType) {
        return delete(path, wantedReturnType, null);
    }

    /** Close the underlying HTTP client and any threads this class might have started. */
    @Override
    void close();
}
