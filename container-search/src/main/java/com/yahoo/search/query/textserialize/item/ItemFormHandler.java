// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.search.query.textserialize.parser.DispatchFormHandler;

import java.util.List;

/**
 * @author Tony Vaagenes
 */
public class ItemFormHandler implements DispatchFormHandler{
    @Override
    public Object dispatch(String name, List<Object> arguments, Object dispatchContext) {
        ItemFormConverter executor = ItemExecutorRegistry.getByName(name);
        return executor.formToItem(name, new ItemArguments(arguments), (ItemContext)dispatchContext);
    }
}
