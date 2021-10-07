// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

/**
 * Exception specially handled to avoid dumping full stack trace on convergence failure.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class ConvergenceException extends RuntimeException {
    public ConvergenceException(String message) {
        super(message);
    }

    public ConvergenceException(String message, Throwable t) {
        super(message, t);
    }
}
