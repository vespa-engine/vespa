// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;

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
     * Returns the first cause or the given throwable that is an instance of {@code clazz}
     */
    public static <T extends Throwable> Optional<T> findCause(Throwable t, Class<T> clazz) {
        for (; t != null; t = t.getCause()) {
            if (clazz.isInstance(t))
                return Optional.of(clazz.cast(t));
        }
        return Optional.empty();
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

    public static void uncheckInterrupted(RunnableThrowingInterruptedException runnable) {
        try {
            runnable.run();
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e, false);
        }
    }

    public static void uncheckInterruptedAndRestoreFlag(RunnableThrowingInterruptedException runnable) {
        try {
            runnable.run();
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e, true);
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

    /** Similar to uncheck(), except an exceptionToIgnore exception is silently ignored. */
    public static <T extends IOException> void uncheckAndIgnore(RunnableThrowingIOException runnable, Class<T> exceptionToIgnore) {
        try {
            runnable.run();
        } catch (UncheckedIOException e) {
            IOException cause = e.getCause();
            if (cause == null) throw e;
            try {
                cause.getClass().asSubclass(exceptionToIgnore);
            } catch (ClassCastException f) {
                throw e;
            }
            // Do nothing - OK
        } catch (IOException e) {
            try {
                e.getClass().asSubclass(exceptionToIgnore);
            } catch (ClassCastException f) {
                throw new UncheckedIOException(e);
            }
            // Do nothing - OK
        }
    }

    @FunctionalInterface
    public interface RunnableThrowingIOException {
        void run() throws IOException;
    }

    @FunctionalInterface public interface RunnableThrowingInterruptedException { void run() throws InterruptedException; }

    /**
     * Wraps any IOException thrown from a function in an UncheckedIOException.
     */
    public static <T, R> Function<T, R> uncheck(FunctionThrowingIOException<T, R> function) {
        return t -> uncheck(() -> function.map(t));
    }
    @FunctionalInterface public interface FunctionThrowingIOException<T, R> { R map(T t) throws IOException; }

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

    /** Similar to uncheck(), except null is returned if exceptionToIgnore is thrown. */
    public static <R, T extends IOException> R uncheckAndIgnore(SupplierThrowingIOException<R> supplier, Class<T> exceptionToIgnore) {
        try {
            return supplier.get();
        } catch (UncheckedIOException e) {
            IOException cause = e.getCause();
            if (cause == null) throw e;
            try {
                cause.getClass().asSubclass(exceptionToIgnore);
            } catch (ClassCastException f) {
                throw e;
            }
            return null;
        } catch (IOException e) {
            try {
                e.getClass().asSubclass(exceptionToIgnore);
            } catch (ClassCastException f) {
                throw new UncheckedIOException(e);
            }
            return null;
        }
    }

    @FunctionalInterface
    public interface SupplierThrowingIOException<T> {
        T get() throws IOException;
    }

    /**
     * Allows treating checked exceptions as unchecked.
     * Usage:
     * throw throwUnchecked(e);
     * The reason for the return type is to allow writing throw at the call site
     * instead of just calling throwUnchecked. Just calling throwUnchecked
     * means that the java compiler won't know that the statement will throw an exception,
     * and will therefore complain on things such e.g. missing return value.
     */
    public static RuntimeException throwUnchecked(Throwable e) {
        throwUncheckedImpl(e);
        return new RuntimeException(); // Non-null return value to stop tooling from complaining about potential NPE
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUncheckedImpl(Throwable t) throws T {
        throw (T)t;
    }


}
