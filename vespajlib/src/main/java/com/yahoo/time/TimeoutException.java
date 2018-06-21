// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.time;

/**
 * Exception thrown when a blocking operation times out.
 *
 * <p>Unchecked variant of {@link java.util.concurrent.TimeoutException}
 *
 * @author hakon
 */
@SuppressWarnings("serial")
public class TimeoutException extends RuntimeException {
    public TimeoutException() { }

    public TimeoutException(String message) {
        super(message);
    }
}
