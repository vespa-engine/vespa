// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

/**
 * A wrapper to make exceptions leaking into Jetty easier to track. Jetty
 * swallows all information about where an exception was thrown, so this wrapper
 * ensures some extra information is automatically added to the contents of
 * getMessage().
 *
 * @author Steinar Knutsen
 */
public class ExceptionWrapper extends RuntimeException {
    private final String message;

    /**
     * Update if serializable contents are added.
     */
    private static final long serialVersionUID = 1L;

    public ExceptionWrapper(Throwable t) {
        super(t);
        this.message = formatMessage(t);
    }

    // If calling methods from the constructor, it makes life easier if the
    // methods are static...
    private static String formatMessage(final Throwable t) {
        StringBuilder b = new StringBuilder();
        Throwable cause = t;
        while (cause != null) {
            StackTraceElement[] trace = cause.getStackTrace();
            String currentMsg = cause.getMessage();

            if (b.length() > 0) {
                b.append(": ");
            }
            b.append(t.getClass().getSimpleName()).append('(');
            if (currentMsg != null) {
                b.append('"').append(currentMsg).append('"');
            }
            b.append(')');
            if (trace.length > 0) {
                b.append(" at ").append(trace[0].getClassName()).append('(');
                if (trace[0].getFileName() != null) {
                    b.append(trace[0].getFileName()).append(':')
                            .append(trace[0].getLineNumber());
                }
                b.append(')');
            }
            cause = cause.getCause();
        }
        return b.toString();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
