// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.api.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.yahoo.document.predicate.BooleanPredicate;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.search.predicate.annotator.PredicateTreeAnnotations;
import com.yahoo.search.predicate.annotator.PredicateTreeAnnotator;
import com.yahoo.search.predicate.index.Feature;
import com.yahoo.search.predicate.index.Interval;
import com.yahoo.search.predicate.index.IntervalWithBounds;
import com.yahoo.search.predicate.index.Posting;
import com.yahoo.search.predicate.index.PredicateIntervalStore;
import com.yahoo.search.predicate.index.PredicateOptimizer;
import com.yahoo.search.predicate.index.SimpleIndex;
import com.yahoo.search.predicate.index.conjunction.ConjunctionIndexBuilder;
import com.yahoo.search.predicate.index.conjunction.IndexableFeatureConjunction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.stream.Collectors.joining;

/**
 * A builder for {@link PredicateIndex}.
 * <p>
 * When creating a PredicateIndexBuilder, you must specify an arity. This is used for
 * range features, and is a trade-off of index size vs. query speed. Higher
 * arities gives larger index but faster search.
 * </p>
 * <p>
 * {@link #indexDocument(int, Predicate)}
 * takes a document id and a predicate to insert into the index.
 * Predicates should be specified using the predicate syntax described in the documentation.
 * Create the {@link Predicate} objects using {@link Predicate#fromString(String)}.
 * </p>
 * <p>
 * Use {@link #build()} to create an instance of {@link PredicateIndex}.
 * </p>
 * @author bjorncs
 */
@Beta
public class PredicateIndexBuilder {

    // Unique ids / mapping from internal to external id. LinkedHashSet as the insertion order is crucial.
    private final Set<Integer> seenIds = new LinkedHashSet<>();
    private final List<Short> intervalEndsBuilder = new ArrayList<>();
    private final List<Byte> minFeatureIndexBuilder = new ArrayList<>();
    private final List<Integer> zeroConstraintDocuments = new ArrayList<>();
    private final SimpleIndex.Builder intervalIndexBuilder = new SimpleIndex.Builder();
    private final SimpleIndex.Builder boundsIndexBuilder = new SimpleIndex.Builder();
    private final SimpleIndex.Builder conjunctionIntervalIndexBuilder = new SimpleIndex.Builder();
    private final ConjunctionIndexBuilder conjunctionIndexBuilder = new ConjunctionIndexBuilder();
    private final PredicateIntervalStore.Builder intervalStoreBuilder;
    private final PredicateOptimizer optimizer;
    private final Config config;
    private int documentIdCounter = 0;
    private int nZStarDocuments = 0;
    private int nZStarIntervals = 0;
    private int highestIntervalEnd = 1;

    /**
     * Creates a PredicateIndexBuilder with default upper and lower bounds.
     *
     * @param arity the arity to use when indexing range predicates.
     *              Small arity gives smaller index, but more expensive searches.
     */
    public PredicateIndexBuilder(int arity) {
        this(new Config.Builder().setArity(arity).build());
    }

    /**
     * Creates a PredicateIndexBuilder.
     * Limiting the range of possible values in range predicates reduces index size
     * and increases search performance.
     *
     * @param arity      the arity to use when indexing range predicates.
     *                   Small arity gives smaller index, but more expensive searches.
     * @param lowerBound the lower bound for the range of values used by range predicates
     * @param upperBound the upper bound for the range of values used by range predicates
     */
    public PredicateIndexBuilder(int arity, long lowerBound, long upperBound) {
        this(new Config.Builder().setArity(arity).setLowerBound(lowerBound).setUpperBound(upperBound).build());
    }

    /**
     * Creates a PredicateIndexBuilder based on a Config object.
     *
     * @param config configuration for the PredicateIndexBuilder
     */
    public PredicateIndexBuilder(Config config) {
        this.config = config;
        this.optimizer = new PredicateOptimizer(config);
        this.intervalStoreBuilder = new PredicateIntervalStore.Builder();
    }

