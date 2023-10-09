// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.search.predicate.PredicateQuery;
import com.yahoo.search.predicate.SubqueryBitmap;
import com.yahoo.search.predicate.serialization.SerializationHelper;
import com.yahoo.search.predicate.utils.PrimitiveArraySorter;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.api.tuple.primitive.LongObjectPair;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A searchable index of conjunctions (see {@link FeatureConjunction} / {@link IndexableFeatureConjunction}).
 * Implements the algorithm described in the paper
 * <a href="http://dl.acm.org/citation.cfm?id=1687633">Indexing Boolean Expressions</a>.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class ConjunctionIndex {

    // A map from K value to FeatureIndex
    private final IntObjectMap<FeatureIndex> kIndex;
    private final int[] zList;
    private final long[] idMapping;

    public ConjunctionIndex(IntObjectMap<FeatureIndex> kIndex, int[] zList, long[] idMapping) {
        this.kIndex = kIndex;
        this.zList = zList;
        this.idMapping = idMapping;
    }

    public Searcher searcher() {
        return new Searcher();
    }

    public void writeToOutputStream(DataOutputStream out) throws IOException {
        SerializationHelper.writeIntArray(zList, out);
        SerializationHelper.writeLongArray(idMapping, out);
        out.writeInt(kIndex.size());
        for (IntObjectPair<FeatureIndex> p : kIndex.keyValuesView()) {
            out.writeInt(p.getOne());
            p.getTwo().writeToOutputStream(out);
        }
    }

    public static ConjunctionIndex fromInputStream(DataInputStream in) throws IOException {
        int[] zList = SerializationHelper.readIntArray(in);
        long[] idMapping = SerializationHelper.readLongArray(in);
        int kIndexSize = in.readInt();
        IntObjectHashMap<FeatureIndex> kIndex = new IntObjectHashMap<>(kIndexSize);
        for (int i = 0; i < kIndexSize; i++) {
            int key = in.readInt();
            kIndex.put(key, FeatureIndex.fromInputStream(in));
        }
        kIndex.compact();
        return new ConjunctionIndex(kIndex, zList, idMapping);
    }

    public static class FeatureIndex {
        // Maps a feature id to conjunction id
        private final LongObjectMap<int[]> map;

        public FeatureIndex(LongObjectMap<int[]> map) {
            this.map = map;
        }

        public Optional<int[]> getConjunctionIdsForFeature(long featureId) {
            return Optional.ofNullable(map.get(featureId));
        }

        public void writeToOutputStream(DataOutputStream out) throws IOException {
            out.writeInt(map.size());
            for (LongObjectPair<int[]> p : map.keyValuesView()) {
                out.writeLong(p.getOne());
                SerializationHelper.writeIntArray(p.getTwo(), out);
            }
        }

        public static FeatureIndex fromInputStream(DataInputStream in) throws IOException {
            int mapSize = in.readInt();
            LongObjectHashMap<int[]> map = new LongObjectHashMap<>(mapSize);
            for (int i = 0; i < mapSize; i++) {
                long key = in.readLong();
                map.put(key, SerializationHelper.readIntArray(in));
            }
            map.compact();
            return new FeatureIndex(map);
        }
    }

    public class Searcher {
        private final byte[] iteratorsPerConjunction;

        private Searcher() {
            this.iteratorsPerConjunction = new byte[idMapping.length];
        }

        /**
         * Retrieves a list of hits for the given query.
         *
         * @param query Specifies the boolean variables that are true.
         * @return List of hits
         */
        public List<ConjunctionHit> search(PredicateQuery query) {
            List<ConjunctionHit> conjunctionHits = new ArrayList<>();
            int uniqueKeys = (int) query.getFeatures().stream().map(e -> e.key).distinct().count();
            for (int k = uniqueKeys; k >= 0; k--) {
                List<ConjunctionIdIterator> iterators = new ArrayList<>();
                getFeatureIndex(k)
                        .ifPresent(featureIndex -> addFeatureIterators(query, featureIndex, iterators));
                if (k == 0 && zList.length > 0) {
                    iterators.add(new ConjunctionIdIterator(SubqueryBitmap.ALL_SUBQUERIES, zList));
                }
                if (!iterators.isEmpty()) {
                    calculateIteratorsPerConjunction(iterators);
                    findMatchingConjunctions(k, iterators, conjunctionHits, iteratorsPerConjunction);
                }
            }
            return conjunctionHits;
        }

        private void calculateIteratorsPerConjunction(List<ConjunctionIdIterator> iterators) {
            Arrays.fill(iteratorsPerConjunction, (byte)0);
            for (ConjunctionIdIterator iterator : iterators) {
                for (int id : iterator.getConjunctionIds()) {
                    if (ConjunctionId.isPositive(id)) {
                        ++iteratorsPerConjunction[id >>> 1];
                    }
                }
            }
        }

        private Optional<FeatureIndex> getFeatureIndex(int k) {
            return Optional.ofNullable(kIndex.get(k));
        }

        private void addFeatureIterators(PredicateQuery query, FeatureIndex featureIndex, List<ConjunctionIdIterator> iterators) {
            query.getFeatures().stream()
                    .map(e -> toSingleTermIterator(e, featureIndex))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(iterators::add);
        }

        private Optional<ConjunctionIdIterator> toSingleTermIterator(PredicateQuery.Feature feature, FeatureIndex featureIndex) {
            return featureIndex.getConjunctionIdsForFeature(feature.featureHash)
                    .map(conjunctions -> new ConjunctionIdIterator(feature.subqueryBitmap, conjunctions));
        }

        private void findMatchingConjunctions(int k, List<ConjunctionIdIterator> iterators, List<ConjunctionHit> matchingIds, byte[] iteratorsPerConjunction) {
            if (k == 0) {
                k = 1;
            }
            int nextId = getNextId(0, k, iteratorsPerConjunction);
            if (nextId == -1) {
                return; // no hits
            }

            int nIterators = iterators.size();
            if (nIterators < k) {
                return; // No hits
            }
            short[] sortedIndexes = new short[nIterators];
            short[] sortedIndexesMergeBuffer = new short[nIterators];
            for (short i = 0; i < nIterators; ++i) {
                sortedIndexes[i] = i;
            }

            int[] currentIds = new int[nIterators];
            int nCompleted = initializeIterators(iterators, sortedIndexes, currentIds, nextId);
            nIterators -= nCompleted;

            while (nIterators >= k) {
                int id0 = currentIds[sortedIndexes[0]];
                int idK = currentIds[sortedIndexes[k - 1]];

                // There should be at least k iterators for conjunction.
                if (ConjunctionId.equals(id0, idK)) {
                    long matchingSubqueries = SubqueryBitmap.ALL_SUBQUERIES;
                    // Find first positive conjunction
                    int firstPositive = 0;
                    while (firstPositive < nIterators && !ConjunctionId.isPositive(currentIds[sortedIndexes[firstPositive]])) {
                        // AND in the complement of the bitmap for negative conjunctions.
                        matchingSubqueries &= ~iterators.get(sortedIndexes[firstPositive]).getSubqueryBitmap();
                        ++firstPositive;
                    }
                    if (firstPositive + k <= nIterators) {
                        // Verify that at there are k positive iterators for the current conjunction.
                        id0 = currentIds[sortedIndexes[firstPositive]];
                        idK = currentIds[sortedIndexes[firstPositive + k - 1]];
                        if (id0 == idK) { // We know that id0 is positive conjunction
                            for (int i = firstPositive; i < firstPositive + k; i++) {
                                matchingSubqueries &= iterators.get(sortedIndexes[i]).getSubqueryBitmap();
                            }
                            if (matchingSubqueries != 0) {
                                matchingIds.add(new ConjunctionHit(toExternalId(id0), matchingSubqueries));
                            }
                        }
                    }
                }

                // Advance iterators to next conjunction.
                nextId = getNextId(ConjunctionId.nextId(id0), k, iteratorsPerConjunction);
                if (nextId == -1) {
                    return;
                }
                int completed = 0;
                int i;
                for (i = 0; i < nIterators; ++i) {
                    short index = sortedIndexes[i];
                    if (ConjunctionId.compare(currentIds[index], nextId) < 0) {
                        ConjunctionIdIterator iterator = iterators.get(index);
                        if (iterator.next(nextId)) {
                            currentIds[index] = iterator.getConjunctionId();
                        } else {
                            currentIds[index] = Integer.MAX_VALUE;
                            ++completed;
                        }
                    } else {
                        break;
                    }
                }
                if (i > 0 && nIterators - completed >= k) {
                    boolean swapMergeBuffer =
                            PrimitiveArraySorter.sortAndMerge(sortedIndexes, sortedIndexesMergeBuffer, i, nIterators,
                                    (a, b) -> Integer.compare(currentIds[a], currentIds[b]));
                    if (swapMergeBuffer) {
                        short[] temp = sortedIndexes;
                        sortedIndexes = sortedIndexesMergeBuffer;
                        sortedIndexesMergeBuffer = temp;
                    }
                }
                nIterators -= completed;
            }
        }

        private int initializeIterators(List<ConjunctionIdIterator> iterators, short[] sortedIndexes, int[] currentIds, int nextId) {
            int nCompleted = 0;
            int nIterators = iterators.size();
            for (int i = 0; i < nIterators; i++) {
                ConjunctionIdIterator iterator = iterators.get(i);
                if (iterator.next(nextId)) {
                    currentIds[i] = iterator.getConjunctionId();
                } else {
                    currentIds[i] = Integer.MAX_VALUE;
                    ++nCompleted;

                }
            }
            PrimitiveArraySorter.sort(sortedIndexes, (a, b) -> Integer.compare(currentIds[a], currentIds[b]));
            return nCompleted;
        }

        private int getNextId(int fromId, int k, byte[] iteratorsPerConjunction) {
            int id = fromId >>> 1;
            int nDocuments = iteratorsPerConjunction.length;
            while (id < nDocuments && iteratorsPerConjunction[id] < k) {
                ++id;
            }
            return id == nDocuments ? -1 : ((id << 1) | 1);

        }

        private long toExternalId(int internalId) {
            return idMapping[internalId >>> 1];
        }
    }

}
