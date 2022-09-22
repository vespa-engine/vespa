// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

import java.io.FileWriter;
import java.io.IOException;

public class LocalDiskLogger extends AbstractThreadedLogger {

    private String logFilePath;

    public LocalDiskLogger(LocalDiskLoggerConfig config) {
        logFilePath = config.path();
    }

    @Override
    void transport(LoggerEntry entry) {
        String json = entry.toJson();
        try (FileWriter fw = new FileWriter(logFilePath, true)) {
            fw.write(json);
            fw.write(System.getProperty("line.separator"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
