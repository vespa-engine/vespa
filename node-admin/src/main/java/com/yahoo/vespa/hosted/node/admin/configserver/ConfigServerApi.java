// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import java.util.Optional;

/**
 * Interface to execute basic HTTP request against config server(s)
 *
 * @author freva
 */
public interface ConfigServerApi extends AutoCloseable {

    <T> T get(String path, Class<T> wantedReturnType);

    <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType);

    <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType);

    <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType);

    <T> T delete(String path, Class<T> wantedReturnType);

    /**
     * Close the underlying HTTP client and any threads this class might have started.
     */
    @Override
    void close();
}
