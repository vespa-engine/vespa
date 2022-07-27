// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class PostingListSearchTest {

    @Test
    void require_that_search_find_index_of_first_element_higher() {
        int[] values = {2, 8, 4000, 4001, 4100, 10000, 10000000};
        int length = values.length;
        assertEquals(0, PostingListSearch.interpolationSearch(values, 0, length, 1));
        for (int value = 3; value < 8; value++) {
            assertEquals(1, PostingListSearch.interpolationSearch(values, 0, length, value));
        }
        assertEquals(2, PostingListSearch.interpolationSearch(values, 0, length, 8));
        assertEquals(values.length, PostingListSearch.interpolationSearch(values, 0, length, 10000000));
        assertEquals(values.length, PostingListSearch.interpolationSearch(values, 0, length, 10000001));
    }

    @Test
    void require_that_search_is_correct_for_one_size_arrays() {
        int[] values = {100};
        assertEquals(0, PostingListSearch.interpolationSearch(values, 0, 1, 0));
        assertEquals(0, PostingListSearch.interpolationSearch(values, 0, 1, 99));
        assertEquals(1, PostingListSearch.interpolationSearch(values, 0, 1, 100));
        assertEquals(1, PostingListSearch.interpolationSearch(values, 0, 1, 101));
        assertEquals(1, PostingListSearch.interpolationSearch(values, 0, 1, 10000));
    }

    @Test
    void require_that_search_is_correct_for_sub_arrays() {
        int[] values = {0, 2, 8, 4000, 4001, 4100};
        assertEquals(1, PostingListSearch.interpolationSearch(values, 1, 2, 1));
        assertEquals(2, PostingListSearch.interpolationSearch(values, 1, 2, 2));
        assertEquals(2, PostingListSearch.interpolationSearch(values, 1, 4, 2));
        assertEquals(4, PostingListSearch.interpolationSearch(values, 1, 4, 4000));
        assertEquals(5, PostingListSearch.interpolationSearch(values, 1, 5, 4001));
        assertEquals(5, PostingListSearch.interpolationSearch(values, 1, 5, 4101));
    }

    @Test
    void require_that_search_is_correct_for_large_arrays() {
        int length = 10000;
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = 2 * i;
        }
        assertEquals(1, PostingListSearch.interpolationSearch(values, 1, length, 0));
        assertEquals(1227, PostingListSearch.interpolationSearch(values, 1, length, 2452));
        assertEquals(1227, PostingListSearch.interpolationSearch(values, 1, length, 2453));
        assertEquals(1228, PostingListSearch.interpolationSearch(values, 1, length, 2454));
    }
}
