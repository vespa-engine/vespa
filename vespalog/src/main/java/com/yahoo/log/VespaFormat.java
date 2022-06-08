// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.impl.LogUtils;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vespa log formatting utility methods.
 * Contains some code based on LogUtils.java in Cloudname https://github.com/Cloudname/cloudname
 * written by Bjørn Borud, licensed under the Apache 2.0 license.
 *
 * @author arnej27959
 * @author bjorncs
 *
 * Should only be used internally in the log library
 */
class VespaFormat {

    private static final Pattern special   = Pattern.compile("[\r\\\n\\\t\\\\]+");
    private static final Pattern newLine   = Pattern.compile("\n");
    private static final Pattern carriage  = Pattern.compile("\r");
    private static final Pattern tab       = Pattern.compile("\t");
    private static final Pattern backSlash = Pattern.compile("\\\\");

    private static final String hostname;
    private static final String processID;

    static {
        hostname = LogUtils.getHostName();
        processID = LogUtils.getPID();
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
     * @return returns the escaped string
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


    public static String formatTime(Instant instant) {
        return String.format("%d.%06d", instant.getEpochSecond(), instant.getNano() / 1000);
    }

    static String formatThreadProcess(long processId, long threadId) {
        if (threadId == 0) {
            return Long.toString(processId);
        }
        return processId + "/" + threadId;
    }

}
