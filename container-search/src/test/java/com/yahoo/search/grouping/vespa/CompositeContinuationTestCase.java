// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class CompositeContinuationTestCase {

    @Test
    void requireThatAccessorsWork() {
        CompositeContinuation cnt = new CompositeContinuation();
        Iterator<EncodableContinuation> it = cnt.iterator();
        assertFalse(it.hasNext());

        EncodableContinuation foo = new MyContinuation();
        cnt.add(foo);
        it = cnt.iterator();
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertFalse(it.hasNext());

        EncodableContinuation bar = new MyContinuation();
        cnt.add(bar);
        it = cnt.iterator();
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertTrue(it.hasNext());
        assertSame(bar, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatCompositeContinuationsAreFlattened() {
        assertEncode("BCBCBCBEBGBCBKCBACBKCCK",
                newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertEncode("BCBBBBBDBFBCBJBPCBJCCJ",
                newComposite(newComposite(newOffset(-1, -1, -2, -3)), newComposite(newOffset(-5, -8, -13, -21))));
    }

    @Test
    void requireThatEmptyStringCanBeDecoded() {
        assertDecode("", new CompositeContinuation());
    }

    @Test
    void requireThatCompositeContinuationsCanBeDecoded() {
        assertDecode("BCBCBCBEBGBCBKCBACBKCCK",
                newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertDecode("BCBBBBBDBFBCBJBPCBJCCJ",
                newComposite(newOffset(-1, -1, -2, -3), newOffset(-5, -8, -13, -21)));
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(newComposite().hashCode(), newComposite().hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        CompositeContinuation cnt = newComposite();
        assertNotEquals(cnt, new Object());
        assertEquals(cnt, newComposite());
        assertNotEquals(cnt, newComposite(newOffset(1, 1, 2, 3)));
        assertNotEquals(cnt, newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertNotEquals(cnt, newComposite(newOffset(5, 8, 13, 21)));

        cnt = newComposite(newOffset(1, 1, 2, 3));
        assertNotEquals(cnt, new Object());
        assertEquals(cnt, newComposite(newOffset(1, 1, 2, 3)));
        assertNotEquals(cnt, newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertNotEquals(cnt, newComposite(newOffset(5, 8, 13, 21)));

        cnt = newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21));
        assertNotEquals(cnt, new Object());
        assertNotEquals(cnt, newComposite(newOffset(1, 1, 2, 3)));
        assertEquals(cnt, newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertNotEquals(cnt, newComposite(newOffset(5, 8, 13, 21)));
    }

    private static CompositeContinuation newComposite(EncodableContinuation... children) {
        CompositeContinuation ret = new CompositeContinuation();
        for (EncodableContinuation child : children) {
            ret.add(child);
        }
        return ret;
    }

    private static OffsetContinuation newOffset(int resultId, int tag, int offset, int flags) {
        return new OffsetContinuation(ResultId.valueOf(resultId), tag, offset, flags);
    }

    private static void assertEncode(String expected, EncodableContinuation toEncode) {
        IntegerEncoder actual = new IntegerEncoder();
        toEncode.encode(actual);
        assertEquals(expected, actual.toString());
    }

    private static void assertDecode(String toDecode, Continuation expected) {
        assertEquals(expected, ContinuationDecoder.decode(toDecode));
    }

    private static class MyContinuation extends EncodableContinuation {

        @Override
        public EncodableContinuation copy() {
            return null;
        }

        @Override
        public void encode(IntegerEncoder out) {

        }
    }
}
