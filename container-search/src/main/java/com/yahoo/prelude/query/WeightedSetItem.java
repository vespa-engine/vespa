// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.collections.CopyOnWriteHashMap;
import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

/**
 * A term which contains a weighted set.
 *
 * When using a weighted set to search a field, all tokens present in
 * the searched field will be reverse matched against the weighted
 * set. This means that using a weighted set to search a single-value
 * attribute field will have similar semantics to using a normal term
 * to search a weighted set field. The low-level matching information
 * resulting from matching a document with a weighted set term will
 * contain the weights of all the matched tokens in descending
 * order. Each matched weight will be represented as a standard
 * occurrence on position 0 in element 0.
 */
public class WeightedSetItem extends SimpleTaggableItem {

    private String indexName;
    private CopyOnWriteHashMap<Object, Integer> set;

    /** Creates an empty weighted set; note you must provide an index name up front */
    public WeightedSetItem(String indexName) {
        this.indexName = requireNonNullElse(indexName, "");
        set = new CopyOnWriteHashMap<>(1000);
    }

    public WeightedSetItem(String indexName, Map<Object, Integer> map) {
        this.indexName = requireNonNullElse(indexName, "");
        set = new CopyOnWriteHashMap<>(map);
    }

    public Integer addToken(long value, int weight) {
        return addInternal(value, weight);
    }

    /**
     * Adds a weighted token.
     * If this token is already in the set, the maximum weight is kept.
     * The weight must be 1 or more; negative values (and zero) are not allowed.
     *
     * @return the weight of the added token (might be the old value, if kept)
     */
    public Integer addToken(String token, int weight) {
        if (token == null) throw new IllegalArgumentException("token must be a string");
        return addInternal(token, weight);
    }

    private Integer addInternal(Object token, int weight) {
        Integer newWeight = weight;
        Integer oldWeight = set.put(token, newWeight);
        if (oldWeight != null && oldWeight > newWeight) {
            set.put(token, oldWeight);
            return oldWeight;
        }
        return newWeight;
    }

    /** Adds a token with weight 1. */
    public Integer addToken(String token) {
        return addToken(token, 1);
    }

    public Integer getTokenWeight(Object token) {
        return set.get(token);
    }

    public Integer removeToken(String token) {
        return set.remove(token);
    }

    public int getNumTokens() {
        return set.size();
    }

    public Iterator<Map.Entry<Object,Integer>> getTokens() {
        return set.entrySet().iterator();
    }

    @Override
    public void setIndexName(String index) {
        this.indexName = requireNonNullElse(index, "");
    }

    public String getIndexName() {
        return indexName;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.WEIGHTEDSET;
    }

    @Override
    public String getName() {
        return getItemType().name();
    }

    // for tracing - random text format
    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append(indexName);
        buffer.append("{");
        for (Map.Entry<Object, Integer> entry : set.entrySet()) {
            buffer.append("[");
            buffer.append(entry.getValue());
            buffer.append("]:\"");
            buffer.append(entry.getKey());
            buffer.append("\",");
        }
        buffer.deleteCharAt(buffer.length() - 1); // remove extra ","
        buffer.append("}");
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("index", indexName);
        for (Map.Entry<Object, Integer> entry : set.entrySet())
            discloser.addChild(asItem(entry));
    }

    @Override
    public int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        int itemCount = 1;
        for (Map.Entry<Object, Integer> entry : set.entrySet()) {
            asItem(entry).encode(buffer);
            itemCount++;
        }
        return itemCount;
    }

    private PureWeightedItem asItem(Map.Entry<Object, Integer> entry) {
        Object key = entry.getKey();
        if (key instanceof Long)
            return new PureWeightedInteger((Long)key, entry.getValue());
        else
            return new PureWeightedString(key.toString(), entry.getValue());
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(set.size(), buffer);
        putString(indexName, buffer);
    }

    @Override
    public int getTermCount() {
        return 1; // this is just one (big) term
    }

    @Override
    public WeightedSetItem clone() {
        WeightedSetItem clone = (WeightedSetItem)super.clone();
        clone.set = this.set.clone();
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! super.equals(o)) return false;
        var other = (WeightedSetItem)o;
        if ( ! Objects.equals(this.indexName, other.indexName)) return false;
        if ( ! Objects.equals(this.set, other.set)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), indexName, set);
    }

}
