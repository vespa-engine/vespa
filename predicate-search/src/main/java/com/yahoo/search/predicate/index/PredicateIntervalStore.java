// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.google.common.primitives.Ints;
import com.yahoo.search.predicate.serialization.SerializationHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bjorncs
 */
public class PredicateIntervalStore {

    private final int[][] intervalsList;

    public PredicateIntervalStore(int[][] intervalsList) {
        this.intervalsList = intervalsList;
    }

    public int[] get(int intervalRef) {
        assert intervalRef < intervalsList.length;
        return intervalsList[intervalRef];
    }

    public void writeToOutputStream(DataOutputStream out) throws IOException {
        out.writeInt(intervalsList.length);
        for (int[] intervals : intervalsList) {
            SerializationHelper.writeIntArray(intervals, out);
        }
    }

    public static PredicateIntervalStore fromInputStream(DataInputStream in) throws IOException {
        int length = in.readInt();
        int[][] intervalsList = new int[length][];
        for (int i = 0; i < length; i++) {
            intervalsList[i] = SerializationHelper.readIntArray(in);
        }
        return new PredicateIntervalStore(intervalsList);
    }

    public static class Builder {
        private final List<int[]> intervalsListBuilder = new ArrayList<>();
        private final Map<Entry, Integer> intervalsListIndexes = new HashMap<>();
        private final Map<Integer, Integer> entriesForSize = new HashMap<>();
        private int cacheHits = 0;
        private int totalInserts = 0;

        public int insert(List<Integer> intervals) {
            int size = intervals.size();
            if (size == 0) {
                throw new IllegalArgumentException("Cannot insert interval list of size 0");
            }
            int[] array = Ints.toArray(intervals);
            Entry entry = new Entry(array);
            ++totalInserts;
            if (intervalsListIndexes.containsKey(entry)) {
                ++cacheHits;
                return intervalsListIndexes.get(entry);
            } else {
                int index = intervalsListBuilder.size();
                intervalsListBuilder.add(array);
                intervalsListIndexes.put(entry, index);
                entriesForSize.merge(size, 1, Integer::sum);
                return index;
            }
        }

        public PredicateIntervalStore build() {
            int nIntervals = intervalsListBuilder.size();
            int[][] intervalsList = new int[nIntervals][];
            for (int i = 0; i < nIntervals; i++) {
                intervalsList[i] = intervalsListBuilder.get(i);
            }
            return new PredicateIntervalStore(intervalsList);
        }

        public int getCacheHits() {
            return cacheHits;
        }

        public int getTotalInserts() {
            return totalInserts;
        }

        public Map<Integer, Integer> getEntriesForSize() {
            return entriesForSize;
        }

        public int getNumberOfIntervals() {
            return intervalsListBuilder.size();
        }

        private static class Entry {
            public final int[] intervals;
            public final int hashCode;

            public Entry(int[] intervals) {
                this.intervals = intervals;
                this.hashCode = Arrays.hashCode(intervals);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Entry entry = (Entry) o;
                return Arrays.equals(intervals, entry.intervals);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }
    }
}
