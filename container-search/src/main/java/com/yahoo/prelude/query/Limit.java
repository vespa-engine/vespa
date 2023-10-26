// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * An immutable numeric range limit which can be inclusive or exclusive
 *
 * @author bratseth
 */
public class Limit {

    public static final Limit NEGATIVE_INFINITY = new Limit(Double.NEGATIVE_INFINITY, false);
    public static final Limit POSITIVE_INFINITY = new Limit(Double.POSITIVE_INFINITY, false);

    private final Number number;
    private final boolean inclusive;
    private final boolean infinite;

    public Limit(Number number, boolean inclusive) {
        this.number = number;
        this.infinite = Double.isInfinite(number.doubleValue());
        this.inclusive = inclusive || infinite;
    }

    public Number number() { return number; }

    /** Returns true if this limit includes its number, false if it excludes it */
    public boolean isInclusive() { return inclusive; }

    String toRangeStart() {
        return (inclusive ? "[" : "<" ) + (isInfinite() ? "" : number.toString());
    }

    String toRangeEnd() {
        return (isInfinite() ? "" : number.toString()) + (inclusive ? "]" : ">" );
    }

    /** Returns the smaller of this and the given limit */
    public Limit min(Limit other) {
        return this.isSmallerOrEqualTo(other) ? this : other;
    }

    /** Returns the larger of this and the given limit */
    public Limit max(Limit other) {
        return this.isLargerOrEqualTo(other) ? this : other;
    }

    public boolean isSmallerOrEqualTo(Limit other) {
        double thisNumber = this.number().doubleValue();
        double otherNumber = other.number().doubleValue();
        if (thisNumber == otherNumber) {
            if ( ! other.isInclusive()) return false;
            return true;
        }
        return thisNumber < otherNumber;
    }

    public boolean isLargerOrEqualTo(Limit other) {
        double thisNumber = this.number().doubleValue();
        double otherNumber = other.number().doubleValue();
        if (thisNumber == otherNumber) {
            if ( ! other.isInclusive()) return false;
            return true;
        }
        return thisNumber > otherNumber;
    }

    public boolean isInfinite() { return infinite; }

    @Override
    public String toString() {
        return number + " (" +  (inclusive ? "inclusive" : "exclusive") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Limit)) return false;
        Limit other = (Limit)o;
        if (Boolean.compare(other.inclusive, this.inclusive) != 0) return false;
        return this.number.equals(other.number);
    }

    @Override
    public int hashCode() {
        return number.hashCode() + (inclusive ? 1 : 0);
    }

}
