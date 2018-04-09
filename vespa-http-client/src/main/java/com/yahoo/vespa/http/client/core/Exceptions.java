// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

/**
 * Helper methods for handling exceptions
 *
 * @author bratseth
 */
public abstract class Exceptions {

    /**
     * <p>Returns a use friendly error message string which includes information from all nested exceptions.</p>
     *
     * <p>The form of this string is
     * <code>e.getMessage(): e.getCause().getMessage(): e.getCause().getCause().getMessage()...</code>
     * In addition, some heuristics are used to clean up common cases where exception nesting causes bad messages.
     * </p>
     */
    public static String toMessageString(Throwable t) {
        StringBuilder b = new StringBuilder();
        String lastMessage = null;
        String message;
        for (; t != null; t = t.getCause(), lastMessage = message) {
            message = getMessage(t);
            if (message == null) continue;
            if (message.equals(lastMessage)) continue;
            if (b.length() > 0) {
                b.append(": ");
            }
            b.append(message);
        }
        return b.toString();
    }

    /** Returns a useful message from *this* exception, or null if none */
    private static String getMessage(Throwable t) {
        String message = t.getMessage();
        if (t.getCause() == null) {
            if (message == null) return t.getClass().getSimpleName();
        } else {
            if (message == null) return null;
            if (message.equals(t.getCause().getClass().getName() + ": " + t.getCause().getMessage())) return null;
        }
        return message;
    }

}
