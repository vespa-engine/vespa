// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Fuzzy search term
 *
 * @author alexeyche
 */
public class FuzzyItem extends TermItem {
    private String term;

    private int maxEditDistance;
    private int prefixLength;
    private boolean prefixMatch;

    public static int DEFAULT_MAX_EDIT_DISTANCE = 2;
    public static int DEFAULT_PREFIX_LENGTH = 0;

    public FuzzyItem(String indexName, boolean isFromQuery, String term, int maxEditDistance, int prefixLength, boolean prefixMatch) {
        super(indexName, isFromQuery, null);
        setValue(term);
        setMaxEditDistance(maxEditDistance);
        setPrefixLength(prefixLength);
        setPrefixMatch(prefixMatch);
    }

    public FuzzyItem(String indexName, boolean isFromQuery, String term, int maxEditDistance, int prefixLength) {
        this(indexName, isFromQuery, term, maxEditDistance, prefixLength, false);
    }

    public void setMaxEditDistance(int maxEditDistance) {
        this.maxEditDistance = maxEditDistance;
    }

    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    public int getPrefixLength() {
        return this.prefixLength;
    }

    public int getMaxEditDistance() {
        return this.maxEditDistance;
    }

    public boolean isPrefixMatch() {
        return this.prefixMatch;
    }

    public void setPrefixMatch(boolean prefixMatch) {
        this.prefixMatch = prefixMatch;
    }

    @Override
    protected boolean hasPrefixMatchSemantics() {
        return this.prefixMatch;
    }

    @Override
    public void setValue(String value) {
        this.term = value;
    }

    @Override
    public String getRawWord() {
        return stringValue();
    }

    @Override
    public boolean isWords() {
        return false;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.FUZZY;
    }

    @Override
    public String getName() {
        return "FUZZY";
    }

    @Override
    public String stringValue() {
        return term;
    }

    @Override
    public boolean isStemmed() {
        return false;
    }

    @Override
    public String getIndexedString() {
        return stringValue();
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FuzzyItem fuzzyItem = (FuzzyItem) o;
        return maxEditDistance == fuzzyItem.maxEditDistance &&
                prefixLength == fuzzyItem.prefixLength &&
                prefixMatch == fuzzyItem.prefixMatch &&
                Objects.equals(term, fuzzyItem.term);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), term, maxEditDistance, prefixLength, prefixMatch);
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append('(');
        buffer.append(this.term);
        buffer.append(',');
        buffer.append(this.maxEditDistance);
        buffer.append(',');
        buffer.append(this.prefixLength);
        buffer.append(',');
        buffer.append(this.prefixMatch);
        buffer.append(") ");
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        // Prefix matching is communicated via term header flags
        super.encodeThis(buffer);
        putString(getIndexedString(), buffer);
        IntegerCompressor.putCompressedPositiveNumber(this.maxEditDistance, buffer);
        IntegerCompressor.putCompressedPositiveNumber(this.prefixLength, buffer);
    }
}

