// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * Ordered NearItem.
 * Matches as a near operator, but also demands that the operands have the
 * same order in the document as in the query.
 *
 * @author bratseth
 */
public class ONearItem extends NearItem {

    /** Creates a ordered NEAR item with limit 2 */
    public ONearItem() {
        setDistance(2);
    }

    /**
     * Creates a ordered near item which matches if there are at most <code>distance</code>
     * separation between the words, in the right direction.
     */
    public ONearItem(int distance) {
        super(distance);
    }

    @Override
    public ItemType getItemType() {
        return ItemType.ONEAR;
    }

    @Override
    public String getName() {
        return "ONEAR";
    }

}
