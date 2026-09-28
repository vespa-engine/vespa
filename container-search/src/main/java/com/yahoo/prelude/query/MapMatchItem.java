// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * This class represents matching both key and value in a map.
 *
 * @author arnej
 */
public class MapMatchItem extends SameElementItem {

    private boolean frozen = false;

    public MapMatchItem(String fieldName, Item keyItem, Item valueItem) {
        super(fieldName);
        addItem(keyItem);
        addItem(valueItem);
        frozen = true;
    }

    /** Returns the item matching the key. */
    public Item keyItem() { return getItem(0); }

    /** Returns the item matching the value. */
    public Item valueItem() { return getItem(1); }

    @Override
    protected void adding(Item item) {
        super.adding(item);
        if (frozen) {
            throw new IllegalArgumentException("cannot add extra children to MapMatchItem");
        }
    }

    @Override
    public Item setItem(int index, Item item) {
        throw new UnsupportedOperationException("cannot replace children of MapMatchItem");
    }

}
