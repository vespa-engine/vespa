// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Weak And of a collection of sub-expressions:
 * this behaves like an OR unless many hits are returned, and then
 * it starts acting more like an AND.
 * Alternately it can be viewed as an n-of-m operator where n
 * is 1 at first and then increases gradually to m as more hits
 * are seen.
 *
 * @author arnej27959
 */
public final class WeakAndItem extends NonReducibleCompositeItem {

    /** The default targetHits used if none is specified: 100 */
    private static final int defaultTargetHits = 100;

    /** The default N used if none is specified: 100 */
    @Deprecated
    public static final int defaultN = defaultTargetHits;  // TODO Vespa 9: Remove

    private Integer targetHits = null;
    private Integer totalTargetHits = null;

    public WeakAndItem() {}

    @Deprecated
    public WeakAndItem(String unusedIndex) {}

    // For binary compatibility on Vespa 8. TODO: Remove on Vespa 9.
    @Deprecated
    public WeakAndItem(int targetHits) {
        this(Integer.valueOf(targetHits));
    }

    public WeakAndItem(Integer targetHits) {
        this.targetHits = targetHits;
    }

    // For binary compatibility on Vespa 8. TODO: Remove on Vespa 9.
    @Deprecated
    public WeakAndItem(String unusedIndex, int targetHits) {
        this(Integer.valueOf(targetHits));
    }

    /**
     * Make a WeakAnd item with no children.
     *
     * @param unusedIndex the default field to search. This can be overridden in each child.
     * @param targetHits the target minimum number of hits to produce per content node
     */
    @Deprecated
    public WeakAndItem(String unusedIndex, Integer targetHits) {
        this(targetHits);
    }

    @Override
    public ItemType getItemType() { return ItemType.WEAK_AND; }

    @Override
    public String getName() { return "WEAKAND"; }

    /**
     * Apply indexName to all child items of this.
     * Used from NGramSearcher.
     */
    @Override
    public void setIndexName(String index) {
        String toSet = (index == null) ? "" : index;
        super.setIndexName(toSet);
    }

    /** Returns the index name set for this (always ""). */
    @Deprecated
    public String getIndexName() { return ""; }

    /** Appends the heading of this string - <code>[getName()]([limit]) </code> */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        if (targetHits != null) {
            buffer.append("(");
            buffer.append(targetHits);
            buffer.append(")");
        }
        if (totalTargetHits != null) {
            buffer.append(" {");
            buffer.append("totalTargetHits=").append(totalTargetHits);
            buffer.append("}");
        }
        buffer.append(" ");
    }

    /** Returns the number of hits to produce per node, or null if not set. */
    public Integer getTargetHits() { return targetHits; }

    /** Returns the total number of hits to produce across all nodes, or null if not set. */
    public Integer getTotalTargetHits() { return totalTargetHits; }

    /** Set the number of hits to produce per node. */
    public void setTargetHits(Integer target) { this.targetHits = target; }

    /** Set the total number of hits to produce across all nodes. */
    public void setTotalTargetHits(Integer total) { this.totalTargetHits = total; }

    /**
     * Returns the target number of hits to produce per node, or the default if not set
     *
     * @deprecated use getTargetHits()
     */
    @Deprecated
    public int getN() { return targetHits != null && targetHits > 0 ? targetHits : defaultTargetHits; }

    /**
     * Returns whether targetHits was explicitly set.
     *
     * @deprecated check getTargetHits() != null instead
     */
    @Deprecated
    public boolean nIsExplicit() { return targetHits != null && targetHits > 0; }

    /**
     * Set the target for minimum number of hits
     *
     * @deprecated use setTargetHits(Integer)
     */
    @Deprecated
    public void setN(int N) { this.targetHits = N; }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context);
        IntegerCompressor.putCompressedPositiveNumber(resolveTargetHits(context), buffer);
        putString("", buffer);
    }

    private WeakAndItem foldSegments() {
        var result = new WeakAndItem();
        result.setTargetHits(this.targetHits);
        result.setTotalTargetHits(this.totalTargetHits);
        for (var child : items()) {
            if (child instanceof SegmentItem segment && segment.shouldFoldIntoWand()) {
                for (var subItem : segment.items()) {
                    result.addItem(subItem);
                }
            } else {
                result.addItem(child);
            }
        }
        return result;
    }

    private boolean needsFolding() {
        for (var subItem : items()) {
            if (subItem instanceof SegmentItem segment) {
                if (segment.shouldFoldIntoWand()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int encode(ByteBuffer buffer, SerializationContext context) {
        if (needsFolding()) {
            return foldSegments().encode(buffer, context);
        } else {
            return super.encode(buffer, context);
        }
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        if (targetHits != null)
            discloser.addProperty("targetHits", targetHits);
        if (totalTargetHits != null)
            discloser.addProperty("totalTargetHits", totalTargetHits);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), targetHits, totalTargetHits); }

    /** Returns whether this item is of the same class and contains the same state as the given item. */
    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        WeakAndItem other = (WeakAndItem) object; // Ensured by superclass
        if ( ! Objects.equals(this.targetHits, other.targetHits)) return false;
        if ( ! Objects.equals(this.totalTargetHits, other.totalTargetHits)) return false;
        return true;
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemWeakAnd.newBuilder();
        builder.setIndex("");
        builder.setTargetNumHits(resolveTargetHits(context));
        for (var child : items()) {
            builder.addChildren(child.toProtobuf(context));
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemWeakAnd(builder.build())
                .build();
    }

    private int resolveTargetHits(SerializationContext context) {
        if (targetHits != null) return targetHits;
        if (totalTargetHits != null)
            return context.contentShareOf(totalTargetHits);
        return defaultTargetHits;
    }

}
