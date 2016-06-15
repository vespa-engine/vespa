// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * @author balder
 */
// TODO: balder to fix javadoc
public class ExactstringItem extends WordItem {

    public ExactstringItem(String substring) {
        this(substring, false);
    }

    public ExactstringItem(String substring, boolean isFromQuery) {
        super(substring, isFromQuery);
    }

    public ItemType getItemType() {
        return ItemType.EXACT;
    }

    public String getName() {
        return "EXACTSTRING";
    }

    public String stringValue() {
        return getWord();
    }
}
