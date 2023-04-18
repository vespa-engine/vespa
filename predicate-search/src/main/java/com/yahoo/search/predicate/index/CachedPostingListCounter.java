// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.google.common.collect.MinMaxPriorityQueue;
import org.eclipse.collections.api.tuple.primitive.ObjectLongPair;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Counts the number of posting lists per document id.
 * Caches the most expensive posting list in a bit vector.
 *
 * @author bjorncs
 */
public class CachedPostingListCounter {

    // Only use bit vector for counting if the documents covered is more than the threshold (relative to nDocuments)
    private static final double THRESHOLD_USE_BIT_VECTOR = 1;

    private final int nDocuments;
    private final ObjectLongHashMap<int[]> frequency = new ObjectLongHashMap<>();
    private final ObjectIntHashMap<int[]> postingListMapping;
    private final int[] bitVector;

    public CachedPostingListCounter(int nDocuments) {
        this.nDocuments = nDocuments;
        this.postingListMapping = new ObjectIntHashMap<>();
        this.bitVector = new int[0];
    }

    private CachedPostingListCounter(ObjectIntHashMap<int[]> postingListMapping, int[] bitVector) {
        this.nDocuments = bitVector.length;
        this.postingListMapping = postingListMapping;
        this.bitVector = bitVector;
    }

    public synchronized void registerUsage(List<PostingList> postingLists) {
        for (PostingList postingList : postingLists) {
            frequency.updateValue(postingList.getDocIds(), 0, v -> v + 1);
        }
    }

    public void countPostingListsPerDocument(List<PostingList> postingLists, byte[] nPostingListsForDocument) {
        Arrays.fill(nPostingListsForDocument, (byte) 0);
        List<int[]> nonCachedPostingLists = new ArrayList<>(postingLists.size());
        List<int[]> cachedPostingLists = new ArrayList<>(postingLists.size());
        long nDocumentsCachedPostingLists = 0;
        int postingListBitmap = 0;
        for (PostingList postingList : postingLists) {
            int[] docIds = postingList.getDocIds();
            int index = postingListMapping.getIfAbsent(docIds, -1);
            if (index >= 0) {
                cachedPostingLists.add(docIds);
                postingListBitmap |= (1 << index);
                nDocumentsCachedPostingLists += docIds.length;
            } else {
                nonCachedPostingLists.add(docIds);
            }
        }
        if (postingListBitmap != 0) {
            if (nDocumentsCachedPostingLists > nDocuments * THRESHOLD_USE_BIT_VECTOR) {
                countUsingBitVector(nPostingListsForDocument, postingListBitmap);
            } else {
                nonCachedPostingLists.addAll(cachedPostingLists);
            }
        }
        if (!nonCachedPostingLists.isEmpty()) {
            countUsingDocIdIteration(nPostingListsForDocument, nonCachedPostingLists);
        }
    }

    private void countUsingBitVector(byte[] nPostingListsForDocument, int postingListBitmap) {
        for (int docId = 0; docId < nDocuments; docId++) {
            nPostingListsForDocument[docId] += (byte)Integer.bitCount(bitVector[docId] & postingListBitmap);
        }
    }

    private static void countUsingDocIdIteration(byte[] nPostingListsForDocument, List<int[]> nonCachedPostingLists) {
        for (int[] docIds : nonCachedPostingLists) {
            for (int docId : docIds) {
                ++nPostingListsForDocument[docId];
            }
        }
    }

    public CachedPostingListCounter rebuildCache() {
        MinMaxPriorityQueue<Entry> mostExpensive = MinMaxPriorityQueue.maximumSize(32).expectedSize(32).create();
        synchronized (this) {
            for (ObjectLongPair<int[]> p : frequency.keyValuesView()) {
                mostExpensive.add(new Entry(p.getOne(), p.getTwo()));
            }
        }
        ObjectIntHashMap<int[]> postingListMapping = new ObjectIntHashMap<>();
        int[] bitVector = new int[nDocuments];
        int length = mostExpensive.size();
        for (int i = 0; i < length; i++) {
            Entry e = mostExpensive.removeFirst();
            int[] docIds = e.docIds;
            postingListMapping.put(docIds, i);
            for (int docId : docIds) {
                bitVector[docId] |= (1 << i);
            }
        }
        return new CachedPostingListCounter(postingListMapping, bitVector);
    }

    int[] getBitVector() {
        return bitVector;
    }

    ObjectIntHashMap<int[]> getPostingListMapping() {
        return postingListMapping;
    }

    private static class Entry implements Comparable<Entry> {
        public final int[] docIds;
        final double cost;

        private Entry(int[] docIds, long frequency) {
            this.docIds = docIds;
            this.cost = docIds.length * (double) frequency;
            assert cost > 0;
        }

        @Override
        public int compareTo(Entry o) {
            return -Double.compare(cost, o.cost);
        }
    }
}
