// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

/**
 * An exception that can be thrown by {@link ZtsClient} implementations.
 *
 * @author bjorncs
 */
public class ZtsClientException extends RuntimeException {

    public ZtsClientException(String message) {
        super(message);
    }

    public ZtsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
