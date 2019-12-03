// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jdk8compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * Backport of new {@link java.nio.file.Files} methods added after JDK8
 *
 * @author bjorncs
 */
public interface Files {

    static String readString(Path path) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static Path writeString(Path path, CharSequence string, OpenOption... options) throws IOException {
        return java.nio.file.Files.write(path, string.toString().getBytes(), options);
    }
}
