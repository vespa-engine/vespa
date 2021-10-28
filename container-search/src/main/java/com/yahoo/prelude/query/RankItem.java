// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * Represents the rank operator, which only orders the result set and
 * does not change which hits are returned.
 *
 * The first argument is the part selecting the result set, the
 * following operands are used to order the result and does not affect
 * which hits are returned.
 *
 * @author bratseth
 */
public class RankItem extends CompositeItem {

    @Override
    public ItemType getItemType() {
        return ItemType.RANK;
    }

    @Override
    public String getName() {
        return "RANK";
    }

}