    /**
     * Indexes a predicate with the given id.
     *
     * @param docId     a 32-bit document id, returned in the Hit objects when the predicate matches
     * @param predicate the predicate to index
     */
    public void indexDocument(int docId, Predicate predicate) {
        if (documentIdCounter == Integer.MAX_VALUE) {
            throw new IllegalStateException("Index is full, max number of documents is: " + Integer.MAX_VALUE);
        } else if (seenIds.contains(docId)) {
            throw new IllegalArgumentException("Document id is already in use: " + docId);
        } else if (isNeverMatchingDocument(predicate)) {
            return;
        }
        seenIds.add(docId);
        predicate = optimizer.optimizePredicate(predicate);
        int internalId = documentIdCounter++;
        if (isAlwaysMatchingDocument(predicate)) {
            indexZeroConstraintDocument(internalId);
        } else {
            indexDocument(internalId, PredicateTreeAnnotator.createPredicateTreeAnnotations(predicate));
        }
    }

    private static boolean isAlwaysMatchingDocument(Predicate p) {
        return p instanceof BooleanPredicate && ((BooleanPredicate) p).getValue();
    }

    private static boolean isNeverMatchingDocument(Predicate p) {
        return p instanceof BooleanPredicate && !((BooleanPredicate) p).getValue();
    }

    private void indexZeroConstraintDocument(int docId) {
        minFeatureIndexBuilder.add((byte) 0);
        intervalEndsBuilder.add((short) Interval.ZERO_CONSTRAINT_RANGE);
        zeroConstraintDocuments.add(docId);
    }

    private void indexDocument(int docId, PredicateTreeAnnotations annotations) {
        int minFeature = annotations.minFeature;
        Preconditions.checkState(minFeature <= 0xFF,
                "Predicate is too complex. Expected min-feature less than %d, was %d.", 0xFF, minFeature);
        int intervalEnd = annotations.intervalEnd;
        Preconditions.checkState(intervalEnd <= Interval.MAX_INTERVAL_END,
                "Predicate is too complex. Expected min-feature less than %d, was %d.",
                Interval.MAX_INTERVAL_END, intervalEnd);
        highestIntervalEnd = Math.max(highestIntervalEnd, intervalEnd);
        intervalEndsBuilder.add((short) intervalEnd);
        minFeatureIndexBuilder.add((byte) minFeature);
        indexDocumentFeatures(docId, annotations.intervalMap);
        indexDocumentBoundsFeatures(docId, annotations.boundsMap);
        indexDocumentConjunctions(docId, annotations.featureConjunctions);
        aggregateZStarStatistics(annotations.intervalMap);
    }

    private void aggregateZStarStatistics(Map<Long, List<Integer>> intervalMap) {
        List<Integer> intervals = intervalMap.get(Feature.Z_STAR_COMPRESSED_ATTRIBUTE_HASH);
        if (intervals != null) {
            ++nZStarDocuments;
            nZStarIntervals += intervals.size();
        }
    }

    private void indexDocumentFeatures(int docId, Map<Long, List<Integer>> intervalMap) {
        intervalMap.entrySet().stream()
                .forEach(entry -> intervalIndexBuilder.insert(entry.getKey(),
                        new Posting(docId,
                                intervalStoreBuilder.insert(entry.getValue()))));
    }

    private void indexDocumentBoundsFeatures(int docId, Map<Long, List<IntervalWithBounds>> boundsMap) {
        boundsMap.entrySet().stream()
                .forEach(entry -> boundsIndexBuilder.insert(entry.getKey(),
                        new Posting(docId,
                                intervalStoreBuilder.insert(
                                        entry.getValue().stream().flatMap(IntervalWithBounds::stream).toList()))));
    }

