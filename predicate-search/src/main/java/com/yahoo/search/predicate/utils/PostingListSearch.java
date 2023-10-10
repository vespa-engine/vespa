// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

/**
 * Algorithms for searching in the docId arrays in posting lists.
 *
 * @author bjorncs
 */
public class PostingListSearch {

    // Use linear search when size less than threshold
    public static final int LINEAR_SEARCH_THRESHOLD = 16;
    // Use linear search when value difference between first value and key is less than threshold
    public static final int LINEAR_SEARCH_THRESHOLD_2 = 32;
    // User binary search when size is less than threshold
    public static final int BINARY_SEARCH_THRESHOLD = 32768;

    public static int interpolationSearch(int[] a, int fromIndex, int toIndex, int key) {
        int low = fromIndex;
        int lowVal = a[low];
        if (key - lowVal < LINEAR_SEARCH_THRESHOLD_2) {
            return linearSearch(a, low, toIndex, key);
        }
        int high = toIndex - 1;
        int diff = high - low;
        if (diff <= BINARY_SEARCH_THRESHOLD) {
            return binarySearch(a, low, toIndex, key);
        }
        int highVal = a[high];
        do {
            if (key == lowVal) {
                return low + 1;
            }
            if (key >= highVal) {
                return high + 1;
            }
            int mean = (int) (diff * (long) (key - lowVal) / (highVal - lowVal));
            int eps = diff >>> 4;
            int lowMid = low + Math.max(0, mean - eps);
            int highMid = low + Math.min(diff, mean + eps);
            assert lowMid <= highMid;
            assert lowMid >= low;
            assert highMid <= high;

            if (a[lowMid] > key) {
                high = lowMid;
                highVal = a[lowMid];
            } else if (a[highMid] <= key) {
                low = highMid;
                lowVal = a[highMid];
            } else {
                low = lowMid;
                lowVal = a[lowMid];
                high = highMid;
                highVal = a[highMid];
            }
            assert low <= high;
            diff = high - low;
        } while (diff >= BINARY_SEARCH_THRESHOLD);
        return binarySearch(a, low, high + 1, key);
    }

    /**
     * Modified binary search:
     *  - Returns the first index where a[index] is larger then key
     */
    private static int binarySearch(int[] a, int fromIndex, int toIndex, int key) {
        assert fromIndex < toIndex;
        int low = fromIndex;
        int high = toIndex - 1;
        while (high - low > LINEAR_SEARCH_THRESHOLD) {
            int mid = (low + high) >>> 1;
            assert mid < high;
            if (a[mid] < key) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return linearSearch(a, low, high + 1, key);
    }

    private static int linearSearch(int[] a, int low, int high, int key) {
        assert low < high;
        while (low < high && a[low] <= key) {
            ++low;
        }
        return low;
    }

}
