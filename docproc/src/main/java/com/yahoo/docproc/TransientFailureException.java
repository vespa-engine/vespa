// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

/**
 * Exception to be thrown by a document processor on transient failures.
 * Caller is welcome to try the call again later.
 *
 * @author Einar M R Rosenvinge
 * @deprecated  Will be removed in Vespa 8. Only for internal use.
 */
@Deprecated(forRemoval = true, since = "7")
public class TransientFailureException extends RuntimeException {

    public TransientFailureException(String s) {
        super(s);
    }

}
