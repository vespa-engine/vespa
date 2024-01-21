// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

/**
 * Utility to make common conversions safe
 *
 * @author baldersheim
 */
public class Convert {
    public static int safe2Int(long value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IndexOutOfBoundsException("value = " + value + ", which is too large to fit in an int");
        }
        return (int) value;
    }
}
