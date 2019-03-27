// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vespa log formatting utility methods.
 * Contains some code based on Util.java in Cloudname https://github.com/Cloudname/cloudname
 * written by Bjørn Borud, licensed under the Apache 2.0 license.
 * 
 * @author arnej27959
 */
public class VespaFormat {

    private static final Pattern special   = Pattern.compile("[\r\\\n\\\t\\\\]+");
    private static final Pattern newLine   = Pattern.compile("\n");
    private static final Pattern carriage  = Pattern.compile("\r");
    private static final Pattern tab       = Pattern.compile("\t");
    private static final Pattern backSlash = Pattern.compile("\\\\");

    private static final String hostname;
    private static final String processID;

    static {
        hostname = Util.getHostName();
        processID = Util.getPID();
    }

    /**
     * This static method is used to detect if a message needs
     * to be escaped, and if so, performs the escaping.  Since the
     * common case is most likely that escaping is <em>not</em>
     * needed, the code is optimized for this case.  The forbidden
     * characters are:
     *
     * <UL>
     *  <LI> newline
     *  <LI> tab
     *  <LI> backslash
     * </UL>
     *
     * <P>
     * Also handles the case where the message is <code>null</code>
     * and replaces the null message with a tag saying that the
     * value was "(empty)".
     *
     * @param s String that might need escaping
     * @return Returns escaped string
     *
     */
    public static String escape (String s) {
        if (s == null) {
            return "(empty)";
        }

        Matcher m = special.matcher(s);
        if (! m.find()) {
            return s;
        }

        // invariant: we had special characters

        m = backSlash.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\\\\\\\\");
        }

        m = newLine.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\\\\n");
        }

        m = carriage.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\\\\r");
        }

        m = tab.matcher(s);
        if (m.find()) {
            s = m.replaceAll("\\\\t");
        }

        return s;
    }


    /**
     * It is easier to slice and dice strings in Java than formatting
     * numbers...
     */
    public static void formatTime (long time, StringBuilder sbuffer) {
        String timeString = Long.toString(time);
        int len = timeString.length();

        // something wrong.  handle it by just returning the input
        // long as a string.  we prefer this to just crashing in
        // the substring handling.
        if (len < 3) {
            sbuffer.append(timeString);
            return;
        }
        sbuffer.append(timeString.substring(0, len - 3));
        sbuffer.append('.');
        sbuffer.append(timeString.substring(len - 3));
    }

    static String formatTime(Instant instant) {
        StringBuilder builder = new StringBuilder();
        VespaFormat.formatTime(instant.toEpochMilli(), builder);
        return builder.toString();
    }

    public static String format(String levelName,
                                String component,
                                String componentPrefix,
                                long millis,
                                String threadId,
                                String serviceName,
                                String formattedMessage,
                                Throwable t)
    {
        StringBuilder sbuf = new StringBuilder(300); // initial guess

        // format the time
        formatTime(millis, sbuf);
        sbuf.append("\t");

        sbuf.append(hostname).append("\t");

        sbuf.append(processID);
        if (threadId != null) {
            sbuf.append("/").append(threadId);
        }
        sbuf.append("\t");

        sbuf.append(serviceName).append("\t");

        if (component == null && componentPrefix == null) {
            sbuf.append("-");
        } else if (component == null) {
            sbuf.append(componentPrefix);
        } else if (componentPrefix == null) {
            sbuf.append(".").append(component);
        } else {
            sbuf.append(componentPrefix).append(".").append(component);
        }
        sbuf.append("\t");

        sbuf.append(levelName).append("\t");

        sbuf.append(escape(formattedMessage));
        if (t != null) {
            formatException(t, sbuf);
        }
        sbuf.append("\n");
        return sbuf.toString();
    }

    /**
     * Format throwable into given StringBuffer.
     *
     * @param t The Throwable we want to format
     * @param sbuf The stringbuffer into which we wish to
     *             format the Throwable
     */
    public static void formatException (Throwable t, StringBuilder sbuf) {
        Throwable last = t;
        int depth = 0;
        while (last != null) {
            sbuf.append("\\nmsg=\"");
            sbuf.append(escape(last.getMessage()));
            sbuf.append("\"\\nname=\"");
            sbuf.append(escape(last.getClass().getName()));
            sbuf.append("\"\\nstack=\"\\n");

            // loop through stack frames and format them
            StackTraceElement[] st = last.getStackTrace();
            int stopAt = Math.min(st.length, 15);
            boolean first = true;
            for (int i = 0; i < stopAt; i++) {
                if (first) {
                    first = false;
                } else {
                    sbuf.append("\\n");
                }
                sbuf.append(escape(st[i].toString()));
            }

            // tell the reader if we chopped off part of the stacktrace
            if (stopAt < st.length) {
                sbuf.append("\\n[...]");
            }
            sbuf.append("\\n\"");

            last = last.getCause();
            depth++;
        }
        sbuf.append(" nesting=").append(depth);
    }

    static String formatThreadProcess(long processId, long threadId) {
        if (threadId == 0) {
            return Long.toString(processId);
        }
        return processId + "/" + threadId;
    }

}
