// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.processing.IllegalInputException;

/**
 * Wrapper class to avoid code duplication of common parsing requirements.
 *
 * @author Steinar Knutsen
 */
public class ParameterParser {

    /**
     * Tries to return the given object as a Long. If it is a Number, treat it
     * as a number of seconds, i.e. get a Long representation and multiply by
     * 1000. If it has a String representation, try to parse this as a floating
     * point number, followed by by an optional unit (seconds and an SI prefix,
     * a couple of valid examples are "s" and "ms". Only a very small subset of
     * SI prefixes are supported). If no unit is given, seconds are assumed.
     *
     * @param value some representation of a number of seconds
     * @param defaultValue returned if value is null
     * @return value as a number of milliseconds
     * @throws NumberFormatException if value is not a Number instance and its String
     *         representation cannot be parsed as a number followed optionally by time unit
     */
    public static Long asMilliSeconds(Object value, Long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number)value).longValue() * 1000L;
        return parseTime(value.toString());
    }

    private static Long parseTime(String time) throws NumberFormatException {
        time = time.trim();
        try {
            int unitOffset = findUnitOffset(time);
            double measure = Double.valueOf(time.substring(0, unitOffset));
            double multiplier = parseUnit(time.substring(unitOffset));
            return (long) (measure * multiplier);
        } catch (RuntimeException e) {
            throw new IllegalInputException("Error parsing '" + time + "'", e);
        }
    }

    private static int findUnitOffset(String time) {
        int unitOffset = 0;
        while (unitOffset < time.length()) {
            char c = time.charAt(unitOffset);
            if (c == '.' || (c >= '0' && c <= '9')) {
                unitOffset += 1;
            } else {
                break;
            }
        }
        if (unitOffset == 0) {
            throw new IllegalInputException("Invalid number '" + time + "'");
        }
        return unitOffset;
    }

    private static double parseUnit(String unit) {
        unit = unit.trim();
        final double multiplier;
        if ("ks".equals(unit)) {
            multiplier = 1e6d;
        } else if ("s".equals(unit)) {
            multiplier = 1000.0d;
        } else if ("ms".equals(unit)) {
            multiplier = 1.0d;
        } else if ("\u00B5s".equals(unit)) {
            // microseconds
            multiplier = 1e-3d;
        } else {
            multiplier = 1000.0d;
        }
        return multiplier;
    }

}
