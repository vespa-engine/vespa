// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

public class TestFileUtil {
    protected static final String DATA_PATH = "./test/crosslanguagefiles";

    public static void writeToFile(String path, byte[] data) throws IOException {
        // Write to a temporary file to avoid racing with cross-language tests reading the
        // exact same file we're trying to write.
        String tmpPath = path + ".tmp";
        try (FileOutputStream stream = new FileOutputStream(tmpPath)) {
            stream.write(data);
            stream.flush();
        }
        // We make the assumption that all file systems we run these tests on support some form
        // of atomic moving rather than "move by content copy".
        Files.move(FileSystems.getDefault().getPath(tmpPath), FileSystems.getDefault().getPath(path), ATOMIC_MOVE);
    }

    /**
     * Write `data` to `path` using UTF-8 as binary encoding format.
     */
    public static void writeToFile(String path, String data) throws IOException {
        writeToFile(path, data.getBytes(Charset.forName("UTF-8")));
    }

    /**
     * Returns the path to use for data files.
     *
     * @param filename The name of the file to include in the path.
     * @return The data file path.
     */
    public static String getPath(String filename) {
        return DATA_PATH + "/" + filename;
    }

    public static byte[] readFile(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }
}
