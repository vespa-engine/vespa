// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;

/**
 * @author Tony Vaagenes
 */
public class WordConverter extends TermConverter {
    @Override
    WordItem newTermItem(String word) {
        return new WordItem(word);
    }

    @Override
    protected String getValue(TermItem item) {
        return ((WordItem)item).getWord();
    }
}
