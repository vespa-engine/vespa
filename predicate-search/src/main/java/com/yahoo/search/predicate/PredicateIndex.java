// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.api.annotations.Beta;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.search.predicate.index.*;
import com.yahoo.search.predicate.index.conjunction.ConjunctionHit;
import com.yahoo.search.predicate.index.conjunction.ConjunctionIndex;
import com.yahoo.search.predicate.serialization.SerializationHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * An index of {@link Predicate} objects.
 * <p>
 * Use a {@link PredicateQuery} to find the ids of documents that have matching Predicates.
 * Create an instance of {@link PredicateIndex} using the {@link PredicateIndexBuilder}.
 * </p><p>
 * To build a {@link PredicateQuery} you add features and rangeFeatures with a 64-bit
 * bitmap specifying which subqueries they appear in.
 * </p><p>
 * To perform a search, create a {@link Searcher} and call its {@link Searcher#search(PredicateQuery)}
 * method, which returns a stream of {@link Hit} objects,
 * each of which contains a document id and a 64-bit bitmap specifying which subqueries the hit is for.
 * </p><p>
 * Note that the {@link PredicateIndex} is thread-safe, but a {@link Searcher} is not.
 * Each thread <strong>must</strong> use its own searcher.
 * </p>
 * @author Magnar Nedland
 * @author bjorncs
 */
@Beta
public class PredicateIndex {

    private static final int SERIALIZATION_FORMAT_VERSION = 3;

    private final PredicateRangeTermExpander expander;
    private final int[] internalToExternalIdMapping;
    private final byte[] minFeatureIndex;
    private final short[] intervalEnds;
    private final int highestIntervalEnd;
    private final SimpleIndex intervalIndex;
    private final SimpleIndex boundsIndex;
    private final SimpleIndex conjunctionIntervalIndex;
    private final PredicateIntervalStore intervalStore;
    private final ConjunctionIndex conjunctionIndex;
    private final int[] zeroConstraintDocuments;
    private final Config config;
    private final AtomicReference<CachedPostingListCounter> postingListCounter;


    /**
     * Package private as the index should be constructed using {@link PredicateIndexBuilder}.
     */
    PredicateIndex(
            Config config,
            int[] internalToExternalIdMapping,
            byte[] minFeatureIndex,
            short[] intervalEnds,
            int highestIntervalEnd,
            SimpleIndex intervalIndex,
            SimpleIndex boundsIndex,
            SimpleIndex conjunctionIntervalIndex,
            PredicateIntervalStore intervalStore,
            ConjunctionIndex conjunctionIndex,
            int[] zeroConstraintDocuments) {
        this.internalToExternalIdMapping = internalToExternalIdMapping;
        this.minFeatureIndex = minFeatureIndex;
        this.intervalEnds = intervalEnds;
        this.highestIntervalEnd = highestIntervalEnd;
        this.intervalIndex = intervalIndex;
        this.boundsIndex = boundsIndex;
        this.conjunctionIntervalIndex = conjunctionIntervalIndex;
        this.intervalStore = intervalStore;
        this.conjunctionIndex = conjunctionIndex;
        this.zeroConstraintDocuments = zeroConstraintDocuments;
        this.expander = new PredicateRangeTermExpander(config.arity, config.lowerBound, config.upperBound);
        this.config = config;
        this.postingListCounter = new AtomicReference<>(new CachedPostingListCounter(internalToExternalIdMapping.length));
    }

    public void rebuildPostingListCache() {
        postingListCounter.getAndUpdate(CachedPostingListCounter::rebuildCache);
    }

    /**
     * Create a new searcher.
     */
    public Searcher searcher() {
        return new Searcher();
    }

    public void writeToOutputStream(DataOutputStream out) throws IOException {
        out.writeInt(SERIALIZATION_FORMAT_VERSION);
        config.writeToOutputStream(out);
        SerializationHelper.writeIntArray(internalToExternalIdMapping, out);
        SerializationHelper.writeByteArray(minFeatureIndex, out);
        SerializationHelper.writeShortArray(intervalEnds, out);
        out.writeInt(highestIntervalEnd);
        SerializationHelper.writeIntArray(zeroConstraintDocuments, out);
        intervalIndex.writeToOutputStream(out);
        boundsIndex.writeToOutputStream(out);
        conjunctionIntervalIndex.writeToOutputStream(out);
        intervalStore.writeToOutputStream(out);
        conjunctionIndex.writeToOutputStream(out);
    }

    public static PredicateIndex fromInputStream(DataInputStream in) throws IOException {
        int version = in.readInt();
        if (version != SERIALIZATION_FORMAT_VERSION) {
            throw new IllegalArgumentException(String.format(
                    "Invalid serialization format version. Expected %d, was %d.", SERIALIZATION_FORMAT_VERSION, version));
        }
        Config config = Config.fromInputStream(in);
        int[] internalToExternalIdMapping = SerializationHelper.readIntArray(in);
        byte[] minFeatureIndex = SerializationHelper.readByteArray(in);
        short[] intervalEnds = SerializationHelper.readShortArray(in);
        int highestIntervalEnd = in.readInt();
        int[] zeroConstraintDocuments = SerializationHelper.readIntArray(in);
        SimpleIndex intervalIndex = SimpleIndex.fromInputStream(in);
        SimpleIndex boundsIndex = SimpleIndex.fromInputStream(in);
        SimpleIndex conjunctionIntervalIndex = SimpleIndex.fromInputStream(in);
        PredicateIntervalStore intervalStore = PredicateIntervalStore.fromInputStream(in);
        ConjunctionIndex conjunctionIndex = ConjunctionIndex.fromInputStream(in);
        return new PredicateIndex(
                config,
                internalToExternalIdMapping,
                minFeatureIndex,
                intervalEnds,
                highestIntervalEnd,
                intervalIndex,
                boundsIndex,
                conjunctionIntervalIndex,
                intervalStore,
                conjunctionIndex,
                zeroConstraintDocuments
        );
    }

    @Beta
    public class Searcher {
        private final byte[] nPostingListsForDocument;
        private final ConjunctionIndex.Searcher conjunctionIndexSearcher;

        private Searcher() {
            this.nPostingListsForDocument = new byte[internalToExternalIdMapping.length];
            this.conjunctionIndexSearcher = conjunctionIndex.searcher();
        }

        /**
         * Retrieves a stream of hits for the given query.
         *
         * @param query Specifies the boolean variables that are true.
         * @return A stream of hits.
         */
        public Stream<Hit> search(PredicateQuery query) {
            ArrayList<PostingList> postingLists = new ArrayList<>();
            for (PredicateQuery.Feature feature : query.getFeatures()) {
                addIntervalPostingList(feature.featureHash, feature.subqueryBitmap, postingLists);
            }
            for (PredicateQuery.RangeFeature feature : query.getRangeFeatures()) {
                expander.expand(
                        feature.key,
                        feature.value,
                        featureHash -> addIntervalPostingList(featureHash, feature.subqueryBitmap, postingLists),
                        (featureHash, value) -> addBoundsPostingList(featureHash, value, feature.subqueryBitmap, postingLists));
            }
            addCompressedZStarPostingList(postingLists);
            addConjunctionPostingLists(query, postingLists);
            addZeroConstraintPostingList(postingLists);

            CachedPostingListCounter counter = postingListCounter.get();
            counter.registerUsage(postingLists);
            counter.countPostingListsPerDocument(postingLists, nPostingListsForDocument);
            return new PredicateSearch(
                    postingLists, nPostingListsForDocument, minFeatureIndex, intervalEnds, highestIntervalEnd).stream()
                    // Map to external id. Note that internal id for first document is 1.
                    .map(hit -> new Hit(internalToExternalIdMapping[hit.getDocId()], hit.getSubquery()));
        }

        private void addCompressedZStarPostingList(List<PostingList> postingLists) {
            SimpleIndex.Entry e = intervalIndex.getPostingList(Feature.Z_STAR_COMPRESSED_ATTRIBUTE_HASH);
            if (e != null) {
                postingLists.add(new ZstarCompressedPostingList(intervalStore, e.docIds, e.dataRefs));
            }
        }

        private void addBoundsPostingList(
                long featureHash, int value, long subqueryBitMap, List<PostingList> postingLists) {
            SimpleIndex.Entry e = boundsIndex.getPostingList(featureHash);
            if (e != null) {
                postingLists.add(new BoundsPostingList(intervalStore, e.docIds, e.dataRefs, subqueryBitMap, value));
            }
        }

        private void addIntervalPostingList(long featureHash, long subqueryBitMap, List<PostingList> postingLists) {
            SimpleIndex.Entry e = intervalIndex.getPostingList(featureHash);
            if (e != null) {
                postingLists.add(new IntervalPostingList(intervalStore, e.docIds, e.dataRefs, subqueryBitMap));
            }
        }

        private void addConjunctionPostingLists(PredicateQuery query, List<PostingList> postingLists) {
            List<ConjunctionHit> hits = conjunctionIndexSearcher.search(query);
            for (ConjunctionHit hit : hits) {
                SimpleIndex.Entry e = conjunctionIntervalIndex.getPostingList(hit.conjunctionId);
                if (e != null) {
                    postingLists.add(new IntervalPostingList(intervalStore, e.docIds, e.dataRefs, hit.subqueryBitmap));
                }
            }
        }

        private void addZeroConstraintPostingList(ArrayList<PostingList> postingLists) {
            if (zeroConstraintDocuments.length > 0) {
                postingLists.add(new ZeroConstraintPostingList(zeroConstraintDocuments));
            }
        }

    }

}