    private void indexDocumentConjunctions(
            int docId, Map<IndexableFeatureConjunction, List<Integer>> featureConjunctions) {
        for (Map.Entry<IndexableFeatureConjunction, List<Integer>> e : featureConjunctions.entrySet()) {
            IndexableFeatureConjunction fc = e.getKey();
            List<Integer> intervals = e.getValue();
            Posting posting = new Posting(docId, intervalStoreBuilder.insert(intervals));
            conjunctionIntervalIndexBuilder.insert(fc.id, posting);
            conjunctionIndexBuilder.indexConjunction(fc);
        }
    }

    public PredicateIndex build() {
        return new PredicateIndex(
                config,
                Ints.toArray(seenIds),
                Bytes.toArray(minFeatureIndexBuilder),
                Shorts.toArray(intervalEndsBuilder),
                highestIntervalEnd,
                intervalIndexBuilder.build(),
                boundsIndexBuilder.build(),
                conjunctionIntervalIndexBuilder.build(),
                intervalStoreBuilder.build(),
                conjunctionIndexBuilder.build(),
                Ints.toArray(zeroConstraintDocuments)
        );
    }

    public int getZeroConstraintDocCount() {
        return zeroConstraintDocuments.size();
    }

    /**
     * Retrieves metrics about the current index.
     *
     * @return an object containing metrics
     */
    public PredicateIndexStats getStats() {
        return new PredicateIndexStats(zeroConstraintDocuments, intervalIndexBuilder,
                boundsIndexBuilder, intervalStoreBuilder, conjunctionIndexBuilder, nZStarDocuments, nZStarIntervals);
    }

    /**
     * A collection of metrics about the currently built {@link PredicateIndex}.
     */
    public static class PredicateIndexStats {
        private final Map<String, Object> metrics = new TreeMap<>();

        public PredicateIndexStats(
                List<Integer> zeroConstraintDocuments,
                SimpleIndex.Builder intervalIndex,
                SimpleIndex.Builder boundsIndex,
                PredicateIntervalStore.Builder intervalStore,
                ConjunctionIndexBuilder conjunctionIndex,
                int nZStarDocuments,
                int nZStarIntervals) {
            Map<Integer, Integer> intervalStoreEntries = intervalStore.getEntriesForSize();
            metrics.put("Zero-constraint documents", zeroConstraintDocuments.size());
            metrics.put("Interval index keys", intervalIndex.getKeyCount());
            metrics.put("Interval index entries", intervalIndex.getEntryCount());
            metrics.put("Bounds index keys", boundsIndex.getKeyCount());
            metrics.put("Bounds index entries", boundsIndex.getEntryCount());
            metrics.put("Conjunction index feature count", conjunctionIndex.calculateFeatureCount());
            metrics.put("Conjunction index unique conjunction count", conjunctionIndex.getUniqueConjunctionCount());
            metrics.put("Conjunction index conjunction count", conjunctionIndex.getConjunctionsSeen());
            metrics.put("Conjunction index Z list size", conjunctionIndex.getZListSize());
            metrics.put("Interval store cache hits", intervalStore.getCacheHits());
            metrics.put("Interval store insert count", intervalStore.getTotalInserts());
            metrics.put("Interval store interval count", intervalStore.getNumberOfIntervals());
            metrics.put("Documents with ZStar intervals", nZStarDocuments);
            metrics.put("Total ZStar intervals", nZStarIntervals);
            intervalStoreEntries.entrySet().stream()
                    .filter(entry -> entry.getKey() != 0)
                    .forEach(entry -> metrics.put("Size " + entry.getKey() + " intervals", entry.getValue()));
        }

        public void putValues(Map<String, Object> valueMap) {
            valueMap.putAll(metrics);
        }

        @Override
        public String toString() {
            return metrics.entrySet().stream()
                    .map(e -> String.format("%50s: %s", e.getKey(), e.getValue()))
                    .collect(joining("\n"));
        }
    }

}
