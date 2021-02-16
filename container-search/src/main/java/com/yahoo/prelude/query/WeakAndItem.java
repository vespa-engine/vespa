// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;

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

    private int n;
    private String index;
    private int scoreThreshold = 0;

    public ItemType getItemType() {
        return ItemType.WEAK_AND;
    }

    public String getName() {
        return "WAND";
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
    public WeakAndItem(int N) {
        this("", N);
    }

    /** Sets the index name of all subitems of this */
    public void setIndexName(String index) {
        String toSet = (index == null) ? "" : index;
        super.setIndexName(toSet);
        this.index = toSet;
    }

    public String getIndexName() {
        return index;
    }

    /** Appends the heading of this string - <code>[getName()]([limit]) </code> */
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append("(");
        buffer.append(n);
        buffer.append(")");
        buffer.append(" ");
    }

    /** The default N used if none is specified: 100 */
    public static final int defaultN = 100;

    /** Creates a WAND item with default N */
    public WeakAndItem() {
        this(defaultN);
    }

    public int getN() {
        return n;
    }

    public void setN(int N) {
        this.n = N;
    }

    @Deprecated // TODO: Remove on Vespa 8
    public int getScoreThreshold() {
        return scoreThreshold;
    }

    /**
     * Noop.
     *
     * @deprecated has no effect
     */
    @Deprecated // TODO: Remove on Vespa 8
    public void setScoreThreshold(int scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

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
    public int hashCode() {
        return super.hashCode() + 31 * n;
    }

    /** Returns whether this item is of the same class and contains the same state as the given item. */
    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        WeakAndItem other = (WeakAndItem) object; // Ensured by superclass
        if (this.n != other.n) return false;
        return true;
    }

}
