// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.lang;

/**
 * A mutable long
 *
 * @author bratseth
 */
public class MutableLong {

    private long value;

    public MutableLong(long value) {
        this.value = value;
    }

    public long get() { return value; }

    public void set(long value) { this.value = value; }

    /** Adds the increment to the current value and returns the resulting value */
    public long add(long increment) {
        value += increment;
        return value;
    }

    /** Adds the increment to the current value and returns the resulting value */
    public long subtract(long increment) {
        value -= increment;
        return value;
    }

    @Override
    public String toString() { return Long.toString(value); }

}
