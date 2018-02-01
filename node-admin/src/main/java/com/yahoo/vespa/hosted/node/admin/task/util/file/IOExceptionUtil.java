// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

/**
 * @author hakonhall
 */
public class IOExceptionUtil {
    public static <T> void uncheck(RunnableThrowingIOException<T> runnable) {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * If an IOException is thrown, an UncheckedIOException is made with the given format+args
     * message. Useful to decorate the exception when the bare IOException leaves out details.
     */
    public static <T> T uncheck(SupplierThrowingIOException<T> supplier,
                                String format,
                                String... args) {
        try {
            return supplier.get();
        } catch (IOException e) {
            String message = String.format(format, (Object[]) args);
            throw new UncheckedIOException(message, e);
        }
    }

    public static <T> T uncheck(SupplierThrowingIOException<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    public interface SupplierThrowingIOException<T> {
        T get() throws IOException;
    }


    @FunctionalInterface
    public interface RunnableThrowingIOException<T> {
        void run() throws IOException;
    }

    /**
     * Useful if it's not known whether a file or directory exists, in case e.g.
     * NoSuchFileException is thrown and the caller wants an Optional.empty() in that case.
     */
    public static <T> Optional<T> ifExists(SupplierThrowingIOException<T> supplier) {
        try {
            return Optional.ofNullable(uncheck(supplier));
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof NoSuchFileException) {
                return Optional.empty();
            }

            throw e;
        }
    }
}
