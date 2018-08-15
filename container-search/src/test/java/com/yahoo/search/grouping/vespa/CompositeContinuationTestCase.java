// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class CompositeContinuationTestCase {

    @Test
    public void requireThatAccessorsWork() {
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
    public void requireThatCompositeContinuationsAreFlattened() {
        assertEncode("BCBCBCBEBGBCBKCBACBKCCK",
                     newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertEncode("BCBBBBBDBFBCBJBPCBJCCJ",
                     newComposite(newComposite(newOffset(-1, -1, -2, -3)), newComposite(newOffset(-5, -8, -13, -21))));
    }

    @Test
    public void requireThatEmptyStringCanBeDecoded() {
        assertDecode("", new CompositeContinuation());
    }

    @Test
    public void requireThatCompositeContinuationsCanBeDecoded() {
        assertDecode("BCBCBCBEBGBCBKCBACBKCCK",
                     newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertDecode("BCBBBBBDBFBCBJBPCBJCCJ",
                     newComposite(newOffset(-1, -1, -2, -3), newOffset(-5, -8, -13, -21)));
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(newComposite().hashCode(), newComposite().hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        CompositeContinuation cnt = newComposite();
        assertFalse(cnt.equals(new Object()));
        assertEquals(cnt, newComposite());
        assertFalse(cnt.equals(newComposite(newOffset(1, 1, 2, 3))));
        assertFalse(cnt.equals(newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21))));
        assertFalse(cnt.equals(newComposite(newOffset(5, 8, 13, 21))));

        cnt = newComposite(newOffset(1, 1, 2, 3));
        assertFalse(cnt.equals(new Object()));
        assertEquals(cnt, newComposite(newOffset(1, 1, 2, 3)));
        assertFalse(cnt.equals(newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21))));
        assertFalse(cnt.equals(newComposite(newOffset(5, 8, 13, 21))));

        cnt = newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21));
        assertFalse(cnt.equals(new Object()));
        assertFalse(cnt.equals(newComposite(newOffset(1, 1, 2, 3))));
        assertEquals(cnt, newComposite(newOffset(1, 1, 2, 3), newOffset(5, 8, 13, 21)));
        assertFalse(cnt.equals(newComposite(newOffset(5, 8, 13, 21))));
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
        public void encode(IntegerEncoder out) {

        }
    }
}
