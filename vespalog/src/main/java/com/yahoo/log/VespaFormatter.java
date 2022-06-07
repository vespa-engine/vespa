// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$
package com.yahoo.log;

import com.yahoo.log.event.Event;
import com.yahoo.log.impl.LogUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class implements a log formatter which takes care of
 * formatting messages according to the VESPA common log format.
 *
 * @author  Bjorn Borud
 * @author arnej27959
 *
 */
class VespaFormatter extends SimpleFormatter {

    private static final Pattern backSlash = Pattern.compile("\\\\");

    // other way around
    private static final Pattern backSlashN = Pattern.compile("\\\\n");
    private static final Pattern backSlashT = Pattern.compile("\\\\t");
    private static final Pattern backSlash2 = Pattern.compile("\\\\\\\\");

    private static final String hostname;
    private static final String processID;

    public static final String serviceNameUnsetValue = "-";

    static {
        hostname = LogUtils.getHostName();
        processID = LogUtils.getPID();
    }

    private String serviceName;
    private final String componentPrefix;

    /**
     * Default constructor
     */
    public VespaFormatter() {
        this.serviceName = serviceNameUnsetValue;
        this.componentPrefix = null;
    }

    /**
     * @param serviceName The VESPA service name.
     * @param componentPrefix The application name.
     */
    public VespaFormatter(String serviceName, String componentPrefix) {
        if (serviceName == null) {
            this.serviceName = serviceNameUnsetValue;
        } else {
            this.serviceName = serviceName;
        }
        this.componentPrefix = componentPrefix;
    }

    /**
     * Un-escapes previously escaped string.
     * note: look at com.yahoo.config.StringNode.unescapeQuotedString()
     *
     * @param s String that might need un-escaping
     * @return Returns un-escaped string
     */
    public static String unEscape(String s) {
        Matcher m = backSlash.matcher(s);
        if (! m.find()) {
            return s;
        }
        m = backSlashN.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\n");
        }
        m = backSlashT.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\t");
        }
        m = backSlash2.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\\\\");
        }
        return s;
    }

    @SuppressWarnings("deprecation")
    public String format(LogRecord r) {
        StringBuilder sbuf = new StringBuilder(300); // initial guess

        String levelName = LogLevel.getVespaLogLevel(r.getLevel()).toString().toLowerCase();

        String component = r.getLoggerName();

        // format the time
        sbuf.append(VespaFormat.formatTime(r.getInstant()));
        sbuf.append("\t");

        sbuf.append(hostname).append("\t")
            .append(processID).append("/")
            .append(r.getThreadID()).append("\t")
            .append(serviceName).append("\t");

        if (component == null && componentPrefix == null) {
            sbuf.append("-");
        } else if (component == null) {
            sbuf.append(componentPrefix);
        } else if (componentPrefix == null) {
            sbuf.append(".").append(component);
        } else {
            sbuf.append(componentPrefix).append(".").append(component);
        }

        sbuf.append("\t").append(levelName).append("\t");

        // for events, there is no ordinary message string;
        // instead we render a string represantion of the event object:
        if (r.getLevel() == LogLevel.EVENT) {
            Event event = (Event) r.getParameters()[0];
            sbuf.append(VespaFormat.escape(event.toString()));
        } else {
            // otherwise, run standard java text formatting on the message
            sbuf.append(VespaFormat.escape(formatMessage(r)));
        }
        appendException(r.getThrown(), sbuf);

        sbuf.append("\n");
        return sbuf.toString();
    }

    private void appendException(Throwable throwable, StringBuilder builder) {
        if (throwable == null)
            return;

        String escapedStackTrace = VespaFormat.escape(stackTrace(throwable));
        builder.append("\\n").append("exception=").append("\\n").append(escapedStackTrace);
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        PrintWriter wrappedWriter = new PrintWriter(writer);
        throwable.printStackTrace(wrappedWriter);
        wrappedWriter.close();
        return writer.toString();
    }


    /**
     * Set the service name (usually the VESPA config-id) of this
     * formatter.
     *
     * @param serviceName The service name
     */
    public void setServiceName (String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Get the service name for this formatter.
     *
     * @return Returns the service name.
     */
    public String getServiceName () {
        return serviceName;
    }


    public static String toMessageString(Throwable t) {
        StringBuilder b = new StringBuilder();
        String lastMessage = null;
        String message;
        for (; t != null; t = t.getCause()) {
            message = getMessage(t);
            if (message == null) continue;
            if (message.equals(lastMessage)) continue;
            if (b.length() > 0) {
                b.append(": ");
            }
            b.append(message);
            lastMessage = message;
        }
        return b.toString();
    }

    /** Returns a useful message from *this* exception, or null if there is nothing useful to return */
    private static String getMessage(Throwable t) {
        String message = t.getMessage();
        if (t.getCause() == null) {
            if (message == null) return t.getClass().getSimpleName();
        }
        return message;
    }


}
