// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


/**
 * Produces compact output format for prelude logs
 *
 * @author Bob Travis
 */
public class LogFormatter extends Formatter {

    /** date format objects */
    static SimpleDateFormat ddMMMyyyy;
    static DateFormat dfMMM;
    static SimpleDateFormat yyyyMMdd;

    static {
        ddMMMyyyy = new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]", Locale.US);
        ddMMMyyyy.setTimeZone(TimeZone.getTimeZone("UTC"));

        dfMMM = new SimpleDateFormat("MMM", Locale.US);
        dfMMM.setTimeZone(TimeZone.getTimeZone("UTC"));

        yyyyMMdd = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]", Locale.US);
        yyyyMMdd.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /** Whether to strip down the message to only the message or not */
    private boolean messageOnly = false;

    /** Controls which of the available timestamp formats is used in all log records
     */
    private static final int timestampFormat = 2; // 0=millis, 1=mm/dd/yyyy, 2=yyyy-mm-dd

    /**
     * Standard constructor
     */

    public LogFormatter() {}

    /**
     * Make it possible to log stripped messages
     */
    public void messageOnly (boolean messageOnly) {
        this.messageOnly = messageOnly;
    }

    public String format(LogRecord record) {

        // if we don't want any other stuff we just return the message
        if (messageOnly) {
            return formatMessage(record);
        }

        String rawMsg = record.getMessage();
        boolean isLogMsg =
            rawMsg.charAt(0) == 'L'
            && rawMsg.charAt(1) == 'O'
            && rawMsg.charAt(2) == 'G'
            && rawMsg.charAt(3) == ':';
        String nameInsert =
            (!isLogMsg)
            ? record.getLevel().getName() + ": "
            : "";
        return (timeStamp(record)
                + nameInsert
                + formatMessage(record)
                + "\n"
                );
    }

    /**
     * Public support methods
     */

    /**
     * Static insertDate method will insert date fragments into a string
     * based on '%x' pattern elements.  Equivalents in SimpleDateFormatter patterns,
     * with examples:
     * <ul>
     * <li>%Y  YYYY  2003
     * <li>%m  MM  08
     * <li>%x  MMM  Aug
     * <li>%d  dd  25
     * <li>%H  HH  14
     * <li>%M  mm  30
     * <li>%S  ss  35
     * <li>%s  SSS  123
     * <li>%Z  Z -0400
     * </ul>
     *Others:
     * <ul>
     * <li>%T Long.toString(time)
     * <li>%%  %
     * </ul>
     */
    public static String insertDate(String pattern, long time) {
        DateFormat df = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss.SSS Z", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date(time);
        String datetime = df.format(date);
        StringBuilder result = new StringBuilder();
        int i=0;
        while (i < pattern.length()) {
            int j = pattern.indexOf('%',i);
            if (j == -1 || j >= pattern.length()-1) { // done
                result.append(pattern.substring(i)); // copy rest of pattern and quit
                break;
            }
            result.append(pattern.substring(i, j));
            switch (pattern.charAt(j+1)) {
            case 'Y':
                result.append(datetime.substring(0,4)); // year
                break;
            case 'm':
                result.append(datetime.substring(5,7)); // month
                break;
            case 'd':
                result.append(datetime.substring(8,10)); // day of month
                break;
            case 'H':
                result.append(datetime.substring(11,13)); // hour
                break;
            case 'M':
                result.append(datetime.substring(14,16)); // minute
                break;
            case 'S':
                result.append(datetime.substring(17,19)); // second
                break;
            case 's':
                result.append(datetime.substring(20,23)); // thousanths
                break;
            case 'Z':
                result.append(datetime.substring(24)); // time zone string
                break;
            case 'T':
                result.append(Long.toString(time)); //time in Millis
                break;
            case 'x':
                result.append(capitalize(dfMMM.format(date)));
                break;
            case '%':
                result.append("%%");
                break;
            default:
                result.append("%"); // copy pattern escape and move on
                j--;                // only want to bump by one position....
                break;
            }
            i = j+2;
        }

        return result.toString();
    }

    /**
     * Private methods: timeStamp(LogRecord)
     */
    private String timeStamp(LogRecord record) {
        Date date = new Date(record.getMillis());
        String stamp;
        switch (timestampFormat) {
        case 0:
            stamp = Long.toString(record.getMillis());
            break;
        case 1:
             stamp = ddMMMyyyy.format(date);
             break;
        case 2:
        default:
            stamp = yyyyMMdd.format(date);
            break;
        }
        return stamp;
    }

    /** Return the given string with the first letter in upper case */
    private static String capitalize(String string) {
        if (Character.isUpperCase(string.charAt(0))) return string;
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

}
