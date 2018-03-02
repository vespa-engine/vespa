// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.yahoo.fs4.*;
import com.yahoo.search.Query;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the Packet class.  Specifically made this unit test suite
 * for checking that queries that are too large for the buffer
 * are handled gracefully.
 *
 * @author Bjorn Borud
 */
public class PacketTestCase {

    /**
     * Make sure we don't get false negatives for reasonably sized
     * buffers
     */
    @Test
    public void testSmallQueryOK () {
        Query query = new Query("/?query=foo");
        assertNotNull(query);

        QueryPacket queryPacket = QueryPacket.create(query);
        assertNotNull(queryPacket);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int position = buffer.position();

        try {
            queryPacket.encode(buffer, 0);
        }
        catch (BufferTooSmallException e) {
            fail();
        }

        // make sure state of buffer HAS changed and is according
        // to contract
        assertTrue(position != buffer.position());
        assertTrue(buffer.position() == buffer.limit());
    }

    /**
     * Make a query that is too large and then try to encode it
     * into a small ByteBuffer
     */
    @Test
    public void testLargeQueryFail () {
        StringBuilder queryBuffer = new StringBuilder(4008);
        queryBuffer.append("/?query=");
        for (int i=0; i < 1000; i++) {
            queryBuffer.append("the%20");
        }
        Query query = new Query(queryBuffer.toString());
        assertNotNull(query);

        QueryPacket queryPacket = QueryPacket.create(query);
        assertNotNull(queryPacket);

        ByteBuffer buffer = ByteBuffer.allocate(100);
        int position = buffer.position();
        int limit    = buffer.limit();
        try {
            queryPacket.encode(buffer, 0);
            fail();
        }
        catch (BufferTooSmallException e) {
            // success if exception is thrown
        }

        // make sure state of buffer is unchanged
        assertEquals(position, buffer.position());
        assertEquals(limit, buffer.limit());
    }

    @Test
    public void requireThatPacketsCanTurnOnCompression() throws BufferTooSmallException {
        QueryPacket queryPacket = QueryPacket.create(new Query("/?query=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int channel = 32;

        queryPacket.encode(buffer, channel);
        buffer.flip();
        assertEquals(86, buffer.getInt());  // size
        assertEquals(0xda, buffer.getInt());  // code
        assertEquals(channel, buffer.getInt());

        queryPacket.setCompressionLimit(88);
        buffer.clear();
        queryPacket.encode(buffer, channel);
        buffer.flip();
        assertEquals(86, buffer.getInt());  // size
        assertEquals(0xda, buffer.getInt());  // code

        queryPacket.setCompressionLimit(84);
        buffer.clear();
        queryPacket.encode(buffer, channel);
        buffer.flip();
        assertEquals(57, buffer.getInt());  // size
        assertEquals(0x060000da, buffer.getInt());  // code
        assertEquals(channel, buffer.getInt());
    }

    @Test
    public void requireThatUncompressablePacketsArentCompressed() throws BufferTooSmallException {
        QueryPacket queryPacket = QueryPacket.create(new Query("/?query=aaaaaaaaaaaaaaa"));
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int channel = 32;

        queryPacket.setCompressionLimit(10);
        buffer.clear();
        queryPacket.encode(buffer, channel);
        buffer.flip();
        assertEquals(56, buffer.getInt());  // size
        assertEquals(0xda, buffer.getInt());  // code
        assertEquals(channel, buffer.getInt());
    }

    class MyPacket extends Packet {
        private String bodyString = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        private int myCode = 1234;

        @Override
        public int getCode() {
            return myCode;
        }

        @Override
        protected void encodeBody(ByteBuffer buffer) {
            buffer.put(bodyString.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void codeDecodedHook(int code) {
            assertEquals(myCode, code);
        }

        @Override
        public void decodeBody(ByteBuffer buffer) {
            byte[] bytes = new byte[bodyString.length()];
            buffer.get(bytes);
            assertEquals(bodyString, new String(bytes));
        }
    }

    @Test
    public void requireThatCompressedPacketsCanBeDecompressed() throws BufferTooSmallException {

        MyPacket packet = new MyPacket();
        packet.setCompressionLimit(10);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int channel = 32;
        packet.encode(buffer, channel);

        buffer.flip();
        new MyPacket().decode(buffer);
    }

    @Test
    public void requireThatCompressedByteBufferMayContainExtraData() throws BufferTooSmallException {

        MyPacket packet = new MyPacket();
        packet.setCompressionLimit(10);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putLong(0xdeadbeefL);
        int channel = 32;
        packet.encode(buffer, channel);
        buffer.limit(buffer.limit() + 8);
        buffer.putLong(0xdeadbeefL);

        buffer.flip();
        assertEquals(0xdeadbeefL, buffer.getLong());  // read initial content.
        new MyPacket().decode(buffer);
        assertEquals(0xdeadbeefL, buffer.getLong());  // read final content.
    }

    class MyBasicPacket extends BasicPacket {
        private String bodyString = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        private int myCode = 1234;

        @Override
        public int getCode() {
            return myCode;
        }

        @Override
        protected void encodeBody(ByteBuffer buffer) {
            buffer.put(bodyString.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void codeDecodedHook(int code) {
            assertEquals(myCode, code);
        }

        @Override
        public void decodeBody(ByteBuffer buffer) {
            byte[] bytes = new byte[bodyString.length()];
            buffer.get(bytes);
            assertEquals(bodyString, new String(bytes));
        }
    }

    @Test
    public void requireThatCompressedBasicPacketsCanBeDecompressed() throws BufferTooSmallException {

        MyBasicPacket packet = new MyBasicPacket();
        packet.setCompressionLimit(10);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        packet.encode(buffer);

        buffer.flip();
        new MyBasicPacket().decode(buffer);
    }

}
