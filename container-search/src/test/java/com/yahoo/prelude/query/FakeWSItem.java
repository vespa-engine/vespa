// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

/** A fake weighted set item for testing purposes. */
public class FakeWSItem extends CompositeIndexedItem {

    public FakeWSItem() { setIndexName("index"); }
    @Override public ItemType getItemType() { return ItemType.WEIGHTEDSET; }
    @Override public String getName() { return "WEIGHTEDSET"; }
    @Override public int getNumWords() { return 1; }
    @Override public String getIndexedString() { return ""; }
    @Override protected SearchProtocol.QueryTreeItem toProtobuf() {
        throw new UnsupportedOperationException("FakeWSItem does not support protobuf serialization");
    }

    public void add(String token, int weight) {
        WordItem w = new WordItem(token, getIndexName());
        w.setWeight(weight);
        super.addItem(w);
    }

}
