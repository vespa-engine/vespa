// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.google.common.primitives.Ints;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.search.predicate.serialization.SerializationTestHelper.assertSerializationDeserializationMatches;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
public class PredicateIntervalStoreTest {

    @Test
    void requireThatEmptyIntervalListThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
            builder.insert(new ArrayList<>());
        });
    }

    @Test
    void requireThatSingleIntervalCanBeInserted() {
        testInsertAndRetrieve(0x0001ffff);
    }

    @Test
    void requireThatMultiIntervalEntriesCanBeInserted() {
        testInsertAndRetrieve(0x00010001, 0x00020002, 0x0003ffff);
        testInsertAndRetrieve(0x00010001, 0x00020002, 0x00030003, 0x00040004, 0x00050005, 0x00060006,
                0x00070007, 0x00080008, 0x00090009, 0x000a000a);
    }

    @Test
    void requireThatDifferentSizeIntervalArraysCanBeInserted() {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        int intervals1[] = new int[]{0x00010001, 0x00020002};
        int intervals2[] = new int[]{0x00010001, 0x00020002, 0x00030003};
        assertEquals(0, builder.insert(Ints.asList(intervals1)));
        assertEquals(1, builder.insert(Ints.asList(intervals2)));
    }

    @Test
    void requireThatSerializationAndDeserializationRetainIntervals() throws IOException {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        builder.insert(Arrays.asList(0x00010001, 0x00020002));
        builder.insert(Arrays.asList(0x00010001, 0x00020002, 0x00030003));
        builder.insert(Arrays.asList(0x0fffffff, 0x00020002, 0x00030003));
        PredicateIntervalStore store = builder.build();
        assertSerializationDeserializationMatches(
                store, PredicateIntervalStore::writeToOutputStream, PredicateIntervalStore::fromInputStream);
    }

    @Test
    void requireThatEqualIntervalListsReturnsSameReference() {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        List<Integer> intervals1 = Arrays.asList(0x00010001, 0x00020002);
        List<Integer> intervals2 = Arrays.asList(0x00010001, 0x00020002);
        int ref1 = builder.insert(intervals1);
        int ref2 = builder.insert(intervals2);
        PredicateIntervalStore store = builder.build();
        int[] a1 = store.get(ref1);
        int[] a2 = store.get(ref2);
        assertEquals(a1, a2);
    }

    private static void testInsertAndRetrieve(int... intervals) {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        int ref = builder.insert(Ints.asList(intervals));
        PredicateIntervalStore store = builder.build();

        int retrieved[] = store.get(ref);
        assertArrayEquals(intervals, retrieved);
    }

}
