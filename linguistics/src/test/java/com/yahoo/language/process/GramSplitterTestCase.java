// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.process.GramSplitter.Gram;
import com.yahoo.language.process.GramSplitter.GramSplitterIterator;
import com.yahoo.language.simple.SimpleLinguistics;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class GramSplitterTestCase {

    private static final GramSplitter gramSplitter = new SimpleLinguistics().getGramSplitter();

    @Test
    public void testNoSpaces() {
        // no spaces
        assertGramSplit("engulbillesang", 1, "[e, n, g, u, l, b, i, l, l, e, s, a, n, g]");
        assertGramSplit("engulbillesang", 2, "[en, ng, gu, ul, lb, bi, il, ll, le, es, sa, an, ng]");
        assertGramSplit("engulbillesang", 3, "[eng, ngu, gul, ulb, lbi, bil, ill, lle, les, esa, san, ang]");
    }

    @Test
    public void testWithSpaces() {
        // with spaces
        assertGramSplit("en gul bille sang", 1, "[e, n, g, u, l, b, i, l, l, e, s, a, n, g]");
        assertGramSplit("en gul bille sang", 2, "[en, gu, ul, bi, il, ll, le, sa, an, ng]");
        assertGramSplit("en gul bille sang", 3, "[en, gul, bil, ill, lle, san, ang]");
    }

    @Test
    public void testCornerCases() {
        // corner cases
        assertGramSplit("", 1, "[]");
        assertGramSplit("", 2, "[]");
        assertGramSplit("e", 1, "[e]");
        assertGramSplit("e", 2, "[e]");
        assertGramSplit("en", 1, "[e, n]");
        assertGramSplit("en", 2, "[en]");
        assertGramSplit("en", 3, "[en]");
    }

    @Test
    public void testEmojis() {
        String emoji1 = "\uD83D\uDD2A"; // 🔪
        String emoji2 = "\uD83D\uDE00"; // 😀
        assertGramSplit(emoji1, 2, "[" + emoji1+ "]");
        assertGramSplit(emoji1 + emoji2, 2, "[" + emoji1 + ", " + emoji2 + "]");
        assertGramSplit(emoji1 + "." + emoji2, 2, "[" + emoji1 + ", " + emoji2 + "]");
        assertGramSplit("." + emoji1 + "." + emoji2 + ".", 2, "[" + emoji1 + ", " + emoji2 + "]");
        assertGramSplit("foo" + emoji1 + "bar" + emoji2 + "baz", 2, "[fo, oo, " + emoji1 + ", ba, ar, " + emoji2 + ", ba, az]");
    }

    @Test
    public void testSpaceCornerCases() {
        // space corner cases
        assertGramSplit("e   en   e", 1, "[e, e, n, e]");
        assertGramSplit("e   en   e", 2, "[e, en, e]");
        assertGramSplit("e   en   e", 3, "[e, en, e]");
        assertGramSplit(" e   en   e ", 1, "[e, e, n, e]");
        assertGramSplit(" e   en   e ", 2, "[e, en, e]");
        assertGramSplit(" e   en   e ", 3, "[e, en, e]");
        assertGramSplit("  e   en   e  ", 1, "[e, e, n, e]");
        assertGramSplit("  e   en   e  ", 2, "[e, en, e]");
        assertGramSplit("  e   en   e  ", 3, "[e, en, e]");
        assertGramSplit("a  b c", 4, "[a, b, c]");
    }

    @Test
    public void testWithCasing() {
        assertGramSplit("This is the Black Eyed Peas", 2,
                        "[Th, hi, is, is, th, he, Bl, la, ac, ck, Ey, ye, ed, Pe, ea, as]");
        assertGramSplit("This is the Black Eyed Peas", 3,
                        "[Thi, his, is, the, Bla, lac, ack, Eye, yed, Pea, eas]");
        assertGramSplit("This is the Black Eyed Peas", 4,
                        "[This, is, the, Blac, lack, Eyed, Peas]");
        assertGramSplit("This is the Black Eyed Peas", 5,
                        "[This, is, the, Black, Eyed, Peas]");
        assertGramSplit("This is the Black Eyed Peas", 6,
                        "[This, is, the, Black, Eyed, Peas]");
    }

    @Test
    public void testWithPunctuation() {
        assertGramSplit("this is, in a sense, more than the sum of parts!", 2,
                        "[th, hi, is, is, in, a, se, en, ns, se, mo, or, re, th, ha, an, th, he, su, um, of, pa, ar, rt, ts]");
        assertGramSplit("this is, in a sense, more than the sum of parts!", 3,
                        "[thi, his, is, in, a, sen, ens, nse, mor, ore, tha, han, the, sum, of, par, art, rts]");
        assertGramSplit("this is, in a sense, more than the sum of parts!", 4,
                        "[this, is, in, a, sens, ense, more, than, the, sum, of, part, arts]");
        assertGramSplit("this is, in a sense, more than the sum of parts!", 5,
                        "[this, is, in, a, sense, more, than, the, sum, of, parts]");
        assertGramSplit("this is, in a sense, more than the sum of parts!", 6,
                        "[this, is, in, a, sense, more, than, the, sum, of, parts]");
    }

    @Test
    public void testAccents() {
        assertGramSplit("caf\u00e9 de l'h\u00f4tel", 2, "[ca, af, f\u00e9, de, l, h\u00f4, \u00f4t, te, el]");
        assertGramSplit("caf\u00e9 de l'h\u00f4tel", 3, "[caf, af\u00e9, de, l, h\u00f4t, \u00f4te, tel]");
        assertGramSplit("caf\u00e9 de l'h\u00f4tel", 4, "[caf\u00e9, de, l, h\u00f4te, \u00f4tel]");
        assertGramSplit("caf\u00e9 de l'h\u00f4tel", 5, "[caf\u00e9, de, l, h\u00f4tel]");
        assertGramSplit("caf\u00e9 de l'h\u00f4tel", 6, "[caf\u00e9, de, l, h\u00f4tel]");
    }

    @Test
    public void testChinese() {
        String input = "\u77f3\u5ba4\u8a69\u58eb\u65bd\u6c0f\uff0c\u55dc\u7345\uff0c\u8a93\u98df\u5341\u7345\u3002" +
                       "\u65bd\u6c0f\u6642\u6642\u9069\u5e02\u8996\u7345\uff0c\u5341\u6642\uff0c\u9069\u5341\u7345" +
                       "\u9069\u5e02\u3002";
        assertGramSplit(input, 2, "[\u77f3\u5ba4, \u5ba4\u8a69, \u8a69\u58eb, \u58eb\u65bd, \u65bd\u6c0f, " +
                                  "\u55dc\u7345, \u8a93\u98df, \u98df\u5341, \u5341\u7345, \u65bd\u6c0f, " +
                                  "\u6c0f\u6642, \u6642\u6642, \u6642\u9069, \u9069\u5e02, \u5e02\u8996, " +
                                  "\u8996\u7345, \u5341\u6642, \u9069\u5341, \u5341\u7345, \u7345\u9069, " +
                                  "\u9069\u5e02]");
        assertGramSplit(input, 3, "[\u77f3\u5ba4\u8a69, \u5ba4\u8a69\u58eb, \u8a69\u58eb\u65bd, \u58eb\u65bd\u6c0f, " +
                                  "\u55dc\u7345, \u8a93\u98df\u5341, \u98df\u5341\u7345, \u65bd\u6c0f\u6642, " +
                                  "\u6c0f\u6642\u6642, \u6642\u6642\u9069, \u6642\u9069\u5e02, \u9069\u5e02\u8996, " +
                                  "\u5e02\u8996\u7345, \u5341\u6642, \u9069\u5341\u7345, \u5341\u7345\u9069, " +
                                  "\u7345\u9069\u5e02]");
    }

    @Test
    public void testSurrogatePairs() {
        // A surrogate pair representing a code point in the "letter" class
        String s = "\uD800\uDC00";

        assertGramSplits(s, 1, s);
        assertGramSplits(s, 2, s);
        assertGramSplits(s + s, 1, s, s);
        assertGramSplits(s + s, 2, s + s);
        assertGramSplits(s + s, 3, s + s);
        assertGramSplits(s + "   " + s + s + "   " + s, 1, s, s, s, s);
        assertGramSplits(s + "   " + s + s + "   " + s, 2, s, s + s, s);
        assertGramSplits(s + "   " + s + s + "   " + s, 3, s, s + s, s);
        assertGramSplits(" " + s + "   " + s + s + "   " + s + " ", 1, s, s, s, s);
        assertGramSplits(" " + s + "   " + s + s + "   " + s + " ", 2, s, s + s, s);
        assertGramSplits(" " + s + "   " + s + s + "   " + s + " ", 3, s, s + s, s);
        assertGramSplits("  " + s + "   " + s + s + "   " + s + "  ", 1, s, s, s, s);
        assertGramSplits("  " + s + "   " + s + s + "   " + s + "  ", 2, s, s + s, s);
        assertGramSplits("  " + s + "   " + s + s + "   " + s + "  ", 3, s, s + s, s);
        assertGramSplits(s + "  " + s + " " + s, 4, s, s, s);
        assertGramSplits(s + s + s + s, 3, s + s + s, s + s + s);
        assertGramSplits(s + s + s + s + " " + s, 3, s + s + s, s + s + s, s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSplitSize() {
        gramSplitter.split("en", 0);
    }

    @Test(expected = NullPointerException.class)
    public void testInvalidSplitNull() {
        gramSplitter.split(null, 1);
    }

    @Test
    public void testUnusualIteratorUse() {
        String text = "en gul bille sang";
        Iterator<GramSplitter.Gram> grams = gramSplitter.split(text, 3);

        assertEquals("en", grams.next().extractFrom(text));
        assertTrue(grams.hasNext());
        assertTrue(grams.hasNext());
        assertEquals("gul", grams.next().extractFrom(text));
        assertEquals("bil", grams.next().extractFrom(text));
        assertEquals("ill", grams.next().extractFrom(text));
        assertEquals("lle", grams.next().extractFrom(text));
        assertTrue(grams.hasNext());
        assertTrue(grams.hasNext());
        assertEquals("san", grams.next().extractFrom(text));
        assertEquals("ang", grams.next().extractFrom(text));
        assertFalse(grams.hasNext());
        assertFalse(grams.hasNext());
    }

    @Test
    public void testLongString() {
        String input = "hey ho come 色 let's go, and then we go again!\n色色色".repeat(10_000);
        for (GramSplitterIterator grams = new GramSplitter(new CharacterClasses()).split(input, 3); grams.hasNext(); ) {
            Gram gram = grams.next();
            gram.extractFrom(input);
        }
    }

    @Test
    public void testChineseComma() {
        String text = "我喜欢红色、蓝色和紫色";
        Iterator<GramSplitter.Gram> grams = gramSplitter.split(text, 2);
        assertEquals("我喜", grams.next().extractFrom(text));
        assertEquals("喜欢", grams.next().extractFrom(text));
        assertEquals("欢红", grams.next().extractFrom(text));
        assertEquals("红色", grams.next().extractFrom(text));
        assertEquals("蓝色", grams.next().extractFrom(text));
        assertEquals("色和", grams.next().extractFrom(text));
        assertEquals("和紫", grams.next().extractFrom(text));
        assertEquals("紫色", grams.next().extractFrom(text));
    }

    @Test
    public void testEnglishComma() {
        String text = "我喜欢红色,蓝色和紫色";
        Iterator<GramSplitter.Gram> grams = gramSplitter.split(text, 2);
        assertEquals("我喜", grams.next().extractFrom(text));
        assertEquals("喜欢", grams.next().extractFrom(text));
        assertEquals("欢红", grams.next().extractFrom(text));
        assertEquals("红色", grams.next().extractFrom(text));
        assertEquals("蓝色", grams.next().extractFrom(text));
        assertEquals("色和", grams.next().extractFrom(text));
        assertEquals("和紫", grams.next().extractFrom(text));
        assertEquals("紫色", grams.next().extractFrom(text));
    }

    private void assertGramSplits(String input, int gramSize, String ... expected) {
        assertEquals(Arrays.asList(expected), gramSplitter.split(input, gramSize).toExtractedList());
    }

    private void assertGramSplit(String input, int gramSize, String expected) {
        assertEquals(expected, gramSplitter.split(input, gramSize).toExtractedList().toString());
    }

}
