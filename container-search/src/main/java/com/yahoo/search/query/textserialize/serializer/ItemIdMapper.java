// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.serializer;

import com.yahoo.prelude.query.Item;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class ItemIdMapper {
    private final Map<Item, String> idByItem = new IdentityHashMap<>();
    private int idCounter = 0;

    public String getId(Item item) {
        String id = idByItem.get(item);
        if (id != null) {
            return id;
        } else {
            idByItem.put(item, generateId(item));
            return getId(item);
        }
    }

    private String generateId(Item item) {
        return item.getName() + "_" + nextCount();
    }

    private int nextCount() {
        return idCounter++;
    }
}
