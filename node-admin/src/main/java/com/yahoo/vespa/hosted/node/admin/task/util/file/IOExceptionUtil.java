// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.yolean.Exceptions;

import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Utils related to IOException.
 *
 * @author hakonhall
 */
public class IOExceptionUtil {
    /**
     * Useful if it's not known whether a file or directory exists, in case e.g.
     * NoSuchFileException is thrown and the caller wants an Optional.empty() in that case.
     */
    public static <T> Optional<T> ifExists(Exceptions.SupplierThrowingIOException<T> supplier) {
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
