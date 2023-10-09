// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.lang;

/**
 * A mutable boolean
 *
 * @author bratseth
 */
public class MutableBoolean {

    private boolean value;

    public MutableBoolean(boolean value) {
        this.value = value;
    }

    public boolean get() { return value; }

    public void set(boolean value) { this.value = value; }

    public void andSet(boolean value) { this.value &= value; }

    public void orSet(boolean value) { this.value |= value; }

    public boolean getAndSet(boolean newValue) {
        boolean prev = value;
        value = newValue;
        return prev;
    }

    @Override
    public String toString() { return Boolean.toString(value); }

}
