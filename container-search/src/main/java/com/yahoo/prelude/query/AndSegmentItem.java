// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

import java.util.Iterator;

/**
 * An immutable and'ing of a collection of sub-expressions. It does not extend
 * AndItem to avoid code using instanceof handling it as an AndItem.
 *
 * @author Steinar Knutsen
 */
public class AndSegmentItem extends SegmentItem implements BlockItem {

    public AndSegmentItem(String rawWord, boolean isFromQuery, boolean stemmed) {
        super(rawWord, rawWord, isFromQuery, stemmed, null);
    }

    public AndSegmentItem(String rawWord, String current, boolean isFromQuery, boolean stemmed) {
        super(rawWord, current, isFromQuery, stemmed, null);
    }

    public AndSegmentItem(PhraseSegmentItem item) {
        super(item.getRawWord(), item.stringValue(), item.isFromQuery(), item.isStemmed(), null);
        shouldFoldIntoWand(item.shouldFoldIntoWand());
        int weight = item.getWeight();
        if (item.getItemCount() > 0) {
            for (Iterator<Item> i = item.getItemIterator(); i.hasNext();) {
                TermItem word = (TermItem) i.next();
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

    public void setWeight(int w) {
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            i.next().setWeight(w);
        }
    }

    @Override
    protected SearchProtocol.QueryTreeItem toProtobuf() {
        // AndSegmentItem should be folded/converted before serialization
        var builder = ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.ItemAnd.newBuilder();
        for (var child : items()) {
            builder.addChildren(child.toProtobuf());
        }
        return ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.QueryTreeItem.newBuilder()
                .setItemAnd(builder.build())
                .build();
    }

}
