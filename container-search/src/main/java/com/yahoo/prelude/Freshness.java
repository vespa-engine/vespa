// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.util.Calendar;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * The parameters for a freshness query (uses the datetime http parameter)
 * Parses the string part of the "datetime=&lt;string&gt;", converts it to seconds
 * since epoch and send that plus sets the flag in the QueryX packet that
 * enables freshnessboost in fsearch.
 * <p>
 * This is a value object
 *
 * @author Per G. Auran
 */
public class Freshness {

    private long refSecondsSinceEpoch = 0;   // reference time

    private void parse(String dateTime) {

        /** Convert dateTime string to seconds since epoch */
        if (dateTime.startsWith("now")) {

            /** Case 1: if string starts with now: special case read system time */
            refSecondsSinceEpoch = getSystemTimeInSecondsSinceEpoch();

            /** Case 2: now can be followed by -seconds for time offset */
            if (dateTime.startsWith("now-")) {
                // offset in seconds may be given
                String offsetStr = dateTime.substring(4);
                long timeOffset;
                if ( offsetStr.length() > 0) {
                    timeOffset = Long.parseLong(offsetStr);
                } else {
                    timeOffset = 1;
                }
                refSecondsSinceEpoch = refSecondsSinceEpoch - timeOffset;
            }
        } else { /** Case 3: Reftime explicitly given seconds since epoch */
            refSecondsSinceEpoch = Long.parseLong(dateTime);
        }
        //  Need to activate freshness in the QueryX packet if enabled: See QueryPacket.java
    }

    public Freshness(String dateTime) {
        parse(toLowerCase(dateTime)); // Set reference time
    }

    /** Calculates the current time since epoch in seconds */
    public long getSystemTimeInSecondsSinceEpoch() {
        long msSinceEpochNow = Calendar.getInstance().getTimeInMillis();
        return (msSinceEpochNow/1000);
    }

    /** Get the reference time as a long value (in seconds since epoch) */
    public long getRefTime() {return refSecondsSinceEpoch;}

    /** Set the reference time as a string value */
    @Override
    public String toString() {
        StringBuilder ser = new StringBuilder();
        /** convert long value to string */
        String dateTime = Long.toString(refSecondsSinceEpoch);
        ser.append(dateTime);
        return ser.toString().trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (! (other instanceof Freshness)) return false;
        return ((Freshness)other).refSecondsSinceEpoch == this.refSecondsSinceEpoch;
    }

    @Override
    public int hashCode() {
        return (int)refSecondsSinceEpoch;
    }

}
