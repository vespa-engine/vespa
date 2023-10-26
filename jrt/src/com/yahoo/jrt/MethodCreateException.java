// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Unchecked exception thrown when the {@link Method} constructor
 * fails to resolve the method handler. The most usual reasons for
 * this is that the method handler simply does not exist or that it
 * has the wrong signature.
 **/
public class MethodCreateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a MethodCreateException with the given message.
     *
     * @param msg the exception message
     **/
    MethodCreateException(String msg) {
        super(msg);
    }

    /**
     * Create a MethodCreateException with the given message and
     * cause.
     *
     * @param msg the exception message
     * @param cause what caused this exception
     **/
    MethodCreateException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
