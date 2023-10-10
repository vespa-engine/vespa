// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

/**
 * Thrown on timeouts
 *
 * @author bratseth
 */
@SuppressWarnings("serial")
public class TimeoutException extends RuntimeException {

    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(String message,Throwable cause) {
        super(message,cause);
    }

}
