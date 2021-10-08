// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A PredicateQueryItem is a collection of feature/value-pairs
 * that are used to query predicate fields, which contains boolean
 * constraints. If the feature/value-pairs from the PredicateQueryItem
 * satisfies the boolean constraints, the document is a match.
 *
 * @author Magnar Nedland
 */
public class PredicateQueryItem extends SimpleTaggableItem {

    private String fieldName = "predicate";
    private ArrayList<Entry> features = new ArrayList<>();
    private ArrayList<RangeEntry> rangeFeatures = new ArrayList<>();
    public static final long ALL_SUB_QUERIES = 0xffffffffffffffffL;

    /**
     * Sets the field name to be used for the predicates.
     * @param index name of the field.
     */
    @Override
    public void setIndexName(String index) {
        this.fieldName = index;
    }

    /**
     * @return the field name used for the predicates.
     */
    public String getIndexName() {
        return fieldName;
    }

    /**
     * Adds a feature/value-pair to the predicate query. This feature is applied to all sub queries.
     * @param key name of the feature to be set in this query.
     * @param value value of the feature.
     */
    public void addFeature(String key, String value) {
        addFeature(key, value, ALL_SUB_QUERIES);
    }

    /**
     * Adds a feature/value-pair to the predicate query.
     * @param key name of the feature to be set in this query.
     * @param value value of the feature.
     * @param subQueryBitmap bitmap specifying which sub queries this feature applies to.
     */
    public void addFeature(String key, String value, long subQueryBitmap) {
        addFeature(new Entry(key, value, subQueryBitmap));
    }

    /**
     * Adds a feature/value-pair to the predicate query.
     * @param entry the feature to add.
     */
    public void addFeature(Entry entry) {
        features.add(entry);
    }

    /**
     * Adds a range feature with a given value to the predicate query.
     * This feature is applied to all sub queries.
     * @param key name of the feature to be set in this query.
     * @param value value of the feature.
     */
    public void addRangeFeature(String key, long value) {
        addRangeFeature(key, value, ALL_SUB_QUERIES);
    }

    /**
     * Adds a range feature with a given value to the predicate query.
     * @param key name of the feature to be set in this query.
     * @param value value of the feature.
     * @param subQueryBitmap bitmap specifying which sub queries this feature applies to.
     */
    public void addRangeFeature(String key, long value, long subQueryBitmap) {
        addRangeFeature(new RangeEntry(key, value, subQueryBitmap));
    }

    /**
     * Adds a range feature with a given value to the predicate query.
     * @param entry the feature to add.
     */
    public void addRangeFeature(RangeEntry entry) {
        rangeFeatures.add(entry);
    }

    /**
     * @return a mutable collection of feature entries.
     */
    public Collection<Entry> getFeatures() {
        return features;
    }

    /**
     * @return a mutable collection of range feature entries.
     */
    public Collection<RangeEntry> getRangeFeatures() {
        return rangeFeatures;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.PREDICATE_QUERY;
    }

    @Override
    public String getName() {
        return "PREDICATE_QUERY_ITEM";
    }

    @Override
    public int encode(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(fieldName, buffer);
        encodeFeatures(features, buffer);
        encodeFeatures(rangeFeatures, buffer);
        return 1;  // number of encoded stack dump items
    }

    private void encodeFeatures(ArrayList<? extends EntryBase> features, ByteBuffer buffer) {
        IntegerCompressor.putCompressedPositiveNumber(features.size(), buffer);
        for (EntryBase e : features) {
            e.encode(buffer);
        }
    }

    @Override
    public int getTermCount() {
        return 1;  // one big term
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        boolean first = true;
        for (Entry e : features) {
            if (!first) {
                buffer.append(", ");
            } else {
                first = false;
            }
            buffer.append(e.getKey()).append('=').append(e.getValue());
            if (e.getSubQueryBitmap() != ALL_SUB_QUERIES) {
                buffer.append("[0x").append(Long.toHexString(e.getSubQueryBitmap())).append(']');
            }
        }
        for (RangeEntry e : rangeFeatures) {
            if (!first) {
                buffer.append(", ");
            } else {
                first = false;
            }
            buffer.append(e.getKey()).append(':').append(e.getValue());
            if (e.getSubQueryBitmap() != ALL_SUB_QUERIES) {
                buffer.append("[0x").append(Long.toHexString(e.getSubQueryBitmap())).append(']');
            }
        }
    }

    @Override
    public PredicateQueryItem clone() {
        PredicateQueryItem clone = (PredicateQueryItem)super.clone();
        clone.features = new ArrayList<>(this.features);
        clone.rangeFeatures = new ArrayList<>(this.rangeFeatures);
        return clone;
    }

    public abstract static class EntryBase {
        private String key;
        private long subQueryBitmap;

        public EntryBase(String key, long subQueryBitmap) {
            this.key = key;
            this.subQueryBitmap = subQueryBitmap;
        }

        public String getKey() {
            return key;
        }

        public long getSubQueryBitmap() {
            return subQueryBitmap;
        }

        public void setSubQueryBitmap(long subQueryBitmap) {
            this.subQueryBitmap = subQueryBitmap;
        }

        public abstract void encode(ByteBuffer buffer);
    }

    public static class Entry extends EntryBase {
        private String value;

        public Entry(String key, String value) {
            this(key, value, ALL_SUB_QUERIES);
        }
        public Entry(String key, String value, long subQueryBitmap) {
            super(key, subQueryBitmap);
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public void encode(ByteBuffer buffer) {
            putString(getKey(), buffer);
            putString(getValue(), buffer);
            buffer.putLong(getSubQueryBitmap());
        }
    }

    public static class RangeEntry extends EntryBase {
        private long value;

        public RangeEntry(String key, long value) {
            this(key, value, ALL_SUB_QUERIES);
        }

        public RangeEntry(String key, long value, long subQueryBitmap) {
            super(key, subQueryBitmap);
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        @Override
        public void encode(ByteBuffer buffer) {
            putString(getKey(), buffer);
            buffer.putLong(getValue());
            buffer.putLong(getSubQueryBitmap());
        }
    }
}
