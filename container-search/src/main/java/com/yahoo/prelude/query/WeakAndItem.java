// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Weak And of a collection of sub-expressions:
 * this behaves like an OR unless many hits are returned and then
 * it starts acting more like an AND.
 * Alternately it can be viewed as an n-of-m operator where n
 * is 1 at first and then increases gradually to m as more hits
 * are seen.
 *
 * @author arnej27959
 */
public final class WeakAndItem extends NonReducibleCompositeItem {

    /** The default N used if none is specified: 100 */
    public static final int defaultN = 100;  // TODO Vespa 9: Make private

    private int n;
    private String index;

    /** Creates a WAND item with default N */
    public WeakAndItem() {
        this(-1);
    }

    public WeakAndItem(int N) {
        this("", N);
    }

    /**
     * Make a WeakAnd item with no children. You can mention a common index or you can mention it on each child.
     *
     * @param index the index to search
     * @param n the target for minimum number of hits to produce;
     *        a backend will not suppress any hits in the operator
     *        until N hits have been produced.
     */
    public WeakAndItem(String index, int n) {
        this.n = n;
        this.index = (index == null) ? "" : index;
    }

    @Override
    public ItemType getItemType() { return ItemType.WEAK_AND; }

    @Override
    public String getName() { return "WEAKAND"; }

    /**
     * Sets the default index name to apply to all child items of this.
     * This is useful in conjunction with using {@link PureWeightedItem}s as children.
     */
    @Override
    public void setIndexName(String index) {
        String toSet = (index == null) ? "" : index;
        super.setIndexName(toSet);
        this.index = toSet;
    }

    /** Returns the index name set for this, or null if none. */
    public String getIndexName() { return index; }

    /** Appends the heading of this string - <code>[getName()]([limit]) </code> */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append("(");
        buffer.append(getN());
        buffer.append(") ");
    }

    public int getN() { return nIsExplicit() ? n : defaultN; }
    public boolean nIsExplicit() { return n > 0; }
    public void setN(int N) { this.n = N; }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(getN(), buffer);
        putString(index, buffer);
    }

    private WeakAndItem foldSegments() {
        var result = new WeakAndItem(this.index, this.n);
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
    public int encode(ByteBuffer buffer) {
        if (needsFolding()) {
            return foldSegments().encode(buffer);
        } else {
            return super.encode(buffer);
        }
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("N", getN());
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), n, index); }

    /** Returns whether this item is of the same class and contains the same state as the given item. */
    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        WeakAndItem other = (WeakAndItem) object; // Ensured by superclass
        if (this.n != other.n) return false;
        if ( ! Objects.equals(this.index, other.index)) return false;
        return true;
    }

}
