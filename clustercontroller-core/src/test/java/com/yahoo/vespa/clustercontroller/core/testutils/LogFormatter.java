// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

    private static boolean initialized = false;

    @Override
    public String format(LogRecord record) {
        return record.getMillis() + " " + record.getLevel() + " "
                + record.getLoggerName().substring(record.getLoggerName().lastIndexOf('.') + 1) + " " + record.getMessage() + "\n";
    }

    public synchronized static void initializeLogging() {
        if (initialized) return;
        initialized = true;
        try {
            File f = new File("src/test/resources/test.logging.properties");
            if (!f.exists()) {
                System.err.println("Test logging property file does not exist");
            }
            final InputStream inputStream = new FileInputStream(f);
            LogManager.getLogManager().readConfiguration(inputStream);
        } catch (Throwable t) {
            System.err.println("Failed to initialize logging");
            t.printStackTrace();
        }
    }

}
