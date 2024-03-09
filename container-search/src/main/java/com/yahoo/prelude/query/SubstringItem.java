// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * A word that matches substrings of words
 *
 * @author banino
 */
public class SubstringItem extends WordItem {

    public SubstringItem(String substring) {
        this(substring, false);
    }

    public SubstringItem(String substring, boolean isFromQuery) {
        super(substring, isFromQuery);
    }

    public SubstringItem(String substring, String indexName, boolean isFromQuery, Substring origin) {
        super(substring, indexName, isFromQuery, origin);
    }

    public SubstringItem newInstance(String word, String indexName, boolean isFromQuery, Substring origin) {
        return new SubstringItem(word, indexName, isFromQuery, origin);
    }

    @Override
    public ItemType getItemType() {
        return ItemType.SUBSTRING;
    }

    @Override
    public String getName() {
        return "SUBSTRING";
    }

    @Override
    public String stringValue() {
        return "*" + getWord() + "*";
    }

}
