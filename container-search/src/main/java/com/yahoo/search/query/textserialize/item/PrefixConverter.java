// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.PrefixItem;

/**
 * @author Tony Vaagenes
 */
public class PrefixConverter extends WordConverter {
    @Override
    PrefixItem newTermItem(String word) {
        return new PrefixItem(word);
    }
}
