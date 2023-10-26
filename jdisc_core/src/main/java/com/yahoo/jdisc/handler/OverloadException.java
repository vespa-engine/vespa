// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

/**
 * An exception to signal abort current action, as the container is overloaded.
 * Just unroll state as cheaply as possible.
 *
 * The contract of OverloadException is:
 *
 * <ol>
 *   <li>You must set the response yourself first, or you'll get an internal server error.
 *   <li>You must throw it from handleRequest synchronously.
 * </ol>
 *
 * @author Steinar Knutsen
 */
public class OverloadException extends RuntimeException {
    public OverloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
