// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.SubstringItem;

/**
 * @author Tony Vaagenes
 */
public class SubStringConverter extends WordConverter {
    @Override
    SubstringItem newTermItem(String word) {
        return new SubstringItem(word);
    }
}
