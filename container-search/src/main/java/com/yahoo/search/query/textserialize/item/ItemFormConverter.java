// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.textserialize.serializer.DispatchForm;
import com.yahoo.search.query.textserialize.serializer.ItemIdMapper;

/**
 * @author Tony Vaagenes
 */
public interface ItemFormConverter {
    Object formToItem(String name, ItemArguments arguments, ItemContext context);
    DispatchForm itemToForm(Item item, ItemIdMapper itemIdMapper);
}
