// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

import java.util.Map;

/**
 * A weighted set query item to be evaluated as a sparse dot product.
 *
 * The resulting dot product will be available as a raw score in the rank framework.
 *
 * @author havardpe
 */
public class DotProductItem extends WeightedSetItem {

    public DotProductItem(String indexName) { super(indexName); }
    public DotProductItem(String indexName, Map<Object, Integer> map) { super(indexName, map); }

    @Override
    public ItemType getItemType() { return ItemType.DOTPRODUCT; }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        // Detect if we have strings or longs
        boolean hasLongs = false;
        boolean hasStrings = false;
        for (var it = getTokens(); it.hasNext();) {
            var entry = it.next();
            if (entry.getKey() instanceof Long) {
                hasLongs = true;
            } else {
                hasStrings = true;
            }
        }

        if (hasLongs && !hasStrings) {
            var builder = SearchProtocol.ItemDotProductOfLong.newBuilder();
            builder.setProperties(ToProtobuf.buildTermProperties(this));
            for (var it = getTokens(); it.hasNext();) {
                var entry = it.next();
                var weightedLong = SearchProtocol.PureWeightedLong.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue((Long) entry.getKey())
                        .build();
                builder.addWeightedLongs(weightedLong);
            }
            return SearchProtocol.QueryTreeItem.newBuilder()
                    .setItemDotProductOfLong(builder.build())
                    .build();
        } else {
            var builder = SearchProtocol.ItemDotProductOfString.newBuilder();
            builder.setProperties(ToProtobuf.buildTermProperties(this));
            for (var it = getTokens(); it.hasNext();) {
                var entry = it.next();
                var weightedString = SearchProtocol.PureWeightedString.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue(entry.getKey().toString())
                        .build();
                builder.addWeightedStrings(weightedString);
            }
            return SearchProtocol.QueryTreeItem.newBuilder()
                    .setItemDotProductOfString(builder.build())
                    .build();
        }
    }

}
