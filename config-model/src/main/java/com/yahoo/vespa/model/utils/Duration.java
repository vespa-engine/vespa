// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a string on the form:
 *
 * [numbers]\s*[unit]?
 *
 * Where numbers is a double, and unit is one of:
 * d - days
 * m - minutes
 * s - seconds
 * ms - milliseconds
 *
 * Default is seconds.
 */
public class Duration {

    private static final Pattern pattern = Pattern.compile("([0-9\\.]+)\\s*([a-z]+)?");
    private static final Map<String, Integer> unitMultiplier = new HashMap<>();
    static {
        unitMultiplier.put("s", 1000);
        unitMultiplier.put("d", 1000 * 3600 * 24);
        unitMultiplier.put("ms", 1);
        unitMultiplier.put("m", 60 * 1000);
        unitMultiplier.put("h", 60 * 60 * 1000);
    }

    long value;

    public Duration(String value) {
        Matcher matcher = pattern.matcher(value);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Illegal duration format: " + value);
        }

        double num = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);

        if (unit != null) {
            Integer multiplier = unitMultiplier.get(unit);
            if (multiplier == null) {
                throw new IllegalArgumentException("Unknown time unit: " + unit + " in time value " + value);
            }

            num *= multiplier;
        } else {
            num *= 1000;
        }

        this.value = (long)num;
    }

    public double getSeconds() {
        return value / 1000.0;
    }

    public long getMilliSeconds() {
        return value;
    }

}

