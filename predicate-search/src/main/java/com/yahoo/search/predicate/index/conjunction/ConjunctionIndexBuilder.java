// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A builder for {@link ConjunctionIndex}.
 *
 * @author bjorncs
 */
public class ConjunctionIndexBuilder {

    // A map from K value to FeatureIndex
    private final HashMap<Integer, FeatureIndexBuilder> kIndexBuilder = new HashMap<>();
    private final List<Integer> zListBuilder = new ArrayList<>();
    // Unique ids / mapping from internal to external id. LinkedHashSet as the insertion order is crucial.
    private final Set<Long> seenIds = new LinkedHashSet<>();
    private int idCounter = 0;
    private int conjunctionsSeen = 0;

    private static class FeatureIndexBuilder {
        // Maps a feature id to conjunction id
        private final Map<Long, Set<Integer>> map = new HashMap<>();

        public void insert(long featureId, int conjunctionId) {
            map.computeIfAbsent(featureId, k -> new TreeSet<>()).add(conjunctionId);
        }
    }

    public void indexConjunction(IndexableFeatureConjunction c) {
        ++conjunctionsSeen;
        long externalId = c.id;
        if (seenIds.contains(externalId)) return;

        seenIds.add(externalId);
        int internalId = generateInternalId();
        FeatureIndexBuilder featureIndexBuilder = kIndexBuilder.computeIfAbsent(c.k, (k) -> new FeatureIndexBuilder());
        c.features.forEach(f -> featureIndexBuilder.insert(f, internalId));
        c.negatedFeatures.forEach(f -> featureIndexBuilder.insert(f, internalId & ~1));
        if (c.k == 0) {
            zListBuilder.add(internalId);
        }
    }

    private int generateInternalId() {
        return ((idCounter++) << 1) | 1;
    }

    public ConjunctionIndex build() {
        int[] zList = Ints.toArray(zListBuilder);
        IntObjectMap<ConjunctionIndex.FeatureIndex> kIndex = buildKIndex(kIndexBuilder);
        long[] idMapping = Longs.toArray(seenIds);
        return new ConjunctionIndex(kIndex, zList, idMapping);
    }

    /**
     * @return The number of unique features in index.
     */
    public long calculateFeatureCount() {
        return kIndexBuilder.values().stream()
                .map(index -> index.map.keySet())
                .reduce(
                        new HashSet<>(),
                        (acc, keySet) -> {
                            keySet.forEach(acc::add);
                            return acc;
                        }, (acc1, acc2) -> {
                            acc1.addAll(acc2);
                            return acc1;
                        })
                .size();
    }

    /**
     * @return The number of unique conjunctions indexed.
     */
    public long getUniqueConjunctionCount() {
        return seenIds.size();
    }

    public int getZListSize() {
        return zListBuilder.size();
    }

    public int getConjunctionsSeen() {
        return conjunctionsSeen;
    }

    private static IntObjectMap<ConjunctionIndex.FeatureIndex> buildKIndex(HashMap<Integer, FeatureIndexBuilder> kIndexBuilder) {
        IntObjectHashMap<ConjunctionIndex.FeatureIndex> map = new IntObjectHashMap<>();
        for (Map.Entry<Integer, FeatureIndexBuilder> entry : kIndexBuilder.entrySet()) {
            map.put(entry.getKey(), buildFeatureIndex(entry.getValue()));
        }
        map.compact();
        return map;
    }

    private static ConjunctionIndex.FeatureIndex buildFeatureIndex(FeatureIndexBuilder featureIndexBuilder) {
        LongObjectHashMap<int[]> map = new LongObjectHashMap<>();
        for (Map.Entry<Long, Set<Integer>> featureEntry : featureIndexBuilder.map.entrySet()) {
            int[] conjunctionIds = Ints.toArray(featureEntry.getValue());
            map.put(featureEntry.getKey(), conjunctionIds);
        }
        map.compact();
        return new ConjunctionIndex.FeatureIndex(map);
    }

}
