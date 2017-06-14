// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

/**
 * Exception to be thrown by a document processor on transient failures.&nbsp;Caller
 * is welcome to try the call again later.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class TransientFailureException extends RuntimeException {
    public TransientFailureException(String s) {
        super(s);
    }
}
