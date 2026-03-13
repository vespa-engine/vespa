// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

class ToProtobuf {

    /** Convert sany Item to SearchProtocol.QueryTreeItem. */
    static SearchProtocol.QueryTreeItem convertFromQuery(Item item) {
        return convertFromQuery(item, new SerializationContext(1.0));
    }

    /** Converts any Item to SearchProtocol.QueryTreeItem. */
    static SearchProtocol.QueryTreeItem convertFromQuery(Item item, SerializationContext context) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot convert null item");
        }
        return item.toProtobuf(context);
    }

    static SearchProtocol.TermItemProperties buildTermProperties(Item item, String index) {
        var props = SearchProtocol.TermItemProperties.newBuilder();

        props.setIndex(index);

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
