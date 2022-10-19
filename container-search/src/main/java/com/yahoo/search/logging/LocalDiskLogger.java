// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

import java.io.FileWriter;
import java.io.IOException;

public class LocalDiskLogger extends AbstractThreadedLogger {

    private final String logFilePath;

    public LocalDiskLogger(LocalDiskLoggerConfig config) {
        logFilePath = config.path();
    }

    @Override
    public boolean transport(LoggerEntry entry) {
        String json = entry.serialize();
        try (FileWriter fw = new FileWriter(logFilePath, true)) {
            fw.write(json);
            fw.write(System.getProperty("line.separator"));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
