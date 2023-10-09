// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.prelude.query.ExactStringItem;

/**
 * @author baldersheim
 */

public class ExactStringConverter extends WordConverter {
    @Override
    ExactStringItem newTermItem(String word) {
        return new ExactStringItem(word);
    }
}
