// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.serializer;

import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.textserialize.item.ItemExecutorRegistry;


/**
 * @author Tony Vaagenes
 */
public class QueryTreeSerializer {
    public String serialize(Item root) {
        ItemIdMapper itemIdMapper = new ItemIdMapper();
        return ItemExecutorRegistry.getByType(root.getItemType()).itemToForm(root, itemIdMapper).serialize(itemIdMapper);
    }
}
