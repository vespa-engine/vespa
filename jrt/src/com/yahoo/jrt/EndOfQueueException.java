// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Checked exception thrown when someone tries to dequeue an object
 * from a closed and empty {@link ThreadQueue}.
 **/
class EndOfQueueException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Create a EndOfQueueException.
     **/
    EndOfQueueException() {
        super();
    }
}
