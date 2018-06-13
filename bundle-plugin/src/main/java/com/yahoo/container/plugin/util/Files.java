// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.io.File;
import java.io.FileOutputStream;
import java.util.stream.Stream;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class Files {
    public static Stream<File> allDescendantFiles(File file) {
        if (file.isFile()) {
            return Stream.of(file);
        } else if (file.isDirectory()) {
            return Stream.of(file.listFiles()).flatMap(Files::allDescendantFiles);
        } else {
            return Stream.empty();
        }
    }

    public static <T> T withFileOutputStream(File file, ThrowingFunction<FileOutputStream, T> f) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            return f.apply(fos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
