// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.BufferTooSmallException;
import com.yahoo.fs4.ErrorPacket;
import com.yahoo.fs4.PacketDecoder;
import com.yahoo.fs4.PacketDecoder.DecodedPacket;
import com.yahoo.fs4.QueryResultPacket;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the PacketDecoder
 *
 * @author Bjørn Borud
 */
public class PacketDecoderTestCase {

    static byte[] queryResultPacketData
        = new byte[] {0,0,0,104,
                      0,0,0,217-256,
                      0,0,0,1,
                      0,0,0,0,
                      0,0,0,2,
                      0,0,0,0,0,0,0,5,
                      0x40,0x39,0,0,0,0,0,0,
                      0,0,0,111,
                      0,0,0,97,
                      0,0,0,3, 1,1,1,1,1,1,1,1,1,1,1,1, 0x40,0x37,0,0,0,0,0,23, 0,0,0,7, 0,0,0,36,
                      0,0,0,4, 2,2,2,2,2,2,2,2,2,2,2,2, 0x40,0x35,0,0,0,0,0,21, 0,0,0,8, 0,0,0,37};
    static int len = queryResultPacketData.length;

    /**
     * In this testcase we have exactly one packet which fills the
     * entire buffer
     */
    @Test
    public void testOnePacket () throws BufferTooSmallException {
        ByteBuffer data = ByteBuffer.allocate(len);
        data.put(queryResultPacketData);
        data.flip();

        // not really necessary for testing, but these help visualize
        // the state the buffer should be in so a reader of this test
        // will not have to
        assertEquals(0, data.position());
        assertEquals(len, data.limit());
        assertEquals(len, data.capacity());
        assertEquals(data.limit(), data.capacity());

        PacketDecoder.DecodedPacket p = PacketDecoder.extractPacket(data);
        assertTrue(p.packet instanceof QueryResultPacket);

        // now the buffer should have position == capacity == limit
        assertEquals(len, data.position());
        assertEquals(len, data.limit());
        assertEquals(len, data.capacity());

        // next call to decode on same bufer should result
        // in null and buffer should be reset for writing.
        p = PacketDecoder.extractPacket(data);
        assertTrue(p == null);

        // make sure the buffer is now ready for reading
        assertEquals(0, data.position());
        assertEquals(len, data.limit());
        assertEquals(len, data.capacity());
    }

    /**
     * In this testcase we only have 3 bytes so we can't
     * even determine the size of the packet.
     */
    @Test
    public void testThreeBytesPacket () throws BufferTooSmallException  {
        ByteBuffer data = ByteBuffer.allocate(len);
        data.put(queryResultPacketData, 0, 3);
        data.flip();

        // packetLength() should return -1 since we don't even have
        // the size of the packet
        assertEquals(-1, PacketDecoder.packetLength(data));

        // since we can't determine the size we don't get a packet.
        // the buffer should now be at offset 3 so we can read more
        // data and limit should be set to capacity
        PacketDecoder.DecodedPacket p = PacketDecoder.extractPacket(data);
        assertTrue(p == null);
        assertEquals(3, data.position());
        assertEquals(len, data.limit());
        assertEquals(len, data.capacity());
    }

    /**
     * In this testcase we have a partial packet and room for
     * more data
     */
    @Test
    public void testPartialWithMoreRoom () throws BufferTooSmallException  {
        ByteBuffer data = ByteBuffer.allocate(len);
        data.put(queryResultPacketData, 0, 10);
        data.flip();

        PacketDecoder.DecodedPacket p = PacketDecoder.extractPacket(data);
        assertTrue(p == null);

    }

    /**
     * In this testcase we have one and a half packet
     */
    @Test
    public void testOneAndAHalfPackets () throws BufferTooSmallException {
        int half = len / 2;
        ByteBuffer data = ByteBuffer.allocate(len + half);
        data.put(queryResultPacketData);
        data.put(queryResultPacketData, 0, half);
        assertEquals((len + half), data.position());
        data.flip();

        // the first packet we should be able to extract just fine
        BasicPacket p1 = PacketDecoder.extractPacket(data).packet;
        assertTrue(p1 instanceof QueryResultPacket);

        PacketDecoder.DecodedPacket p2 = PacketDecoder.extractPacket(data);
        assertTrue(p2 == null);

        // at this point the buffer should be ready for more
        // reading so position should be at the end and limit
        // should be at capacity
        assertEquals(half, data.position());
        assertEquals(data.capacity(), data.limit());
    }

    /**
     * Test the case where the buffer is too small for the
     * packet
     */
    @Test
    public void testTooSmallBufferForPacket () {
        ByteBuffer data = ByteBuffer.allocate(10);
        data.put(queryResultPacketData, 0, 10);
        data.flip();

        try {
            PacketDecoder.extractPacket(data);
            fail();
        }
        catch (BufferTooSmallException e) {

        }
    }

    @Test
    public void testErrorPacket() throws BufferTooSmallException {
        ByteBuffer b = ByteBuffer.allocate(100);
        b.putInt(0);
        b.putInt(203);
        b.putInt(1);
        b.putInt(37);
        b.putInt(5);
        b.put(new byte[] { (byte) 'n', (byte) 'a', (byte) 'l', (byte) 'l', (byte) 'e' });
        b.putInt(0, b.position() - 4);
        b.flip();
        DecodedPacket p = PacketDecoder.extractPacket(b);
        ErrorPacket e = (ErrorPacket) p.packet;
        assertEquals("nalle (37)", e.toString());
        assertEquals(203, e.getCode());
        assertEquals(37, e.getErrorCode());
        b = ByteBuffer.allocate(100);
        // warn if encoding support is added untested
        e.encode(b);
        b.position(0);
        assertEquals(4, b.getInt());
        assertEquals(203, b.getInt());
        assertFalse(b.hasRemaining());
    }

}
