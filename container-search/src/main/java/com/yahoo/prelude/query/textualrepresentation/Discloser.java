// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.textualrepresentation;

import com.yahoo.prelude.query.Item;

/**
 * Allows an item to disclose its properties and children/value.
 *
 * @author tonytv
 */
public interface Discloser {
    void addProperty(String key, Object value);

    //A given item should either call setValue or addChild, not both.
    void setValue(Object value);
    void addChild(Item item);
}
