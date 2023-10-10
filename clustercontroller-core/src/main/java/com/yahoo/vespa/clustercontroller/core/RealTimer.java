// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Implementation of timer used when running for real.
 */
public class RealTimer implements Timer {

    public long getCurrentTimeInMillis() {
        return System.currentTimeMillis();
    }

    public static String printDuration(long time) {
        StringBuilder sb = new StringBuilder();
        if (time > 1000 * 60 * 60 * 24 * 2) {
            double days = time / (1000.0 * 60 * 60 * 24);
            sb.append(String.format(Locale.ENGLISH, "%.2f", days)).append(" day").append(Math.abs(days - 1.0) < 0.0001 ? "" : "s");
        } else if (time > 1000 * 60 * 60 * 2) {
            double hours = time / (1000.0 * 60 * 60);
            sb.append(String.format(Locale.ENGLISH, "%.2f", hours)).append(" hour").append(Math.abs(hours - 1.0) < 0.0001 ? "" : "s");
        } else if (time > 1000 * 60 * 2) {
            double minutes = time / (1000.0 * 60);
            sb.append(String.format(Locale.ENGLISH, "%.2f", minutes)).append(" minute").append(Math.abs(minutes - 1.0) < 0.0001 ? "" : "s");
        } else if (time > 1000 * 2) {
            double seconds = time / (1000.0);
            sb.append(String.format(Locale.ENGLISH, "%.2f", seconds)).append(" s");
        } else {
            sb.append(time).append(" ms");
        }
        return sb.toString();
    }

    public static String printDateNoMilliSeconds(long time, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(time);
        return String.format(Locale.ENGLISH, "%04d-%02d-%02d %02d:%02d:%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND));
    }

    public static String printDate(long time, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(time);
        return String.format(Locale.ENGLISH, "%04d-%02d-%02d %02d:%02d:%02d.%03d",
                             cal.get(Calendar.YEAR),
                             cal.get(Calendar.MONTH) + 1,
                             cal.get(Calendar.DAY_OF_MONTH),
                             cal.get(Calendar.HOUR_OF_DAY),
                             cal.get(Calendar.MINUTE),
                             cal.get(Calendar.SECOND),
                             cal.get(Calendar.MILLISECOND));
    }

}
