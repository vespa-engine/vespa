// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Iterator;

/**
 * An immutable and'ing of a collection of sub-expressions. It does not extend
 * AndItem to avoid code using instanceof handling it as an AndItem.
 *
 * @author Steinar Knutsen
 */
public class AndSegmentItem extends IndexedSegmentItem implements BlockItem {

    public AndSegmentItem(String rawWord, boolean isFromQuery, boolean stemmed) {
        super(rawWord, rawWord, isFromQuery, stemmed, null);
    }

    public AndSegmentItem(String rawWord, String current, boolean isFromQuery, boolean stemmed) {
        super(rawWord, current, isFromQuery, stemmed, null);
    }

    public AndSegmentItem(PhraseSegmentItem item) {
        super(item.getRawWord(), item.stringValue(), item.isFromQuery(), item.isStemmed(), null);
        int weight = item.getWeight();
        if (item.getItemCount() > 0) {
            for (Iterator<Item> i = item.getItemIterator(); i.hasNext();) {
                WordItem word = (WordItem) i.next();
                word.setWeight(weight);
                addItem(word);
            }
        }
    }

    @Override
    public ItemType getItemType() {
        return ItemType.AND;
    }

    @Override
    public String getName() {
        return "SAND";
    }

    @Override
    public String getIndexName() {
        if (getItemCount() == 0) {
            return "";
        } else {
            return ((IndexedItem) getItem(0)).getIndexName();
        }
    }

    /**
     * Adds a word subitem. The word will have its index name set to the index name of this phrase.
     *
     * @throws IllegalArgumentException if the given item is not a WordItem
     */
    @Override
    public void addItem(Item item) {
        if (item instanceof WordItem) {
            addWordItem((WordItem) item);
        } else {
            throw new IllegalArgumentException("Can not add " + item + " to a segment phrase");
        }
    }

    private void addWordItem(WordItem word) {
        word.setIndexName(this.getIndexName());
        super.addItem(word);
    }

    @Override
    public void setIndexName(String index) {
        super.setIndexName(index);
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            WordItem word = (WordItem) i.next();
            word.setIndexName(index);
        }
    }

    // TODO: Is it necessary to override equals?

    @Override
    public String getIndexedString() {
        StringBuilder b = new StringBuilder();

        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            IndexedItem indexedItem = (IndexedItem) i.next();

            b.append(indexedItem.getIndexedString());
            if (i.hasNext()) {
                b.append(' ');
            }
        }
        return b.toString();
    }

    public void setWeight(int w) {
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            i.next().setWeight(w);
        }
    }

}
