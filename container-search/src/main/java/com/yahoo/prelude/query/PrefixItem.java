// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * A word which matches beginnings of words instead of complete words
 *
 * @author bratseth
 */
public class PrefixItem extends WordItem {

    public PrefixItem(String prefix) {
        this(prefix, false);
    }

    public PrefixItem(String prefix, boolean isFromQuery) {
        super(prefix, isFromQuery);
    }

    public PrefixItem(String prefix, String indexName) {
        super(prefix, indexName);
    }

    public PrefixItem(String prefix, String indexName, boolean isFromQuery, Substring origin) {
        super(prefix, indexName, isFromQuery, origin);
    }

    public PrefixItem newInstance(String word, String indexName, boolean isFromQuery, Substring origin) {
        return new PrefixItem(word, indexName, isFromQuery, origin);
    }

    @Override
    public ItemType getItemType() {
        return ItemType.PREFIX;
    }

    @Override
    public String getName() {
        return "PREFIX";
    }

    @Override
    public String stringValue() {
        return getWord() + "*";
    }

}
