// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import java.util.Objects;

/**
 * Represents a number of bytes or possibly "max".
 *
 * @author hakonhall
 */
public class Size {
    private static final String MAX = "max";

    private final boolean max;
    private final long value;

    public static Size max() {
        return new Size(true, 0);
    }

    public static Size from(long value) {
        return new Size(false, value);
    }

    public static Size from(String value) {
        return value.equals(MAX) ? new Size(true, 0) : new Size(false, Long.parseLong(value));
    }

    private Size(boolean max, long value) {
        this.max = max;
        this.value = value;
    }

    public boolean isMax() {
        return max;
    }

    /** Returns the value, i.e. the number of "bytes" if applicable. Throws if this is max. */
    public long value() {
        if (max) throw new IllegalStateException("Value is max");
        return value;
    }

    public String toFileContent() { return toString() + '\n'; }

    @Override
    public String toString() { return max ? MAX : Long.toString(value); }

    public boolean isGreaterThan(Size that) {
        if (that.max) return false;
        if (this.max) return true;
        return this.value > that.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Size size = (Size) o;
        return max == size.max && value == size.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(max, value);
    }
}
