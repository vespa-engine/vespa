// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Helper methods for handling exceptions
 *
 * @author bratseth
 */
public class Exceptions {

    /**
     * <p>Returns a user friendly error message string which includes information from all nested exceptions.</p>
     *
     * <p>The form of this string is
     * <code>e.getMessage(): e.getCause().getMessage(): e.getCause().getCause().getMessage()...</code>
     * In addition, some heuristics are used to clean up common cases where exception nesting causes bad messages.
     */
    public static String toMessageString(Throwable t) {
        StringBuilder b = new StringBuilder();
        String lastMessage = null;
        String message;
        for (; t != null; t = t.getCause()) {
            message = getMessage(t);
            if (message == null) continue;
            if (message.equals(lastMessage)) continue;
            if (b.length() > 0) {
                b.append(": ");
            }
            b.append(message);
            lastMessage = message;
        }
        return b.toString();
    }

    /** Returns a useful message from *this* exception, or null if there is nothing useful to return */
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

    /**
     * Wraps any IOException thrown from a runnable in an UncheckedIOException.
     */
    public static void uncheck(RunnableThrowingIOException runnable) {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Wraps any IOException thrown from a runnable in an UncheckedIOException w/message.
     */
    public static void uncheck(RunnableThrowingIOException runnable, String format, String... args) {
        try {
            runnable.run();
        } catch (IOException e) {
            String message = String.format(format, (Object[]) args);
            throw new UncheckedIOException(message, e);
        }
    }

    @FunctionalInterface
    public interface RunnableThrowingIOException {
        void run() throws IOException;
    }

    /**
     * Wraps any IOException thrown from a supplier in an UncheckedIOException.
     */
    public static <T> T uncheck(SupplierThrowingIOException<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Wraps any IOException thrown from a supplier in an UncheckedIOException w/message.
     */
    public static <T> T uncheck(SupplierThrowingIOException<T> supplier, String format, String... args) {
        try {
            return supplier.get();
        } catch (IOException e) {
            String message = String.format(format, (Object[]) args);
            throw new UncheckedIOException(message, e);
        }
    }

    @FunctionalInterface
    public interface SupplierThrowingIOException<T> {
        T get() throws IOException;
    }
}
