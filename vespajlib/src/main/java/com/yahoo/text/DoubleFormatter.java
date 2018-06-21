// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import com.google.common.annotations.Beta;

/**
 * Utility class to format a double into a String.
 * <p>
 * This was intended as a lower-cost replacement for the standard
 * String.valueOf(double) since that method used to cause contention.
 * <p>
 * The contention issue is fixed in Java 8u96 so this class is now obsolete.
 *
 * @author arnej27959
 */

@Beta
public final class DoubleFormatter {

    public static StringBuilder fmt(StringBuilder target, double v) {
        target.append(v);
        return target;
    }

    public static String stringValue(double v) {
        return String.valueOf(v);
    }

    public static void append(StringBuilder s, double d) {
        s.append(d);
    }

    public static void append(StringBuilder s, int i) {
        s.append(i);
    }

}
