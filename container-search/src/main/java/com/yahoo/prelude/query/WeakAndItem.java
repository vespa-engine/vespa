// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    public static final int defaultN = 100;

    private int n;
    private String index;

    /** Creates a WAND item with default N */
    public WeakAndItem() {
        this(defaultN);
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
        buffer.append(n);
        buffer.append(") ");
    }

    public int getN() { return n; }

    public void setN(int N) { this.n = N; }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(n, buffer);
        putString(index, buffer);
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("N", n);
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
