// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.data.access.helpers.MatchFeatureData;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Deserializer;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SearchResult {

    public static class Hit implements Comparable<Hit> {
        private final String docId;
        private double rank;
        private MatchFeatureData.HitValue matchFeatures;
        public Hit(Hit h) {
            docId = h.docId;
            rank = h.rank;
            matchFeatures = h.matchFeatures;
        }
        public Hit(String docId, double rank) {
            this.rank = rank;
            this.docId = docId;
            this.matchFeatures = null;
        }
        final public String getDocId() { return docId; }
        final public double getRank() { return rank; }
        final public Optional<MatchFeatureData.HitValue> getMatchFeatures() {
            return Optional.ofNullable(matchFeatures);
        }
        final public void setRank(double rank) { this.rank = rank; }
        final public void setMatchFeatures(MatchFeatureData.HitValue matchFeatures) {
            this.matchFeatures = matchFeatures;
        }
        public int compareTo(Hit h) {
            return (h.rank < rank) ? -1 : (h.rank > rank) ? 1 : 0; // Sort order: descending
        }
    }
    public static class HitWithSortBlob extends Hit {
        private final byte [] sortBlob;
        public HitWithSortBlob(Hit h, byte [] sb) {
            super(h);
            sortBlob = sb;
        }
        final public byte [] getSortBlob() { return sortBlob; }
        public int compareTo(Hit h) {
            HitWithSortBlob b = (HitWithSortBlob) h;
            int m = java.lang.Math.min(sortBlob.length, b.sortBlob.length);
            for (int i = 0; i < m; i++) {
                if (sortBlob[i] != b.sortBlob[i]) {
                    return (((int)sortBlob[i]) & 0xff) < (((int)b.sortBlob[i]) & 0xff) ? -1 : 1;
                }
            }
            return sortBlob.length - b.sortBlob.length;
        }
    }
    private final int    totalHits;
    private final Hit[]  hits;
    private final TreeMap<Integer, byte []> aggregatorList;
    private final TreeMap<Integer, byte []> groupingList;
    private String[] errors;
    private static final int EXTENSION_FLAGS_PRESENT = -1;
    private static final int MATCH_FEATURES_PRESENT_MASK = 1;
    private static final int ERRORS_PRESENT_MASK = 2;

    public SearchResult(Deserializer buf) {
        BufferSerializer bser = (BufferSerializer) buf; // TODO: dirty cast. must do this differently
        bser.order(ByteOrder.BIG_ENDIAN);
        this.totalHits = buf.getInt(null);
        int numHits = buf.getInt(null);
        int extensionFlags = 0;
        if (hasExtensionFlags(numHits)) {
            extensionFlags = buf.getInt(null);
            numHits = buf.getInt(null);
        }
        hits = new Hit[numHits];
        if (numHits != 0) {
            int docIdBufferLength = buf.getInt(null);  // Unused, but need to call getInt() to advance buffer
            byte[] cArr = bser.getBuf().array();
            int start = bser.getBuf().arrayOffset() + bser.position();
            for(int i=0; i < numHits; i++) {
                int end = start;
                while (cArr[end++] != 0);
                hits[i] = new Hit(new String(cArr, start, end-start-1, UTF_8), 0);
                start = end;
            }
            bser.position(start - bser.getBuf().arrayOffset());
            for(int i=0; i < numHits; i++) {
                hits[i].setRank(buf.getDouble(null));
            }
        }

        int numSortBlobs = buf.getInt(null);
        int [] size = new int [numSortBlobs];
        for (int i = 0; i < numSortBlobs; i++) {
            size[i] = buf.getInt(null);
        }
        for (int i = 0; i < numSortBlobs; i++) {
            hits[i] = new HitWithSortBlob(hits[i], buf.getBytes(null, size[i]));
        }

        // Unused, but need to call getInt() to advance buffer
        int numAggregators = buf.getInt(null);
        aggregatorList = new TreeMap<>();
        for (int i = 0; i < numAggregators; i++) {
            int aggrId = buf.getInt(null);
            int aggrLength = buf.getInt(null);
            aggregatorList.put(aggrId, buf.getBytes(null, aggrLength));
        }

        int numGroupings = buf.getInt(null);
        groupingList = new TreeMap<>();
        for (int i = 0; i < numGroupings; i++) {
            int aggrId = buf.getInt(null);
            int aggrLength = buf.getInt(null);
            groupingList.put(aggrId, buf.getBytes(null, aggrLength));
        }

        if (hasMatchFeatures(extensionFlags)) {
            deserializeMatchFeatures(buf, numHits);
        }
        if (hasErrors(extensionFlags)) {
            deserializeErrors(buf);
        } else {
            this.errors = new String[0];
        }
    }

    private void deserializeMatchFeatures(Deserializer buf, int numHits) {
        var featureNames = new ArrayList<String>();
        int numFeatures = buf.getInt(null);
        for (int i = 0; i < numFeatures; ++i) {
            featureNames.add(buf.getString(null));
        }
        var factory = new MatchFeatureData(featureNames);
        for (int i = 0; i < numHits; ++i) {
            var matchFeatures = factory.addHit();
            for (int j = 0; j < numFeatures; ++j) {
                byte featureType = buf.getByte(null);
                if (isDoubleFeature(featureType)) {
                    matchFeatures.set(j, buf.getDouble(null));
                } else {
                    int bufLength = buf.getInt(null);
                    matchFeatures.set(j, buf.getBytes(null, bufLength));
                }
            }
            hits[i].setMatchFeatures(matchFeatures);
        }
    }

    private void deserializeErrors(Deserializer buf) {
        int numErrors = buf.getInt(null);
        this.errors = new String[numErrors];
        for (int i = 0; i < numErrors; ++i) {
            errors[i] = Utf8.toString(buf.getBytes(null, buf.getInt(null)));
        }
    }

    private static boolean hasExtensionFlags(int numHits) {
        return numHits == EXTENSION_FLAGS_PRESENT;
    }

    private static boolean hasMatchFeatures(int extensionFlags) {
        return (extensionFlags & MATCH_FEATURES_PRESENT_MASK) != 0;
    }

    private static boolean hasErrors(int extensionFlags) {
        return (extensionFlags & ERRORS_PRESENT_MASK) != 0;
    }

    private static boolean isDoubleFeature(byte featureType) {
        return featureType == 0;
    }

    final public int getHitCount()      { return hits.length; }
    final public int getTotalHitCount() { return (totalHits != 0) ? totalHits : getHitCount(); }
    final public Hit getHit(int hitNo)  { return hits[hitNo]; }
    final public Map<Integer, byte []> getGroupingList() { return groupingList; }
    final public String[] getErrors() { return errors; }
}
