// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

import com.yahoo.tensor.Tensor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A label is a value of a mapped dimension of a tensor.
 * This class provides a mapping of labels to numbers which allow for more efficient computation with
 * mapped tensor dimensions.
 *
 * @author baldersheim
 */
public class Label {

    private static final String[] SMALL_INDEXES = createSmallIndexesAsStrings(1000);

    private final static Map<String, Integer> string2Enum = new ConcurrentHashMap<>();

    // Index 0 is unused, that is a valid positive number
    // 1(-1) is reserved for the Tensor.INVALID_INDEX
    private static volatile String[] uniqueStrings = {"UNIQUE_UNUSED_MAGIC", "Tensor.INVALID_INDEX"};
    private static int numUniqeStrings = 2;

    private static String[] createSmallIndexesAsStrings(int count) {
        String[] asStrings = new String[count];
        for (int i = 0; i < count; i++) {
            asStrings[i] = String.valueOf(i);
        }
        return asStrings;
    }

    private static int addNewUniqueString(String s) {
        synchronized (string2Enum) {
            if (numUniqeStrings >= uniqueStrings.length) {
                uniqueStrings = Arrays.copyOf(uniqueStrings, uniqueStrings.length*2);
            }
            uniqueStrings[numUniqeStrings] = s;
            return -numUniqeStrings++;
        }
    }

    private static String asNumericString(long index) {
        return ((index >= 0) && (index < SMALL_INDEXES.length)) ? SMALL_INDEXES[(int)index] : String.valueOf(index);
    }

    private static boolean validNumericIndex(String s) {
        if (s.isEmpty() || ((s.length() > 1) && (s.charAt(0) == '0'))) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0') || (c > '9')) return false;
        }
        return true;
    }

    public static int toNumber(String s) {
        if (s == null) { return Tensor.invalidIndex; }
        try {
            if (validNumericIndex(s)) {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
        }
        return string2Enum.computeIfAbsent(s, Label::addNewUniqueString);
    }

    public static String fromNumber(int v) {
        if (v >= 0) {
            return asNumericString(v);
        } else {
            if (v == Tensor.invalidIndex) { return null; }
            return uniqueStrings[-v];
        }
    }

    public static String fromNumber(long v) {
        return fromNumber(Convert.safe2Int(v));
    }

}
