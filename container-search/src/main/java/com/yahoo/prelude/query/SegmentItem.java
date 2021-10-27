// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.util.Objects;


/**
 * An immutable and'ing of a collection of sub-expressions. It does not
 * extend AndItem to avoid code using instanceof handling it as an
 * AndItem.
 *
 * @author Steinar Knutsen
 */
public abstract class SegmentItem extends CompositeItem implements BlockItem {

    private boolean locked = false;
    private final String rawWord;
    private final String value;
    private final boolean isFromQuery;
    private boolean isFromUser;
    private final boolean stemmed;
    private SegmentingRule segmentingRule = SegmentingRule.LANGUAGE_DEFAULT;
    private final Substring origin;

    /**
     * Creates a new segment item
     *
     * @param rawWord the raw form of this segment as received in the request
     * @param current the current transformed version of the raw form, or the raw form repeated if no normalized form is known
     * @param isFromQuery whether this segment stems from the query received in the request
     * @param stemmed whether this is stemmed
     */
    public SegmentItem(String rawWord, String current, boolean isFromQuery, boolean stemmed) {
        this(rawWord, current, isFromQuery, stemmed, null);
    }

    /**
     * Creates a new segment item
     *
     * @param rawWord the raw form of this segment as received in the request
     * @param current the current transformed version of the raw form, or the raw form repeated if no normalized form is known
     * @param isFromQuery whether this segment stems from the query received in the request
     * @param stemmed whether this is stemmed
     * @param origin the original text that led to this
     */
    public SegmentItem(String rawWord, String current, boolean isFromQuery, boolean stemmed, Substring origin) {
        this.rawWord = rawWord;
        this.value = current;
        this.stemmed = stemmed;
        this.isFromQuery = isFromQuery;
        isFromUser = isFromQuery;
        this.origin = origin;
    }

    public String getRawWord() {
        return rawWord;
    }

    public String getNormalized() {
        return value;
    }

    @Override
    public String stringValue() {
        return value;
    }

    @Override
    public boolean isFromQuery() {
        return isFromQuery;
    }

    @Override
    public boolean isStemmed() {
        return stemmed;
    }

    public void lock() {
        locked = true;
    }

    public boolean isLocked() {
        return locked;
    }

    @Override
    public int getNumWords() {
        return getItemCount();
    }

    @Override
    public void addItem(Item item) {
        throwIfLocked();
        super.addItem(item);
    }

    @Override
    public void addItem(int index, Item item) {
        throwIfLocked();
        super.addItem(index, item);
    }

    @Override
    public Item removeItem(int index) {
        throwIfLocked();
        return super.removeItem(index);
    }

    public boolean removeItem(Item item) {
        throwIfLocked();
        return super.removeItem(item);
    }

    private void throwIfLocked() {
        if (locked)
            throw new IllegalStateException("Cannot change change an immutable segment");
    }

    // TODO: Add a getItemIterator which is safe for immutability

    /** Return a deep copy of this object */
    @Override
    public SegmentItem clone() {
        SegmentItem copy;
        synchronized(this) {
            boolean tmpLock = locked;

            locked = false;
            copy = (SegmentItem) super.clone();
            locked = tmpLock;
            copy.locked = tmpLock;
        }
        return copy;
    }

    @Override
    public boolean isWords() {
        return true;
    }

    public boolean isFromUser() {
        return isFromUser;
    }

    public void setFromUser(boolean isFromUser) {
        this.isFromUser = isFromUser;
    }

    /** Returns null right now */
    public Substring getOrigin() {
        return origin;
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("isFromQuery", isFromQuery);
        discloser.addProperty("isFromUser", isFromUser);
        discloser.addProperty("locked", locked);
        discloser.addProperty("rawWord", rawWord);
        discloser.addProperty("stemmed", stemmed);
    }

    @Override
    public SegmentingRule getSegmentingRule() {
        return segmentingRule;
    }

    public void setSegmentingRule(SegmentingRule segmentingRule) {
        this.segmentingRule = segmentingRule;
    }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (SegmentItem)o;
        if ( ! Objects.equals(this.rawWord, other.rawWord)) return false;
        if ( ! Objects.equals(this.value, other.value)) return false;
        if ( this.isFromQuery != other.isFromQuery) return false;
        if ( this.isFromUser != other.isFromUser) return false;
        if ( this.stemmed != other.stemmed) return false;
        if ( this.segmentingRule != other.segmentingRule) return false;
        if ( ! Objects.equals(this.origin, other.origin)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rawWord, value, isFromQuery, isFromUser, stemmed, segmentingRule, origin);
    }

}
