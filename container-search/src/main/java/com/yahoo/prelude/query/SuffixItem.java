// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

/**
 * A word that matches a suffix of words instead of a complete word.
 *
 * @author Steinar Knutsen
 */
public class SuffixItem extends WordItem {

    public SuffixItem(String suffix) {
        this(suffix, false);
    }

    public SuffixItem(String suffix, boolean isFromQuery) {
        super(suffix, isFromQuery);
    }

    @Override
    public ItemType getItemType() {
        return ItemType.SUFFIX;
    }

    @Override
    public String getName() {
        return "SUFFIX";
    }

    @Override
    public String stringValue() {
        return "*" + getWord();
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        var builder = SearchProtocol.ItemSuffixTerm.newBuilder();
        builder.setProperties(ToProtobuf.buildTermProperties(this));
        builder.setWord(getWord());
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemSuffixTerm(builder.build())
                .build();
    }

}
