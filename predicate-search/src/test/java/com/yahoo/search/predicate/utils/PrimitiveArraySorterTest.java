// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class PrimitiveArraySorterTest {

    @Test
    void sorting_empty_array_should_not_throw_exception() {
        short[] array = {};
        PrimitiveArraySorter.sort(array, Short::compare);
    }

    @Test
    void test_sorting_single_item_array() {
        short[] array = {42};
        PrimitiveArraySorter.sort(array, Short::compare);
        assertEquals(42, array[0]);
    }

    @Test
    void test_sorting_custom_comparator() {
        short[] array = {4, 2, 5};
        PrimitiveArraySorter.sort(array, (a, b) -> Short.compare(b, a)); // Sort using inverse ordering.
        short[] expected = {5, 4, 2};
        assertArrayEquals(expected, array);
    }

    @Test
    void test_complicated_array() {
        short[] array = {20381, -28785, -19398, 17307, -12612, 11459, -30164, -16597, -4267, 30838, 8918, 9014, -26444,
                -1232, -14620, 12636, -12389, -4931, 32108, 19854, -12681, 14933, 319, 27348, -4907, 19196, 14209,
                -32694, 2579, 9771, -1157, -13717, 28506, -8016, 21423, 23697, 23755, 29650, 25644, -14660, -18952,
                25272, -19933, -11375, -32363, -11766, -29509, -23898, 12398, -2600, -20703, -23812, -8292, -1605,
                28642, 12748, 2547, -14535, 4476, -7802};
        short[] expected = Arrays.copyOf(array, array.length);
        Arrays.sort(expected);
        PrimitiveArraySorter.sort(array, Short::compare);
        assertArrayEquals(expected, array);
    }

    @Test
    void sorting_random_arrays_should_produce_identical_result_as_java_sort() {
        Random r = new Random(4234);
        for (int i = 0; i < 10000; i++) {
            short[] original = makeRandomArray(r);
            short[] javaSorted = Arrays.copyOf(original, original.length);
            short[] customSorted = Arrays.copyOf(original, original.length);
            PrimitiveArraySorter.sort(customSorted, Short::compare);
            Arrays.sort(javaSorted);
            String errorMsg = String.format("%s != %s (before sorting: %s)", Arrays.toString(customSorted), Arrays.toString(javaSorted), Arrays.toString(original));
            assertArrayEquals(customSorted, javaSorted, errorMsg);
        }
    }

    @Test
    void test_merging_simple_array() {
        short[] array = {-20, -12, 2, -22, -11, 33, 44};
        short[] expected = {-22, -20, -12, -11, 2, 33, 44};
        short[] result = new short[array.length];
        PrimitiveArraySorter.merge(array, result, 3, Short::compare);
        assertArrayEquals(expected, result);
    }

    @Test
    void test_merging_of_random_generated_arrays() {
        Random r = new Random(4234);
        for (int i = 0; i < 10000; i++) {
            short[] array = makeRandomArray(r);
            int length = array.length;
            short[] mergeArray = new short[length];
            short[] expected = Arrays.copyOf(array, length);
            Arrays.sort(expected);

            int pivot = length > 0 ? r.nextInt(length) : 0;
            Arrays.sort(array, 0, pivot);
            Arrays.sort(array, pivot, length);
            PrimitiveArraySorter.merge(array, mergeArray, pivot, Short::compare);
            assertArrayEquals(expected, mergeArray);
        }
    }

    @Test
    void test_sortandmerge_returns_false_when_sort_is_in_place() {
        short[] array = {3, 2, 1, 0, 4, 5, 6};
        short[] mergeArray = new short[array.length];
        assertFalse(PrimitiveArraySorter.sortAndMerge(array, mergeArray, 4, 7, Short::compare));
        assertIsSorted(array);

        array = new short[]{3, 2, 1, 0, 4, 5, 6};
        assertTrue(PrimitiveArraySorter.sortAndMerge(array, mergeArray, 3, 7, Short::compare));
        assertIsSorted(mergeArray);
    }

    // Create random array with size [0, 99] filled with random values.
    private short[] makeRandomArray(Random r) {
        short[] array = new short[r.nextInt(100)];
        for (int j = 0; j < array.length; j++) {
            array[j] = (short) r.nextInt();
        }
        return array;
    }

    private static void assertIsSorted(short[] array) {
        if (array.length == 0) return;
        int prev = array[0];
        for (int i = 1; i < array.length; i++) {
            int next = array[i];
            assertTrue(prev <= next);
            prev = next;
        }
    }

}
