// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;

/**
 * @author Simon Thoresen Hult
 */
class ConsoleLogFormatter {

    // The string used as a replacement for absent/null values.
    static final String ABSENCE_REPLACEMENT = "-";

    private final String hostName;
    private final String processId;
    private final String serviceName;

    public ConsoleLogFormatter(String hostName, String processId, String serviceName) {
        this.hostName = formatOptional(hostName);
        this.processId = formatOptional(processId);
        this.serviceName = formatOptional(serviceName);
    }

    public String formatEntry(LogEntry entry) {
        StringBuilder ret = new StringBuilder();
        formatTime(entry, ret).append('\t');
        formatHostName(ret).append('\t');
        formatProcessId(entry, ret).append('\t');
        formatServiceName(ret).append('\t');
        formatComponent(entry, ret).append('\t');
        formatLevel(entry, ret).append('\t');
        formatMessage(entry, ret);
        formatException(entry, ret);
        return ret.toString();
    }

    // TODO: The non-functional, side effect-laden coding style here is ugly and makes testing hard. See ticket 7128315.

    private StringBuilder formatTime(LogEntry entry, StringBuilder out) {
        Instant instant = Instant.ofEpochMilli(entry.getTime());
        out.append(String.format("%d.%06d", instant.getEpochSecond(), instant.getNano() / 1000));
        return out;
    }

    private StringBuilder formatHostName(StringBuilder out) {
        out.append(hostName);
        return out;
    }

    private StringBuilder formatProcessId(LogEntry entry, StringBuilder out) {
        out.append(processId);
        String threadId = getProperty(entry, "THREAD_ID");
        if (threadId != null) {
            out.append('/').append(threadId);
        }
        return out;
    }

    private StringBuilder formatServiceName(StringBuilder out) {
        out.append(serviceName);
        return out;
    }

    private StringBuilder formatComponent(LogEntry entry, StringBuilder out) {
        Bundle bundle = entry.getBundle();
        String loggerName = getProperty(entry, "LOGGER_NAME");
        if (bundle == null && loggerName == null) {
            out.append("-");
        } else {
            if (bundle != null) {
                out.append(bundle.getSymbolicName());
            }
            if (loggerName != null) {
                out.append('/').append(loggerName);
            }
        }
        return out;
    }

    private StringBuilder formatLevel(LogEntry entry, StringBuilder out) {
        switch (entry.getLogLevel()) {
        case ERROR:
            out.append("error");
            break;
        case WARN:
            out.append("warning");
            break;
        case INFO:
            out.append("info");
            break;
        case DEBUG:
            out.append("debug");
            break;
        default:
            out.append("unknown");
            break;
        }
        return out;
    }

    private StringBuilder formatMessage(LogEntry entry, StringBuilder out) {
        String msg = entry.getMessage();
        if (msg != null) {
            formatString(msg, out);
        }
        return out;
    }

    private StringBuilder formatException(LogEntry entry, StringBuilder out) {
        Throwable t = entry.getException();
        if (t != null) {
            if (entry.getLogLevel() == LogLevel.INFO) {
                out.append(": ");
                String msg = t.getMessage();
                if (msg != null) {
                    formatString(msg, out);
                } else {
                    out.append(t.getClass().getName());
                }
            } else {
                Writer buf = new StringWriter();
                t.printStackTrace(new PrintWriter(buf));
                formatString("\n" + buf, out);
            }
        }
        return out;
    }

    private static StringBuilder formatString(String str, StringBuilder out) {
        for (int i = 0, len = str.length(); i < len; ++i) {
            char c = str.charAt(i);
            switch (c) {
            case '\n':
                out.append("\\n");
                break;
            case '\r':
                out.append("\\r");
                break;
            case '\t':
                out.append("\\t");
                break;
            case '\\':
                out.append("\\\\");
                break;
            default:
                out.append(c);
                break;
            }
        }
        return out;
    }

    private static String getProperty(LogEntry entry, String name) {
        ServiceReference<?> ref = entry.getServiceReference();
        if (ref == null) {
            return null;
        }
        Object val = ref.getProperty(name);
        if (val == null) {
            return null;
        }
        return val.toString();
    }

    static String formatOptional(String str) {
        return formatOptional(str, ABSENCE_REPLACEMENT);
    }

    private static String formatOptional(final String str, final String replacementIfAbsent) {
        if (str == null) {
            return replacementIfAbsent;
        }
        final String result = str.trim();
        if (result.isEmpty()) {
            return replacementIfAbsent;
        }
        return result;
    }
}
