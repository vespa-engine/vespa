// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TaggableItem;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class ItemContext {
    private class Connectivity {
        final String id;
        final double strength;

        public Connectivity(String id, double strength) {
            this.id = id;
            this.strength = strength;
        }
    }

    private final Map<String, Item> itemById = new HashMap<>();
    private final Map<TaggableItem, Connectivity> connectivityByItem = new IdentityHashMap<>();


    public void setItemId(String id, Item item) {
        itemById.put(id, item);
    }

    public void setConnectivity(TaggableItem item, String id, Double strength) {
        connectivityByItem.put(item, new Connectivity(id, strength));
    }

    public void connectItems() {
        for (Map.Entry<TaggableItem, Connectivity> entry : connectivityByItem.entrySet()) {
            entry.getKey().setConnectivity(getItem(entry.getValue().id), entry.getValue().strength);
        }
    }

    private Item getItem(String id) {
        Item item = itemById.get(id);
        if (item == null)
            throw new IllegalArgumentException("No item with id '" + id + "'.");
        return item;
    }
}
