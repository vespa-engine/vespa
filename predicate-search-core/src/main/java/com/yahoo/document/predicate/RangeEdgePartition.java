// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class RangeEdgePartition extends RangePartition {
    private final long value;
    private final int lowerBound;
    private final int upperBound;

    public RangeEdgePartition(String label, long value, int lower, int upper) {
        super(label);
        this.value = value;
        this.lowerBound = lower;
        this.upperBound = upper;
    }

    public long getValue() {
        return value;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    @Override
    public RangeEdgePartition clone() throws CloneNotSupportedException {
        return (RangeEdgePartition)super.clone();
    }

    @Override
    public int hashCode() {
        return super.hashCode()
                + Long.valueOf(value).hashCode()
                + Integer.valueOf(lowerBound).hashCode()
                + Integer.valueOf(upperBound).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof RangeEdgePartition)) {
            return false;
        }
        RangeEdgePartition other = (RangeEdgePartition)obj;
        return super.equals(other) &&
                value == other.value &&
                lowerBound == other.lowerBound &&
                upperBound == other.upperBound;
    }

    @Override
    protected void appendTo(StringBuilder out) {
        super.appendTo(out);

        out.append("+[");
        if (lowerBound > 0)
            out.append(lowerBound);
        out.append("..");
        if (upperBound >= 0)
            out.append(upperBound);
        out.append(']');
    }

    public long encodeBounds() {
        if (lowerBound > 0) {
            if (upperBound >= 0) {
                return lowerBound << 16 | (upperBound + 1);
            } else {
                return lowerBound | 0x80000000L;
            }
        } else {
            if (upperBound >= 0) {
                return (upperBound + 1) | 0x40000000;
            } else {
                return 0x80000000L;  // >0
            }
        }
    }

}
