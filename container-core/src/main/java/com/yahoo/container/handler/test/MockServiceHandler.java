// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.test;

import com.google.common.annotations.Beta;
import com.yahoo.container.jdisc.HttpRequest;

/**
 * A service handler that is able to map a request to a key and retrieve a value given a key.
 *
 * @author Ulf Lilleengen
 */
@Beta
public interface MockServiceHandler {

    /**
     * Create a custom Key given a http request. This will be called for each request, and allows a handler
     * to customize its key format.
     * @param request The client http request.
     * @return a {@link Key} used to query for the value.
     */
    Key createKey(HttpRequest request);

    /**
     * Lookup a {@link Value} for a {@link Key}. Returns null if the key is not found.
     *
     * @param key The {@link Key} to look up.
     * @return A {@link Value} used as response.
     */
    Value get(Key key);

    final class Value {

        public final int returnCode;
        public final byte[] data;
        public final String contentType;

        public Value(int returnCode, byte[] data, String contentType) {
            this.returnCode = returnCode;
            this.data = data;
            this.contentType = contentType;
        }

    }

    interface Key {

        int hashCode();
        boolean equals(Object other);

    }
}
