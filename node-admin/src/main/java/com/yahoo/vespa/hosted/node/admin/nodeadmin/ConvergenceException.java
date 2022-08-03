// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

/**
 * Exception specially handled to avoid dumping full stack trace on convergence failure.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class ConvergenceException extends RuntimeException {
    /** Create an exception that will NOT increment the monitored unhandled_exceptions metric. */
    public static ConvergenceException ofTransient(String message) { return ofTransient(message, null); }

    /** Create an exception that will NOT increment the monitored unhandled_exceptions metric. */
    public static ConvergenceException ofTransient(String message, Throwable t) { return new ConvergenceException(message, t, false); }

    /** Create an exception that increments the monitored unhandled_exceptions metric. */
    public static ConvergenceException ofError(String message) { return ofError(message, null); }

    /** Create an exception that increments the monitored unhandled_exceptions metric. */
    public static ConvergenceException ofError(String message, Throwable t) { return new ConvergenceException(message, t, true); }

    /** Create an exception with the same transient/error as the cause. */
    public static ConvergenceException ofNested(String message, ConvergenceException cause) { return new ConvergenceException(message, cause, cause.isError); }

    private final boolean isError;

    /** @param isError whether the exception should increment the monitored unhandled_exception metric. */
    protected ConvergenceException(String message, boolean isError) {
        this(message, null, isError);
    }

    /** @param isError whether the exception should increment the monitored unhandled_exception metric. */
    protected ConvergenceException(String message, Throwable t, boolean isError) {
        super(message, t);
        this.isError = isError;
    }

    /** Whether the exception signals an error someone may want to look at, or whether it is expected to be transient (false). */
    public boolean isError() { return isError; }
}
