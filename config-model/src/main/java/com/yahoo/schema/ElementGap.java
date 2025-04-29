// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * The gap between positions in adjacent elements in multi-value fields.
 *
 * @author toregge
 */
public final class ElementGap {
    private final Integer gap;

    private ElementGap(Integer gap) { this.gap = gap; }
    public static ElementGap of(int gap) { return new ElementGap(gap); }
    public static ElementGap empty() { return new ElementGap(null); }
    public OptionalInt get() { return isFinite() ? OptionalInt.of(gap) : OptionalInt.empty(); }
    public final boolean isFinite() { return gap != null; }

    @Override
    public String toString() { return isFinite() ? String.valueOf(gap) : "infinity"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementGap that = (ElementGap) o;
        return gap != null ? gap.equals(that.gap) : that.gap == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gap);
    }
}
