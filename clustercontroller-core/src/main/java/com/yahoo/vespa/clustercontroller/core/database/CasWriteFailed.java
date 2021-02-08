// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

/**
 * Exception used to signal that a write intended to overwrite a value previously
 * read encountered an underlying version conflict during an atomic compare-and-swap
 * operation. This generally means that another node has written to the value since
 * we last read it, and that the information we hold may be stale.
 *
 * Upon receiving such an exception, the caller should no longer assume it holds
 * up-to-date information and should drop any roles that build on top of such an
 * assumption (such as leadership sessions).
 */
public class CasWriteFailed extends RuntimeException {

    public CasWriteFailed(String message) {
        super(message);
    }

    public CasWriteFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
