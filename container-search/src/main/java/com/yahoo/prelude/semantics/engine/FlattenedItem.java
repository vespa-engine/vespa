// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.prelude.query.TermItem;

/**
 * An item which knows its position in its parent
 *
 * @author bratseth
 */
public class FlattenedItem {

    private TermItem item;

    /** The position of this item in its parent */
    private int position;

    public FlattenedItem(TermItem item,int position) {
        this.item = item;
        this.position = position;
    }

    public TermItem getItem() { return item; }

    public int getPosition() { return position; }

    public String toString() {
        return position + ":" + item;
    }

}
