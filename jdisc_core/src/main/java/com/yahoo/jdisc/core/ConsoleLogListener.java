// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.net.HostName;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;

import java.io.PrintStream;
import java.util.Optional;

/**
 * @author Vikas Panwar
 */
class ConsoleLogListener implements LogListener {

    public static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.TRACE;
    private final ConsoleLogFormatter formatter;
    private final PrintStream out;
    private final LogLevel maxLevel;

    ConsoleLogListener(PrintStream out, String serviceName, String logLevel) {
        this.out = out;
        this.formatter = new ConsoleLogFormatter(getHostname(), getProcessId(), serviceName);
        this.maxLevel = parseLogLevel(logLevel).orElse(null);
    }

    @Override
    public void logged(LogEntry entry) {
        if (maxLevel == null || !maxLevel.implies(entry.getLogLevel())) {
            return;
        }
        out.println(formatter.formatEntry(entry));
    }

    public static Optional<LogLevel> parseLogLevel(String logLevel) {
        if (logLevel == null || logLevel.isEmpty()) {
            return Optional.of(DEFAULT_LOG_LEVEL);
        }
        if (logLevel.equalsIgnoreCase("OFF")) {
            return Optional.empty();
        }
        if (logLevel.equalsIgnoreCase("ERROR")) {
            return Optional.of(LogLevel.ERROR);
        }
        if (logLevel.equalsIgnoreCase("WARNING")) {
            return Optional.of(LogLevel.WARN);
        }
        if (logLevel.equalsIgnoreCase("INFO")) {
            return Optional.of(LogLevel.INFO);
        }
        if (logLevel.equalsIgnoreCase("DEBUG")) {
            return Optional.of(LogLevel.DEBUG);
        }
        if (logLevel.equalsIgnoreCase("ALL")) {
            return Optional.of(LogLevel.TRACE);
        }
        try {
            return Optional.of(LogLevel.values()[Integer.parseInt(logLevel)]);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // fall through
        }
        return Optional.of(DEFAULT_LOG_LEVEL);
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
