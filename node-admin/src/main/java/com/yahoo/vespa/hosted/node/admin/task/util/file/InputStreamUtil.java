// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author hakonhall
 */
public class InputStreamUtil {
    private final InputStream inputStream;

    public InputStreamUtil(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * TODO: Replace usages with Java 9's InputStream::readAllBytes
     */
    byte[] readAllBytes() {
        // According to https://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
        // all other implementations are much inferior to this in performance.

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = uncheck(() -> inputStream.read(buffer))) != -1) {
            result.write(buffer, 0, length);
        }

        return result.toByteArray();
    }
}
