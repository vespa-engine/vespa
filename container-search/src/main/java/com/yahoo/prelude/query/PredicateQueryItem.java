// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
    private List<Entry> features = new ArrayList<>();
    private List<RangeEntry> rangeFeatures = new ArrayList<>();
    public static final long ALL_SUB_QUERIES = 0xffffffffffffffffL;

    /** Sets the name of the index (field) to be used for the predicates. */
    @Override
    public void setIndexName(String index) {
        this.fieldName = index;
    }

    /** Returns the name of the index (field) used for the predicates. */
    public String getIndexName() {
        return fieldName;
    }

    /**
     * Adds a feature/value-pair to the predicate query. This feature is applied to all sub queries.
     *
     * @param key name of the feature to be set in this query
     * @param value value of the feature
     */
    public void addFeature(String key, String value) {
        addFeature(key, value, ALL_SUB_QUERIES);
    }

    /**
     * Adds a feature/value-pair to the predicate query.
     *
     * @param key name of the feature to be set in this query
     * @param value value of the feature
     * @param subQueryBitmap bitmap specifying which sub queries this feature applies to
     */
    public void addFeature(String key, String value, long subQueryBitmap) {
        addFeature(new Entry(key, value, subQueryBitmap));
    }

    /**
     * Adds a feature/value-pair to the predicate query.
     *
     * @param entry the feature to add
     */
    public void addFeature(Entry entry) {
        features.add(entry);
    }

    /**
     * Adds a range feature with a given value to the predicate query.
     * This feature is applied to all sub queries.
     *
     * @param key name of the feature to be set in this query
     * @param value value of the feature
     */
    public void addRangeFeature(String key, long value) {
        addRangeFeature(key, value, ALL_SUB_QUERIES);
    }

    /**
     * Adds a range feature with a given value to the predicate query.
     *
     * @param key name of the feature to be set in this query
     * @param value value of the feature
     * @param subQueryBitmap bitmap specifying which sub queries this feature applies to
     */
    public void addRangeFeature(String key, long value, long subQueryBitmap) {
        addRangeFeature(new RangeEntry(key, value, subQueryBitmap));
    }

    /**
     * Adds a range feature with a given value to the predicate query.
     *
     * @param entry the feature to add
     */
    public void addRangeFeature(RangeEntry entry) {
        rangeFeatures.add(entry);
    }

    /** Returns a mutable collection of feature entries. */
    public Collection<Entry> getFeatures() {
        return features;
    }

    /** Returns a mutable collection of range feature entries. */
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

    private void encodeFeatures(List<? extends EntryBase> features, ByteBuffer buffer) {
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

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (PredicateQueryItem)o;
        if ( ! this.fieldName.equals(other.fieldName)) return false;
        if ( ! this.features.equals(other.features)) return false;
        if ( ! this.rangeFeatures.equals(other.rangeFeatures)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fieldName, features, rangeFeatures);
    }

    /** An entry in a predicate item. This is immutable. */
    public abstract static class EntryBase {

        private final String key;
        private final long subQueryBitmap;

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

        public abstract void encode(ByteBuffer buffer);

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;

            var other = (EntryBase)o;
            if ( ! Objects.equals(this.key, other.key)) return false;
            if ( this.subQueryBitmap != other.subQueryBitmap) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), key, subQueryBitmap);
        }

    }

    /** A unique entry in a predicate item. This is immutable. */
    public static class Entry extends EntryBase {

        private final String value;

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

        @Override
        public boolean equals(Object other) {
            if ( ! super.equals(other)) return false;
            if ( ! Objects.equals(this.value, ((Entry)other).value)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), value);
        }

    }

    /** A range entry in a predicate item. This is immutable. */
    public static class RangeEntry extends EntryBase {

        private final long value;

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

        @Override
        public boolean equals(Object other) {
            if ( ! super.equals(other)) return false;
            if ( this.value != ((RangeEntry)other).value) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), value);
        }

    }

}
