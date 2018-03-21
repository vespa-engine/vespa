// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test methods changing phrase items.
 *
 * @author Steinar Knutsen
 */
public class PhraseItemTestCase {

    @Test
    public void testAddItem() {
        PhraseItem p = new PhraseItem();
        PhraseSegmentItem pp = new PhraseSegmentItem("", false, false);
        PhraseItem ppp = new PhraseItem();
        pp.addItem(new WordItem("b"));
        pp.addItem(new WordItem("c"));
        ppp.addItem(new WordItem("e"));
        ppp.addItem(new WordItem("f"));
        p.addItem(new WordItem("a"));
        p.addItem(pp);
        p.addItem(new WordItem("d"));
        p.addItem(ppp);
        assertEquals("\"a 'b c' d e f\"", p.toString());
    }

    @Test
    public void testAddItemWithIndex() {
        PhraseItem p = new PhraseItem();
        PhraseSegmentItem pp = new PhraseSegmentItem("", false, false);
        PhraseItem ppp = new PhraseItem();
        pp.addItem(new WordItem("a"));
        pp.addItem(new WordItem("b"));
        ppp.addItem(new WordItem("c"));
        ppp.addItem(new WordItem("d"));
        p.addItem(0, new WordItem("e"));
        p.addItem(0, pp);
        p.addItem(2, new WordItem("f"));
        p.addItem(1, ppp);
        assertEquals("\"'a b' c d e f\"", p.toString());
    }

    @Test
    public void testSetItem() {
        PhraseItem backup = new PhraseItem();
        PhraseSegmentItem segment = new PhraseSegmentItem("", false, false);
        PhraseItem innerPhrase = new PhraseItem();
        WordItem testWord = new WordItem("z");
        PhraseItem p;
        segment.addItem(new WordItem("p"));
        segment.addItem(new WordItem("q"));
        innerPhrase.addItem(new WordItem("x"));
        innerPhrase.addItem(new WordItem("y"));
        backup.addItem(new WordItem("a"));
        backup.addItem(new WordItem("b"));
        backup.addItem(new WordItem("c"));

        p = (PhraseItem) backup.clone();
        p.setItem(0, segment);
        assertEquals("\"'p q' b c\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(1, segment);
        assertEquals("\"a 'p q' c\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(2, segment);
        assertEquals("\"a b 'p q'\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(0, innerPhrase);
        assertEquals("\"x y b c\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(1, innerPhrase);
        assertEquals("\"a x y c\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(2, innerPhrase);
        assertEquals("\"a b x y\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(0, testWord);
        assertEquals("\"z b c\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(1, testWord);
        assertEquals("\"a z c\"", p.toString());

        p = (PhraseItem) backup.clone();
        p.setItem(2, testWord);
        assertEquals("\"a b z\"", p.toString());
    }

}
