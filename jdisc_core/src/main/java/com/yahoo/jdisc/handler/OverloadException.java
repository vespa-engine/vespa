// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

/**
 * An exception to signal abort current action, as the container is overloaded.
 * Just unroll state as cheaply as possible.
 *
 * <p>
 * The contract of OverloadException (for Jetty) is:
 * </p>
 *
 * <ol>
 * <li>You must set the response yourself first, or you'll get 500 internal
 * server error.</li>
 * <li>You must throw it from handleRequest synchronously.</li>
 * </ol>
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class OverloadException extends RuntimeException {
    public OverloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
