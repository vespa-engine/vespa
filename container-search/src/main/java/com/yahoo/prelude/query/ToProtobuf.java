// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

public class ToProtobuf {

    /**
     * Convert any Item to SearchProtocol.QueryTreeItem
     */
    static SearchProtocol.QueryTreeItem convertFromQuery(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot convert null item");
        }
        return item.toProtobuf();
    }

    static SearchProtocol.TermItemProperties buildTermProperties(Item item) {
        var props = SearchProtocol.TermItemProperties.newBuilder();

        if (item instanceof IndexedItem indexedItem) {
            props.setIndex(indexedItem.getIndexName());
        }

        if (item.getWeight() != Item.DEFAULT_WEIGHT) {
            props.setItemWeight(item.getWeight());
        }

        if (item.hasUniqueID()) {
            props.setUniqueId(item.uniqueID);
        }

        if (!item.isRanked()) {
            props.setDoNotRank(true);
        }

        if (!item.usePositionData()) {
            props.setDoNotUsePositionData(true);
        }

        if (item.isFilter()) {
            props.setDoNotHighlight(true);
        }

        if (item.isFromSpecialToken()) {
            props.setIsSpecialToken(true);
        }

        return props.build();
    }

}
