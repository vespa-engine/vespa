// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import org.junit.jupiter.api.Test;
import com.yahoo.prelude.query.PhraseSegmentItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.yahoo.prelude.query.WordItem;

/**
 * Functional test for the logic in items made up from a single block of text.
 *
 * @author steinar
 */
public class SegmentItemTestCase {

    @Test
    final void test() {
        PhraseSegmentItem item = new PhraseSegmentItem("a b c", false, true);
        item.addItem(new WordItem("a"));
        item.addItem(new WordItem("b"));
        item.addItem(new WordItem("c"));
        assertEquals(100, item.getItem(0).getWeight());
        item.setWeight(150);
        assertEquals(150, item.getItem(0).getWeight());
        assertEquals(item.getItem(0).getWeight(), item.getItem(1).getWeight());
        assertEquals(item.getItem(0).getWeight(), item.getItem(2).getWeight());
    }

}
