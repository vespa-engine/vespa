// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class OffsetContinuationTestCase {

    @Test
    void requireThatNullResultIdThrowsException() {
        try {
            new OffsetContinuation(null, 0, 0, 0);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatAccessorsWork() {
        OffsetContinuation cnt = new OffsetContinuation(ResultId.valueOf(1), 2, 3, 4);
        assertEquals(ResultId.valueOf(1), cnt.getResultId());
        assertEquals(2, cnt.getTag());
        assertEquals(3, cnt.getOffset());
        assertEquals(4, cnt.getFlags());

        cnt = new OffsetContinuation(ResultId.valueOf(5), 6, 7, 8);
        assertEquals(ResultId.valueOf(5), cnt.getResultId());
        assertEquals(6, cnt.getTag());
        assertEquals(7, cnt.getOffset());
        assertEquals(8, cnt.getFlags());

        for (int i = 0; i < 30; ++i) {
            cnt = new OffsetContinuation(ResultId.valueOf(1), 2, 3, (1 << i) + (1 << i + 1));
            assertTrue(cnt.testFlag(1 << i));
            assertTrue(cnt.testFlag(1 << i + 1));
            assertFalse(cnt.testFlag(1 << i + 2));
        }
    }

    @Test
    void requireThatOffsetContinuationsCanBeEncoded() {
        assertEncode("BCBCBCBEBG", newOffset(1, 1, 2, 3));
        assertEncode("BCBKCBACBKCCK", newOffset(5, 8, 13, 21));
        assertEncode("BCBBBBBDBF", newOffset(-1, -1, -2, -3));
        assertEncode("BCBJBPCBJCCJ", newOffset(-5, -8, -13, -21));
    }

    @Test
    void requireThatOffsetContinuationsCanBeDecoded() {
        assertDecode("BCBCBCBEBG", newOffset(1, 1, 2, 3));
        assertDecode("BCBKCBACBKCCK", newOffset(5, 8, 13, 21));
        assertDecode("BCBBBBBDBF", newOffset(-1, -1, -2, -3));
        assertDecode("BCBJBPCBJCCJ", newOffset(-5, -8, -13, -21));
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(newOffset(1, 1, 2, 3).hashCode(), newOffset(1, 1, 2, 3).hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        Continuation cnt = newOffset(1, 1, 2, 3);
        assertNotEquals(cnt, new Object());
        assertNotEquals(cnt, newOffset(0, 1, 2, 3));
        assertNotEquals(cnt, newOffset(1, 0, 2, 3));
        assertNotEquals(cnt, newOffset(1, 1, 0, 3));
        assertNotEquals(cnt, newOffset(1, 1, 2, 0));
        assertEquals(cnt, newOffset(1, 1, 2, 3));
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
        assertEquals(expected, OffsetContinuation.decode(new IntegerDecoder(toDecode)));
    }
}
