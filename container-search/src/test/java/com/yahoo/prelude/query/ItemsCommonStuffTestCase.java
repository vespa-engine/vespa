// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;
import com.yahoo.prelude.query.Item.ItemType;

/**
 * Check basic contracts common to "many" item implementations.
 *
 * @author Steinar Knutsen
 */
public class ItemsCommonStuffTestCase {

    @Test
    void testLoops() {
        AndSegmentItem as = new AndSegmentItem("farmyards", false, false);
        boolean caught = false;
        try {
            as.addItem(as);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        AndItem a = new AndItem();
        caught = false;
        try {
            a.addItem(a);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        a.addItem(as);
        try {
            as.addItem(a);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        a.removeItem(as);
        as.addItem(a);
        try {
            a.addItem(as);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    void testIndexName() {
        WordItem w = new WordItem("nalle");
        AndItem a = new AndItem();
        a.addItem(w);
        String expected = "mobil";
        a.setIndexName(expected);
        assertEquals(expected, w.getIndexName());
    }

    @Test
    void testBoundaries() {
        WordItem w = new WordItem("nalle");
        AndItem a = new AndItem();
        boolean caught = false;
        try {
            a.addItem(-1, w);
        } catch (IndexOutOfBoundsException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            a.addItem(1, w);
        } catch (IndexOutOfBoundsException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            a.setItem(-1, w);
        } catch (IndexOutOfBoundsException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            a.setItem(0, w);
        } catch (IndexOutOfBoundsException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    void testRemoving() {
        AndItem other = new AndItem();
        WordItem w = new WordItem("nalle");
        AndItem a = new AndItem();
        WordItem v = new WordItem("bamse");
        v.setParent(other);
        a.addItem(w);
        assertFalse(a.removeItem(null));
        assertTrue(a.removeItem(w));
        assertNull(w.getParent());
        a.removeItem(v);
        assertSame(other, v.getParent());
    }

    @Test
    void testGeneralMutability() {
        AndItem a = new AndItem();
        assertFalse(a.isLocked());
        a.lock();
        assertFalse(a.isLocked());
    }

    @Test
    void testCounting() {
        WordItem w = new WordItem("nalle");
        AndItem a = new AndItem();
        WordItem v = new WordItem("bamse");
        AndItem other = new AndItem();
        assertEquals(0, a.getTermCount());
        a.addItem(w);
        assertEquals(1, a.getTermCount());
        other.addItem(v);
        a.addItem(other);
        assertEquals(2, a.getTermCount());
    }

    @Test
    void testIteratorJuggling() {
        AndItem a = new AndItem();
        WordItem w0 = new WordItem("nalle");
        WordItem w1 = new WordItem("bamse");
        WordItem w2 = new WordItem("teddy");
        boolean caught = false;
        a.addItem(w0);
        a.addItem(w1);
        ListIterator<Item> i = a.getItemIterator();
        assertFalse(i.hasPrevious());
        try {
            i.previous();
        } catch (NoSuchElementException e) {
            caught = true;
        }
        assertTrue(caught);
        assertEquals(-1, i.previousIndex());
        assertEquals(0, i.nextIndex());
        i.next();
        WordItem wn = (WordItem) i.next();
        assertSame(w1, wn);
        assertSame(w1, i.previous());
        assertSame(w0, i.previous());
        assertEquals(0, i.nextIndex());
        i.add(w2);
        assertEquals(1, i.nextIndex());
    }

    @Test
    void testIdStuff() {
        Item i;
        String expected = "i";
        i = new ExactStringItem(expected);
        assertEquals(ItemType.EXACT, i.getItemType());
        assertEquals("EXACTSTRING", i.getName());
        assertEquals(expected, ((ExactStringItem) i).stringValue());
        i = new PrefixItem("p");
        assertEquals(ItemType.PREFIX, i.getItemType());
        assertEquals("PREFIX", i.getName());
        i = new SubstringItem("p");
        assertEquals(ItemType.SUBSTRING, i.getItemType());
        assertEquals("SUBSTRING", i.getName());
        i = new SuffixItem("p");
        assertEquals(ItemType.SUFFIX, i.getItemType());
        assertEquals("SUFFIX", i.getName());
        i = new WeightedSetItem("nalle");
        assertEquals(ItemType.WEIGHTEDSET, i.getItemType());
        assertEquals("WEIGHTEDSET", i.getName());
        i = new AndSegmentItem("", false, false);
        assertEquals(ItemType.AND, i.getItemType());
        assertEquals("SAND", i.getName());
        i = new WeakAndItem();
        assertEquals(ItemType.WEAK_AND, i.getItemType());
        assertEquals("WEAKAND", i.getName());
    }

    @Test
    void testEquivBuilding() {
        WordItem w = new WordItem("nalle");
        WordItem v = new WordItem("bamse");
        w.setConnectivity(v, 1.0);
        EquivItem e = new EquivItem(w);
        assertEquals(1.0, e.getConnectivity(), 1e-9);
        assertSame(v, e.getConnectedItem());
    }

    @Test
    final void testEquivBuildingFromCollection() {
        WordItem w = new WordItem("nalle");
        WordItem v = new WordItem("bamse");
        w.setConnectivity(v, 1.0);
        String expected = "puppy";
        String expected2 = "kvalp";
        EquivItem e = new EquivItem(w, Arrays.asList(expected, expected2));
        assertEquals(1.0, e.getConnectivity(), 1e-9);
        assertSame(v, e.getConnectedItem());
        assertEquals(expected, ((WordItem) e.getItem(1)).getWord());
        assertEquals(expected2, ((WordItem) e.getItem(2)).getWord());
    }

    @Test
    void testSegment() {
        AndSegmentItem as = new AndSegmentItem("farmyards", false, false);
        assertFalse(as.isLocked());
        WordItem firstItem = new WordItem("nalle");
        as.addItem(firstItem);
        WordItem item = new WordItem("bamse");
        as.addItem(1, item);
        assertTrue(as.removeItem(item));
        assertFalse(as.isFromUser());
        as.setFromUser(true);
        assertTrue(as.isFromUser());
        as.lock();
        try {
            as.removeItem(firstItem);
            fail("Excpected exception");
        } catch (IllegalStateException e) {
            // success
        }
        try {
            as.addItem(new WordItem("puppy"));
            fail("Excpected exception");
        } catch (IllegalStateException e) {
            // success
        }
        try {
            as.addItem(1, new WordItem("kvalp"));
            fail("Excpected exception");
        } catch (IllegalStateException e) {
            // success
        }
    }

    @Test
    void testMarkersVsWords() {
        WordItem mw0 = MarkerWordItem.createEndOfHost();
        WordItem mw1 = MarkerWordItem.createStartOfHost();
        WordItem w0 = new WordItem("$");
        WordItem w1 = new WordItem("^");
        assertEquals(w0.getWord(), mw0.getWord());
        assertEquals(w1.getWord(), mw1.getWord());
        assertNotEquals(mw0, w0);
        assertEquals(mw0, MarkerWordItem.createEndOfHost());
        assertNotNull(mw1.hashCode());
    }

    @Test
    void testNumberBasics() {
        String expected = "12";
        IntItem i = new IntItem(expected, "num");
        assertEquals(expected, i.stringValue());
        String expected2 = "34";
        i.setNumber(expected2);
        assertEquals(expected2, i.stringValue());
        String expected3 = "56";
        i.setValue(expected3);
        assertEquals(expected3, i.stringValue());
        assertTrue(i.isStemmed());
        assertFalse(i.isWords());
        assertEquals(1, i.getNumWords());
        assertNotEquals(i, new IntItem(expected3));
        assertEquals(i, new IntItem(expected3, "num"));
    }

    @Test
    void testNullItemFailsProperly() {
        NullItem n = new NullItem();
        n.setIndexName("nalle");
        boolean caught = false;
        try {
            n.encode(ByteBuffer.allocate(100));
        } catch (RuntimeException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            n.getItemType();
        } catch (RuntimeException e) {
            caught = true;
        }
        assertTrue(caught);
        assertEquals(0, n.getTermCount());
    }

    private void fill(CompositeItem c) {
        for (String w : new String[] { "nalle", "bamse", "teddy" }) {
            c.addItem(new WordItem(w));
        }
    }

    @Test
    void testNearisNotAnd() {
        AndItem a = new AndItem();
        NearItem n = new NearItem();
        n.setDistance(2);
        NearItem n2 = new NearItem();
        n2.setDistance(2);
        NearItem n3 = new NearItem();
        n3.setDistance(3);
        fill(a);
        fill(n);
        fill(n2);
        fill(n3);
        assertNotNull(n.hashCode());
        assertNotEquals(n, a);
        assertEquals(n, n2);
        assertNotEquals(n, n3);
    }

    @Test
    void testPhraseSegmentBasics() {
        AndSegmentItem a = new AndSegmentItem("gnurk", "gurk", false, false);
        fill(a);
        a.lock();
        PhraseSegmentItem p = new PhraseSegmentItem(a);
        assertEquals("SPHRASE", p.getName());
        p.addItem(new WordItem("blbl"));
        boolean caught = false;
        try {
            p.addItem(new AndItem());
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        assertEquals("blbl", p.getWordItem(3).getWord());
        ByteBuffer b = ByteBuffer.allocate(5000);
        int i = p.encode(b);
        assertEquals(5, i);
        assertEquals("nalle bamse teddy blbl", p.getIndexedString());
    }

    @Test
    void testPhraseConnectivity() {
        WordItem w = new WordItem("a");
        PhraseItem p = new PhraseItem();
        fill(p);
        p.setConnectivity(w, 500.0d);
        assertEquals(500.0d, p.getConnectivity(), 1e-9);
        assertSame(w, p.getConnectedItem());
    }

    @Test
    void testBaseClassPhraseSegments() {
        PhraseSegmentItem p = new PhraseSegmentItem("g", false, true);
        fill(p);
        assertEquals(4, p.encode(ByteBuffer.allocate(5000)));
        p.setIndexName(null);
        assertEquals("", p.getIndexName());
        PhraseSegmentItem p2 = new PhraseSegmentItem("g", false, true);
        fill(p2);
    }

    @Test
    void testTermTypeBasic() {
        assertNotEquals(TermType.AND, TermType.DEFAULT);
        assertNotEquals(TermType.AND, Integer.valueOf(10));
        assertEquals(TermType.AND, TermType.AND);
        assertSame(AndItem.class, TermType.DEFAULT.createItemClass().getClass());
        assertSame(CompositeItem.class, TermType.DEFAULT.getItemClass());
        assertNotNull(TermType.PHRASE.hashCode());
        assertEquals("term type 'not'", TermType.NOT.toString());
    }

    @Test
    void testRegexp() {
        RegExpItem empty = new RegExpItem("a", true, "");
        assertTrue(empty.isFromQuery());
        assertTrue(empty.isStemmed());
        assertEquals("a", empty.getIndexName());
        assertEquals("", empty.getIndexedString());
        assertEquals(1, empty.getNumWords());
        assertEquals(ItemType.REGEXP, empty.getItemType());

        assertEquals("a", new RegExpItem("i", true, "a").getIndexedString());
        assertEquals(".*", new RegExpItem("i", true, ".*").getIndexedString());
        PatternSyntaxException last = null;
        try {
            assertEquals(0, new RegExpItem("i", true, "*").getNumWords());
        } catch (PatternSyntaxException e) {
            last = e;
        }
        assertEquals("Dangling meta character '*' near index 0\n" + "*\n" + "^", last.getMessage());
    }

}

