// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An item matching a uri field.
 * This is syntactic sugar for a phrase with anchor items.
 *
 * @author bratseth
 */
public class UriItem extends PhraseItem {

    // Fields here are auxiliary information not needed for semantics but used preserve a nice canonical form
    private boolean startAnchorDefault = false;
    private boolean endAnchorDefault = false;
    private String sourceString;

    public UriItem() {
        super();
    }

    public UriItem(String indexName) {
        super(indexName);
    }

    /**
     * Adds a start anchor as the *current* first item.
     * This does not enforce that the items stays at the start if further items are added.
     * */
    public void addStartAnchorItem() {
        addItem(0, MarkerWordItem.createStartOfHost(getIndexName()));
    }

    /**
     * Adds an end anchor as the *current* last item.
     * This does not enforce that the items stays at the end if further items are added.
     */
    public void addEndAnchorItem() {
        addItem(MarkerWordItem.createEndOfHost(getIndexName()));
    }

    /** Returns whether the first item of this is a start anchor */
    public boolean hasStartAnchor() {
        return getItem(0) instanceof MarkerWordItem
               && ((MarkerWordItem)getItem(0)).isStartAnchor();
    }

    /** Returns whether the last item of this is an end anchor */
    public boolean hasEndAnchor() {
        return getItem(getItemCount()-1) instanceof MarkerWordItem
               && ((MarkerWordItem)getItem(getItemCount()-1)).isEndAnchor();
    }

    public boolean isStartAnchorDefault() { return startAnchorDefault; }
    public void setStartAnchorDefault(boolean startAnchorDefault) { this.startAnchorDefault = startAnchorDefault; }

    public boolean isEndAnchorDefault() { return endAnchorDefault; }
    public void setEndAnchorDefault(boolean endAnchorDefault) { this.endAnchorDefault = endAnchorDefault; }

    public void setSourceString(String sourceString) { this.sourceString = sourceString; }

    /**
     * Returns the canonical form of the tokens of this: Either the source string, or if none
     * each token except the start and end anchor separated by space
     */
    public String getArgumentString() {
        if (sourceString != null) return sourceString;

        List<Item> items = new ArrayList<>(items());
        if (hasStartAnchor())
            items.remove(0);
        if (hasEndAnchor())
            items.remove(items.size() - 1);
        return items.stream().map(item -> ((WordItem)item).getWord()).collect(Collectors.joining(" "));
    }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (UriItem)o;
        if ( this.startAnchorDefault != other.startAnchorDefault) return false;
        if ( this.endAnchorDefault != other.endAnchorDefault) return false;
        if ( ! Objects.equals(this.sourceString, other.sourceString)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startAnchorDefault, endAnchorDefault, sourceString);
    }

}
