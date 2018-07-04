// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.TermItem;

/**
 * @author Tony Vaagenes
 */
public class IntConverter extends TermConverter {
    @Override
    IntItem newTermItem(String word) {
        return new IntItem(word);
    }

    @Override
    protected String getValue(TermItem item) {
        return ((IntItem)item).getNumber();
    }
}
