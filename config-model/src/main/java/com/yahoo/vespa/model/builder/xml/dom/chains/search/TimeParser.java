// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing timeout fields.
 *
 * @author tonytv
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TimeParser {
    private static final Pattern timeoutPattern = Pattern.compile("(\\d+(\\.\\d*)?)\\s*(m)?s");
    private static final double milliSecondsPerSecond = 1000.0d;

    public static Double seconds(String timeout) {
        Matcher matcher = timeoutPattern.matcher(timeout);
        if (!matcher.matches()) {
            throw new RuntimeException("Timeout pattern not in sync with schema");
        }

        double value = Double.parseDouble(matcher.group(1));
        if (matcher.group(3) != null) {
            value /= milliSecondsPerSecond;
        }
        return new Double(value);
    }

    public static int asMilliSeconds(String timeout) {
        Matcher matcher = timeoutPattern.matcher(timeout);
        if (!matcher.matches()) {
            throw new RuntimeException("Timeout pattern not in sync with schema");
        }

        double value = Double.parseDouble(matcher.group(1));
        if (matcher.group(3) == null) {
            value *= milliSecondsPerSecond;
        }

        return (int) value;
    }
}
