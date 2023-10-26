// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Utility methods relating to IO
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class IO {

    /**
     * Creates a new file and all its parent directories, and provides a file output stream to the file.
     */
    public static <T> T withFileOutputStream(File file, ThrowingFunction<OutputStream, T> f) {
        makeDirectoriesRecursive(file.getParentFile());
        try (FileOutputStream fos = new FileOutputStream(file)) {
            return f.apply(fos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void makeDirectoriesRecursive(File file) {
        if (!file.mkdirs() && !file.isDirectory()) {
            throw new RuntimeException("Could not create directory " + file.getPath());
        }
    }
}
