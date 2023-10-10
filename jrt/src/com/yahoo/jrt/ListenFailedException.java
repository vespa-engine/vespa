// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Checked exception thrown when listening fails.
 * @see Supervisor#listen
 **/
public class ListenFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Create a ListenFailedException with the given message.
     *
     * @param msg the exception message
     **/
    ListenFailedException(String msg) {
        super(msg);
    }

    /**
     * Create a ListenFailedException with the given message and cause.
     *
     * @param msg the exception message
     * @param cause what caused this exception
     **/
    ListenFailedException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
