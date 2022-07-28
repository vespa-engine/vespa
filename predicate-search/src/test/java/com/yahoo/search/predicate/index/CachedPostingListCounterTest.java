// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.google.common.primitives.Ints;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class CachedPostingListCounterTest {

    @Test
    void require_that_docids_are_counted_correctly() {
        int nDocuments = 4;
        byte[] nPostingListsPerDocument = new byte[nDocuments];
        CachedPostingListCounter c = new CachedPostingListCounter(nDocuments);
        c.countPostingListsPerDocument(
                list(
                        postingList(0, 1, 2, 3),
                        postingList(1, 2),
                        postingList(1, 3),
                        postingList(3)),
                nPostingListsPerDocument);
        assertArrayEquals(new byte[]{1, 3, 2, 3}, nPostingListsPerDocument);
    }

    @Test
    void require_that_most_costly_posting_lists_are_first_in_bit_vector() {
        int nDocuments = 5;
        CachedPostingListCounter c = new CachedPostingListCounter(nDocuments);
        List<PostingList> list = new ArrayList<>();
        PostingList p1 = postingList(1, 2, 4);
        PostingList p2 = postingList(0, 1, 2, 3, 4);
        PostingList p3 = postingList(1, 2, 3, 4);
        PostingList p4 = postingList(3, 4);
        list.add(p1);
        list.add(p2);
        list.add(p3);
        list.add(p4);
        for (int i = 0; i < 100; i++) {
            list.add(postingList(0));
        }
        c.registerUsage(list);
        CachedPostingListCounter newC = c.rebuildCache();
        ObjectIntHashMap<int[]> mapping = newC.getPostingListMapping();
        assertEquals(0, mapping.getIfAbsent(p2.getDocIds(), -1));
        assertEquals(1, mapping.getIfAbsent(p3.getDocIds(), -1));
        assertEquals(2, mapping.getIfAbsent(p1.getDocIds(), -1));
        assertEquals(3, mapping.getIfAbsent(p4.getDocIds(), -1));

        int[] bitVector = newC.getBitVector();
        assertEquals(0b0001, bitVector[0] & 0b1111);
        assertEquals(0b0111, bitVector[1] & 0b1111);
        assertEquals(0b0111, bitVector[2] & 0b1111);
        assertEquals(0b1011, bitVector[3] & 0b1111);
        assertEquals(0b1111, bitVector[4] & 0b1111);
    }

    @Test
    void require_that_cached_docids_are_counted_correctly() {
        int nDocuments = 4;
        byte[] nPostingListsPerDocument = new byte[nDocuments];
        CachedPostingListCounter c = new CachedPostingListCounter(nDocuments);
        PostingList p1 = postingList(0, 1, 2, 3);
        PostingList p2 = postingList(1, 2);
        PostingList p3 = postingList(1, 3);
        PostingList p4 = postingList(3);
        List<PostingList> postingLists = list(p1, p2, p3, p4);
        c.registerUsage(postingLists);
        CachedPostingListCounter newC = c.rebuildCache();
        newC.countPostingListsPerDocument(postingLists, nPostingListsPerDocument);
        assertArrayEquals(new byte[]{1, 3, 2, 3}, nPostingListsPerDocument);
        newC.countPostingListsPerDocument(list(p1, p2), nPostingListsPerDocument);
        assertArrayEquals(new byte[]{1, 2, 2, 1}, nPostingListsPerDocument);
    }

    @Test
    void require_that_cache_rebuilding_behaves_correctly_for_large_amount_of_posting_lists() {
        int nDocuments = 4;
        byte[] nPostingListsPerDocument = new byte[nDocuments];
        CachedPostingListCounter c = new CachedPostingListCounter(nDocuments);
        List<PostingList> postingLists = new ArrayList<>(100 * nDocuments);
        for (int i = 0; i < 100 * nDocuments; i++) {
            postingLists.add(postingList(i % nDocuments));
        }
        c.registerUsage(postingLists);
        CachedPostingListCounter newC = c.rebuildCache();
        newC.countPostingListsPerDocument(postingLists, nPostingListsPerDocument);
        assertArrayEquals(new byte[]{100, 100, 100, 100}, nPostingListsPerDocument);

        List<PostingList> doc0PostingLists = new ArrayList<>();
        for (int i = 0; i < 100 * nDocuments; i += nDocuments) {
            doc0PostingLists.add(postingLists.get(i));
        }
        newC.countPostingListsPerDocument(doc0PostingLists, nPostingListsPerDocument);
        assertArrayEquals(new byte[]{100, 0, 0, 0}, nPostingListsPerDocument);
    }

    private static List<PostingList> list(PostingList... postingLists) {
        return Arrays.asList(postingLists);
    }

    private static PostingList postingList(Integer... docIds) {
        PostingList postingList = mock(PostingList.class);
        when(postingList.getDocIds()).thenReturn(Ints.toArray(Arrays.asList((docIds))));
        return postingList;
    }

}
