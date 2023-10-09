// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import java.util.stream.Stream;

/**
 * Represents a collapsed leaf node in the fixed tree range representation.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class IntervalWithBounds {

    private int[] intervalBoundsArray;
    private int arrayIndex;

    public IntervalWithBounds() {
        setIntervalArray(null, 0);
    }
    public IntervalWithBounds(int interval, int bounds) {
        setIntervalArray(new int[] {interval, bounds}, 0);
    }

    public void setIntervalArray(int[] intervalBoundsArray, int arrayIndex) {
        this.intervalBoundsArray = intervalBoundsArray;
        this.arrayIndex = arrayIndex;
    }
    public boolean hasValue() { return arrayIndex < intervalBoundsArray.length - 1; }
    public void nextValue() { arrayIndex += 2; }

    public Stream<Integer> stream() { return Stream.of(getInterval(), getBounds()); }
    /**
     * 16 MSB represents interval begin, 16 LSB represents interval end.
     */
    public int getInterval() {
        return intervalBoundsArray[arrayIndex];
    }
    /*
     * 2 MSB determines mode for remaining 30 bits.
     *  10 => Greater or equal
     *  01 => Less than
     *  00 => 16 LSB > X >= 16 MSB
     */
    public int getBounds() {
        return intervalBoundsArray[arrayIndex + 1];
    }

    /**
     * Checks if a value is contained within the specified bounds.
     * @param value Value to check against
     * @return true if value is contained within the specified bounds
     */
    public boolean contains(int value) {
        int bounds = getBounds();
        if ((bounds & 0x80000000) != 0) {
            return value >= (bounds & 0x3fffffff);
        } else if ((bounds & 0x40000000) != 0) {
            return value < (bounds & 0x3fffffff);
        } else {
            return (value >= (bounds >> 16)) && (value < (bounds & 0xffff));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntervalWithBounds that = (IntervalWithBounds) o;
        return getInterval() == that.getInterval() && getBounds() == that.getBounds();
    }

    @Override
    public int hashCode() {
        return 31 * getInterval() + getBounds();
    }

    @Override
    public String toString() {
        return "IntervalWithBounds{" +
                "interval=" + getInterval() +
                ", bounds=" + getBounds() +
                '}';
    }

}
