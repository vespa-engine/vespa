// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Superclass of "leaf" conditions containing a single entity which is either matched in a field or not.
 *
 * @author bratseth
 * @author havardpe
 */
public abstract class TermItem extends SimpleIndexedItem implements BlockItem {

    /** Whether the term is from the raw query or is synthetic. */
    private boolean isFromQuery;

    /** Whether accent dropping should be performed */
    private boolean normalizable = true;

    /** The substring which is the raw form of the source of this token, or null if none. */
    private Substring origin;

    private SegmentingRule segmentingRule = SegmentingRule.LANGUAGE_DEFAULT;

    public TermItem() {
        this("");
    }

    public TermItem(String indexName) {
        this(indexName, false);
    }

    public TermItem(String indexName, boolean isFromQuery) {
        this(indexName, isFromQuery, null);
    }

    protected TermItem(String indexName, boolean isFromQuery, Substring origin) {
        setIndexName(indexName);
        this.isFromQuery = isFromQuery;
        this.origin = origin;
    }

    public final int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        return 1;
    }

    /** Appends the index prefix if necessary and delegates to the subclass */
    protected final void appendBodyString(StringBuilder buffer) {
        appendIndexString(buffer);
        buffer.append(stringValue());
    }

    /**
     * Sets the value of this item from a string.
     *
     * @throws UnsupportedOperationException if this is not supported on this kind of item
     */
    public abstract void setValue(String value);

    /** Returns the raw form of the text leading to this term, exactly as received, including original casing */
    public abstract String getRawWord();

    /**
     * Returns the substring which is the raw form of the text leading to this token. This substring also contains
     * the superstring this substring was a part of, e.g the whole query string.
     * If this did not originate directly from a user string, this is null.
     */
    @Override
    public Substring getOrigin() { return origin; }

    /**
     * Whether this term is from the query or has been added by a searcher.
     * Only terms from the user should be modified by query rewriters which attempts to improve the
     * precision or recall of the user's query.
     */
    @Override
    public boolean isFromQuery() { return isFromQuery; }

    public void setFromQuery(boolean isFromQuery) {
        this.isFromQuery = isFromQuery;
    }

    @Override
    public abstract boolean isWords();

    /** Sets the origin of this */
    public void setOrigin(Substring origin) {
        this.origin = origin;
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("origin", origin);
        discloser.setValue(stringValue());
    }

    @Override
    public int getTermCount() { return 1; }

    /** Returns whether accent removal is a meaningful and possible operation for this word. */
    public boolean isNormalizable() { return normalizable; }

    /**
     * Sets whether accent removal is a meaningful and possible operation for this word.
     *
     * @param normalizable set to true if accent removal can/should be performed
     */
    public void setNormalizable(boolean normalizable) { this.normalizable = normalizable; }

    @Override
    public SegmentingRule getSegmentingRule() { return segmentingRule; }

    public void setSegmentingRule(SegmentingRule segmentingRule) { this.segmentingRule = segmentingRule; }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (TermItem)o;
        if ( this.isFromQuery != other.isFromQuery) return false;
        if ( this.normalizable != other.normalizable) return false;
        if ( this.segmentingRule != other.segmentingRule) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isFromQuery, normalizable, segmentingRule);
    }

}
