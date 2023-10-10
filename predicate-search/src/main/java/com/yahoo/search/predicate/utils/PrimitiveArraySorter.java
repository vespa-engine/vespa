// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

/**
 * This class enables sorting of an array of primitive short values using a supplied comparator for custom ordering.
 * The sort methods in Java standard library cannot sort using a comparator for primitive arrays.
 * Sorting is performed using Quicksort.
 *
 * @author bjorncs
 */
public class PrimitiveArraySorter {

    @FunctionalInterface
    public interface ShortComparator {
        int compare(short l, short r);
    }

    private PrimitiveArraySorter() {}

    public static void sort(short[] array, ShortComparator comparator) {
        sort(array, 0, array.length, comparator);
    }

    public static void sort(short[] array, int fromIndex, int toIndex, ShortComparator comparator) {
        // Sort using insertion sort for size less then 20.
        if (toIndex - fromIndex <= 20) {
            insertionSort(array, fromIndex, toIndex, comparator);
            return;
        }
        int i = fromIndex;
        int j = toIndex - 1;
        short pivotValue = array[i + (j - i) / 2]; // Use middle item as pivot value.
        while (i < j) {
            while (comparator.compare(pivotValue, array[i]) > 0) ++i;
            while (comparator.compare(array[j], pivotValue) > 0) --j;
            if (i < j) {
                short temp = array[i];
                array[i] = array[j];
                array[j] = temp;
                ++i;
                --j;
            }
        }
        if (fromIndex < j) {
            sort(array, fromIndex, j + 1, comparator);
        }
        if (i < toIndex - 1) {
            sort(array, i, toIndex, comparator);
        }
    }

    public static boolean sortAndMerge(short[] array, short[] mergeArray, int pivotIndex, int toIndex, ShortComparator comparator) {
        if (array.length == 1) return false;
        sort(array, 0, pivotIndex, comparator);
        if (pivotIndex == toIndex || comparator.compare(array[pivotIndex - 1], array[pivotIndex]) <= 0) {
            return false;
        }
        merge(array, mergeArray, pivotIndex, toIndex, comparator);
        return true;
    }

    public static void merge(short[] array, short[] mergeArray, int pivotIndex, ShortComparator comparator) {
        merge(array, mergeArray, pivotIndex, array.length, comparator);
    }

    public static void merge(short[] array, short[] mergeArray, int pivotIndex, int toIndex, ShortComparator comparator) {
        int indexMergeArray = 0;
        int indexPartition0 = 0;
        int indexPartition1 = pivotIndex;
        while (indexPartition0 < pivotIndex && indexPartition1 < toIndex) {
            short val0 = array[indexPartition0];
            short val1 = array[indexPartition1];
            if (comparator.compare(val0, val1) <= 0) {
                mergeArray[indexMergeArray++] = val0;
                ++indexPartition0;
            } else {
                mergeArray[indexMergeArray++] = val1;
                ++indexPartition1;
            }
        }
        int nLeftPartition0 = pivotIndex - indexPartition0;
        System.arraycopy(array, indexPartition0, mergeArray, indexMergeArray, nLeftPartition0);
        System.arraycopy(array, indexPartition1, mergeArray, indexMergeArray + nLeftPartition0, toIndex - indexPartition1);
    }

    private static void insertionSort(short[] array, int fromIndex, int toIndex, ShortComparator comparator) {
        for (int i = fromIndex + 1; i < toIndex; ++i) {
            int j = i;
            while (j > 0 && comparator.compare(array[j - 1], array[j]) > 0) {
                short temp = array[j - 1];
                array[j - 1] = array[j];
                array[j] = temp;
                --j;
            }
        }
    }

}
