// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * This class represents matching both key and value in a map.
 *
 * @author arnej
 */
public class MapMatchItem extends SameElementItem {

    public final Item keyItem;
    public final Item valueItem;
    private boolean frozen = false;

    public MapMatchItem(String fieldName, Item keyItem, Item valueItem) {
        super(fieldName);
        this.keyItem = keyItem;
        this.valueItem = valueItem;
        addItem(keyItem);
        addItem(valueItem);
        frozen = true;
    }

    @Override
    protected void adding(Item item) {
        super.adding(item);
        if (frozen) {
            throw new IllegalArgumentException("cannot add extra children to MapMatchItem");
        }
    }
}
