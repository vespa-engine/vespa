// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.util;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @author hakon
 */
public class ExceptionUtil {
    /**
     * Remove any checked exceptions from the signature of a non-void expression.
     *
     * <p>Any checked exception thrown will be wrapped in an unchecked exception.
     *
     * <p>Example: {@code Files.readAllBytes()} throws {@code IOException}, which can be hidden
     * as follows:
     *
     * <pre>{@code
     *     byte[] bytes = uncheck(() -> Files.readAllBytes());
     * }</pre>
     */
    public static <T> T uncheck(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove any checked exceptions from the signature of a void expression.
     *
     * Any checked exception thrown will be wrapped in an unchecked exception.
     */
    public static void uncheck(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Make class uninstantiable. */
    private ExceptionUtil() {}
}
