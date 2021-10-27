// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * An or'ing of a collection of sub-expressions
 *
 * @author bratseth
 */
public class OrItem extends CompositeItem {

    @Override
    public ItemType getItemType() {
        return ItemType.OR;
    }

    @Override
    public String getName() {
        return "OR";
    }

}
