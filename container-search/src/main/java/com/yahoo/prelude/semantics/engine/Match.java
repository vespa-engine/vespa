// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;

import java.util.Objects;

/**
 * A match
 *
 * @author bratseth
 */
public class Match {

    /** The start position of this match */
    private final int position;

    private final TermItem item;

    /** The string to replace the match by, usually item.getIndexedString() */
    private final String replaceValue;

    /** The parent of the matched item */
    private final CompositeItem parent;

    /**
     * Creates a match
     *
     * @param item the match to add
     * @param replaceValue the string to replace this match by, usually the item.getIndexedString()
     *        which is what the replace value will be if it is passed as null here
     */
    public Match(FlattenedItem item, String replaceValue) {
        this.item = item.getItem();
        if (replaceValue == null)
            this.replaceValue = item.getItem().getIndexedString();
        else
            this.replaceValue = replaceValue;
        this.parent = this.item.getParent();
        this.position = item.getPosition();
    }

    public int getPosition() { return position; }

    public TermItem getItem() { return item; }

    public String getReplaceValue() {
        return replaceValue;
    }

    /**
     * Returns the parent in which the item was matched, or null if the item was root.
     * Note that the item may subsequently have been removed, so it does not necessarily
     * have this parent
     */
    public CompositeItem getParent() { return parent; }

    /** Returns a new item representing this match */
    public Item toItem(String label) {
        return toItem(label, getReplaceValue());
    }

    /** Returns a new item representing this match */
    public Item toItem(String label, String term) {
        var newItem = new WordItem(term, label);
        newItem.setWeight(item.getWeight());
        return newItem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(item.getIndexedString(), item.getIndexName());
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof Match)) return false;

        Match other = (Match)o;
        if (other.position != position) return false;
        if ( ! other.item.equals(item)) return false;

        return true;
    }

    @Override
    public String toString() {
        return "match of " + item + " at " + position + " to be replaced by " + replaceValue;
    }

}
