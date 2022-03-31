// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.net.HostName;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;

import java.io.PrintStream;

/**
 * @author Vikas Panwar
 */
class ConsoleLogListener implements LogListener {

    public static final int DEFAULT_LOG_LEVEL = Integer.MAX_VALUE;
    private final ConsoleLogFormatter formatter;
    private final PrintStream out;
    private final int maxLevel;

    ConsoleLogListener(PrintStream out, String serviceName, String logLevel) {
        this.out = out;
        this.formatter = new ConsoleLogFormatter(getHostname(), getProcessId(), serviceName);
        this.maxLevel = parseLogLevel(logLevel);
    }

    @Override
    public void logged(LogEntry entry) {
        if (entry.getLevel() > maxLevel) {
            return;
        }
        out.println(formatter.formatEntry(entry));
    }

    public static int parseLogLevel(String logLevel) {
        if (logLevel == null || logLevel.isEmpty()) {
            return DEFAULT_LOG_LEVEL;
        }
        if (logLevel.equalsIgnoreCase("OFF")) {
            return Integer.MIN_VALUE;
        }
        if (logLevel.equalsIgnoreCase("ERROR")) {
            return 1;
        }
        if (logLevel.equalsIgnoreCase("WARNING")) {
            return 2;
        }
        if (logLevel.equalsIgnoreCase("INFO")) {
            return 3;
        }
        if (logLevel.equalsIgnoreCase("DEBUG")) {
            return 4;
        }
        if (logLevel.equalsIgnoreCase("ALL")) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.valueOf(logLevel);
        } catch (NumberFormatException e) {
            // fall through
        }
        return DEFAULT_LOG_LEVEL;
    }

    public static ConsoleLogListener newInstance() {
        return new ConsoleLogListener(System.out,
                                      System.getProperty("jdisc.logger.tag"),
                                      System.getProperty("jdisc.logger.level"));
    }

    static String getProcessId() {
        return Long.toString(ProcessHandle.current().pid());
    }

    static String getHostname() {
        return HostName.getLocalhost();
    }
}
