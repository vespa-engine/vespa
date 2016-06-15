// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.ExactstringItem;

/**
 * @author balder
 */
// TODO: balder to fix javadoc
public class ExactStringConverter extends WordConverter {
    @Override
    ExactstringItem newTermItem(String word) {
        return new ExactstringItem(word);
    }
}
