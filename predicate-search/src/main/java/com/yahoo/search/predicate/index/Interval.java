// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

/**
 * Utility class for interval related constants and methods.
 * An interval consists of a begin and end value indicating the start and end of the interval.
 * Both value are inclusive, eg (1,2) is an interval of size 2.
 *
 * There are 3 types of interval; normal, ZStar1 and ZStar2.
 *
 * Normal intervals have begin value in 16 MSB and end in 16 LSB.
 * ZStar1 intervals have end value in 16 MSB and begin in 16 LSB.
 * ZStar2 intervals have only an end value located at 16 LSB.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class Interval {

    public static final int INTERVAL_BEGIN = 0x01;
    public static final int MAX_INTERVAL_END = 0xffff;
    public static final int ZERO_CONSTRAINT_RANGE = 1;

    private Interval() {}

    public static int fromBoundaries(int begin, int end) {
        assert begin >= INTERVAL_BEGIN && begin <= MAX_INTERVAL_END
                && end >= INTERVAL_BEGIN && end <= MAX_INTERVAL_END : toString(begin, end);
        return (begin << 16) | end;
    }

    public static int fromZStar1Boundaries(int begin, int end) {
        assert begin >= 0 && begin <= MAX_INTERVAL_END
                && end >= INTERVAL_BEGIN && end <= MAX_INTERVAL_END : toString(end, begin);
        return (end << 16) | begin;
    }

    public static int fromZStar2Boundaries(int end) {
        assert end >= INTERVAL_BEGIN && end <= MAX_INTERVAL_END : toString(0, end);
        return end;
    }

    public static boolean isZStar1Interval(int interval) {
        return getBegin(interval) > getEnd(interval);
    }

    public static boolean isZStar2Interval(int interval) {
        return (interval & 0xffff0000) == 0;
    }

    public static int getBegin(int interval) {
        return interval >>> 16;
    }

    public static int getEnd(int interval) {
        return interval & 0xffff;
    }

    public static int getZStar1Begin(int interval) {
        return getEnd(interval);
    }

    public static int getZStar1End(int interval) {
        return getBegin(interval);
    }

    public static int getZStar2End(int interval) {
        return interval;
    }

    /**
     * @return A new ZStar1 interval with boundaries [end(zStar1)+1, end(zStar2)]
     */
    public static int combineZStarIntervals(int zStar1, int zStar2) {
        return zStar1 >>> 16 | zStar2 << 16;
    }

    private static String toString(int begin, int end) {
        if (begin == 0) {
            return String.format("[%d]**", end);
        } else if (begin > end) {
            return String.format("[%d, %d]*", begin, end);
        }
        return String.format("[%d, %d]", begin, end);
    }

}
