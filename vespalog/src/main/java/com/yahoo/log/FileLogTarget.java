// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 * Should only be used internally in the log library
 */
class FileLogTarget implements LogTarget {
    private final File file;
    private FileOutputStream fileOutputStream;

    public FileLogTarget(File target) throws FileNotFoundException {
        this.file = target;
        this.fileOutputStream = null;
    }

    public synchronized OutputStream open() {
        try {
            close();
            fileOutputStream = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to open output stream", e);
        }
        return fileOutputStream;
    }

    public synchronized void close() {
        try {
            if (fileOutputStream != null) {
                fileOutputStream.close();
                fileOutputStream = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to close output stream", e);
        }
    }

    @Override
    public String toString() {
        return file.getAbsolutePath();
    }
}
