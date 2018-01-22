// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;

public class IOExceptionUtil {
    public static <T> void uncheck(RunnableThrowingIOException<T> runnable) {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
}
