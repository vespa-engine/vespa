// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public class TestFileUtil {
    protected static final String DATA_PATH = "./test/crosslanguagefiles";

    public static void writeToFile(String path, byte[] data) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(path)) {
            stream.write(data);
        }
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
        try (FileInputStream stream = new FileInputStream(path)) {
            byte[] data = new byte[stream.available()];
            int read = stream.read(data);
            if (read != data.length) {
                throw new IOException(String.format("Truncated read (expected %d bytes, read %d)", data.length, read));
            }
            return data;
        }
    }

}
