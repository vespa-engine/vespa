// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

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

    @Override
    protected SearchProtocol.QueryTreeItem toProtobuf() {
        var builder = SearchProtocol.ItemOr.newBuilder();
        for (var child : items()) {
            builder.addChildren(child.toProtobuf());
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemOr(builder.build())
                .build();
    }

}
