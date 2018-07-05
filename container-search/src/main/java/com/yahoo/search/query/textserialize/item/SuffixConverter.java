// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.SuffixItem;

/**
 * @author Tony Vaagenes
 */
public class SuffixConverter extends WordConverter {
    @Override
    SuffixItem newTermItem(String word) {
        return new SuffixItem(word);
    }
}
