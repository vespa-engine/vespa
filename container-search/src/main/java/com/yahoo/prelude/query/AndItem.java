// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

/**
 * An and'ing of a collection of sub-expressions
 *
 * @author bratseth
 */
public class AndItem extends CompositeItem {

    @Override
    public ItemType getItemType() {
        return ItemType.AND;
    }

    @Override
    public String getName() {
        return "AND";
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        var builder = SearchProtocol.ItemAnd.newBuilder();
        for (var child : items()) {
            builder.addChildren(child.toProtobuf());
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemAnd(builder.build())
                .build();
    }

}
