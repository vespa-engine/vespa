// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import com.yahoo.fs4.BufferTooSmallException;
import com.yahoo.fs4.GetDocSumsPacket;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.SessionId;
import com.yahoo.search.result.Hit;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests the GetDocsumsPacket
 *
 * @author Bjorn Borud
 */
public class GetDocSumsPacketTestCase {

    private static final byte IGNORE = 69;

    @Test
    public void testDefaultDocsumClass() {
        Query query = new Query("/?query=chain");
        assertNull(query.getPresentation().getSummary());
    }

    @Test
    public void testEncodingWithQuery() throws BufferTooSmallException {
        Hit hit = new FastHit();
        assertPacket(true, hit, new byte[] {0, 0, 0, 57, 0, 0, 0, -37, 0, 0, 40, 21, 0, 0, 0, 0, IGNORE, IGNORE, IGNORE,
                IGNORE, 7, 100, 101, 102, 97, 117, 108, 116, 0, 0, 0, 0x03, 0, 0, 0, 7,
                100, 101, 102, 97, 117, 108, 116, 0, 0, 0, 1, 0, 0, 0, 6, 4, 0, 3, 102, 111, 111, 0, 0, 0, 2});
    }

    @Test
    public void testEncodingWithoutQuery() throws BufferTooSmallException {
        Hit hit = new FastHit();
        assertPacket(false, hit, new byte[] { 0, 0, 0, 43, 0, 0, 0, -37, 0, 0, 40, 17, 0, 0, 0, 0, IGNORE, IGNORE, IGNORE,
                IGNORE, 7, 100, 101, 102, 97, 117, 108, 116, 0, 0, 0, 0x03, 0, 0, 0, 7, 100, 101, 102, 97, 117, 108, 116, 0, 0, 0, 2
        });
    }

    @Test
    public void requireThatSessionIdIsEncodedAsPropertyWhenUsingSearchSession() throws BufferTooSmallException {
        Result result = new Result(new Query("?query=foo"));
        SessionId sessionId = result.getQuery().getSessionId(true);  // create session id.
        result.getQuery().getRanking().setQueryCache(true);
        FastHit hit = new FastHit();
        result.hits().add(hit);
        ByteBuffer answer = ByteBuffer.allocate(1024);
        //assertEquals(0, sessionId.asUtf8String().getByteLength());
        answer.put(new byte[] { 0, 0, 0, (byte)(107+sessionId.asUtf8String().getByteLength()), 0, 0, 0, -37, 0, 0, 56, 17, 0, 0, 0, 0,
                // query timeout
                IGNORE, IGNORE, IGNORE, IGNORE,
                // "default" - rank profile
                7, 'd', 'e', 'f', 'a', 'u', 'l', 't', 0, 0, 0, 0x03,
                // "default" - summaryclass
                0, 0, 0, 7, 'd', 'e', 'f', 'a', 'u', 'l', 't',
                // 2 property entries
                0, 0, 0, 2,
                // rank: sessionId => qrserver.0.XXXXXXXXXXXXX.0
                0, 0, 0, 4, 'r', 'a', 'n', 'k', 0, 0, 0, 1, 0, 0, 0, 9, 's', 'e', 's', 's', 'i', 'o', 'n', 'I', 'd'});
        answer.putInt(sessionId.asUtf8String().getByteLength());
        answer.put(sessionId.asUtf8String().getBytes());
        answer.put(new byte [] {
                // caches: features => true
                0, 0, 0, 6, 'c', 'a', 'c', 'h', 'e', 's',
                0, 0, 0, 1, 0, 0, 0, 5, 'q', 'u', 'e', 'r', 'y', 0, 0, 0, 4, 't', 'r', 'u', 'e',
                // flags
                0, 0, 0, 2});
        byte [] expected = new byte [answer.position()];
        answer.flip();
        answer.get(expected);
        assertPacket(false, result, expected);
    }

    private static void assertPacket(boolean sendQuery, Hit hit, byte[] expected) throws BufferTooSmallException {
        Result result = new Result(new Query("?query=foo"));
        result.hits().add(hit);
        assertPacket(sendQuery, result, expected);
    }

    private static void assertPacket(boolean sendQuery, Result result, byte[] expected) throws BufferTooSmallException {
        GetDocSumsPacket packet = GetDocSumsPacket.create(result, "default", sendQuery);
        ByteBuffer buf = ByteBuffer.allocate(1024);
        packet.encode(buf);
        buf.flip();

        byte[] actual = new byte[buf.remaining()];
        buf.get(actual);
        // assertEquals(Arrays.toString(expected), Arrays.toString(actual));

        assertEquals("Equal length", expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) {
            if (expected[i] == IGNORE) {
                actual[i] = IGNORE;
            }
        }

        assertArrayEquals(expected, actual);
    }
}
