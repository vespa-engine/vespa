// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.exception;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Misc exception utility methods - replacement for Apache commons-lang's ExceptionUtils
 *
 * @author bjorncs
 */
public class ExceptionUtils {

    private ExceptionUtils() {}

    public static String getStackTraceAsString(Throwable throwable) {
        try (StringWriter stringWriter = new StringWriter();
             PrintWriter printWriter = new PrintWriter(stringWriter, true)) {
            throwable.printStackTrace(printWriter);
            return stringWriter.getBuffer().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String getStackTraceRecursivelyAsString(Throwable throwable) {
        Throwable cause = throwable;
        try (StringWriter stringWriter = new StringWriter();
             PrintWriter printWriter = new PrintWriter(stringWriter, true)) {
            do {
                cause.printStackTrace(printWriter);
            } while ((cause = cause.getCause()) != null);
            return stringWriter.getBuffer().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
