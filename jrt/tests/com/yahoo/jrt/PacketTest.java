// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PacketTest {

    @org.junit.Test
    public void testRequestPacket() {

        Values params = new Values();
        params.add(new Int32Value(123));

        Packet packet = new RequestPacket(Packet.FLAG_NOREPLY, 42,
                                          "foobar", params);
        PacketInfo info = packet.getPacketInfo();

        ByteBuffer buf = ByteBuffer.allocate(info.packetLength());
        info.encodePacket(packet, buf);
        buf.flip();

        int bytes = 12 + 4 + 6 + params.bytes();
        ByteBuffer ref = ByteBuffer.allocate(bytes);
        ref.putInt(bytes - 4);    // plen
        ref.putShort((short)2);   // flags (no reply)
        ref.putShort((short)100); // pcode (request)
        ref.putInt(42);           // reqId
        ref.putInt(6);            // method name length
        ref.put((byte)'f').put((byte)'o').put((byte)'o')
            .put((byte)'b').put((byte)'a').put((byte)'r');
        params.encode(ref);
        assertEquals(0, ref.remaining());
        ref.flip();
        assertTrue(buf.equals(ref));

        PacketInfo info2 = PacketInfo.getPacketInfo(buf);
        assertTrue(info2 != null);
        assertEquals(info2.packetLength(), buf.remaining());
        Packet packet2 = info2.decodePacket(buf);
        assertEquals(0, buf.remaining());

        assertEquals(packet2.requestId(), 42);
        assertEquals(((RequestPacket)packet2).methodName(), "foobar");
        Values params2 = ((RequestPacket)packet2).parameters();
        assertEquals(params2.size(), 1);
        assertEquals(params2.get(0).type(), Value.INT32);
        assertEquals(params2.get(0).asInt32(), 123);
    }

    @org.junit.Test
    public void testReplyPacket() {
        Values ret = new Values();
        ret.add(new Int32Value(123));

        Packet packet = new ReplyPacket(0, 42, ret);
        PacketInfo info = packet.getPacketInfo();

        ByteBuffer buf = ByteBuffer.allocate(info.packetLength());
        info.encodePacket(packet, buf);
        buf.flip();

        int bytes = 12 + ret.bytes();
        ByteBuffer ref = ByteBuffer.allocate(bytes);
        ref.putInt(bytes - 4);    // plen
        ref.putShort((short)0);   // flags
        ref.putShort((short)101); // pcode (reply)
        ref.putInt(42);           // reqId
        ret.encode(ref);
        assertEquals(0, ref.remaining());
        ref.flip();
        assertTrue(buf.equals(ref));

        PacketInfo info2 = PacketInfo.getPacketInfo(buf);
        assertTrue(info2 != null);
        assertEquals(info2.packetLength(), buf.remaining());
        Packet packet2 = info2.decodePacket(buf);
        assertEquals(0, buf.remaining());

        assertEquals(packet2.requestId(), 42);
        Values ret2 = ((ReplyPacket)packet2).returnValues();
        assertEquals(ret2.size(), 1);
        assertEquals(ret2.get(0).type(), Value.INT32);
        assertEquals(ret2.get(0).asInt32(), 123);
    }

    @org.junit.Test
    public void testErrorPacket() {
        String errStr = "NSM";
        Packet packet =
            new ErrorPacket(0, 42, ErrorCode.NO_SUCH_METHOD, errStr);
        PacketInfo info = packet.getPacketInfo();

        ByteBuffer buf = ByteBuffer.allocate(info.packetLength());
        info.encodePacket(packet, buf);
        buf.flip();

        int bytes = 12 + 4 + 4 + 3;
        ByteBuffer ref = ByteBuffer.allocate(bytes);
        ref.putInt(bytes - 4);    // plen
        ref.putShort((short)0);   // flags
        ref.putShort((short)102); // pcode (error)
        ref.putInt(42);           // reqId
        ref.putInt(ErrorCode.NO_SUCH_METHOD);
        ref.putInt(3); // length of errorMessage
        ref.put((byte)'N').put((byte)'S').put((byte)'M');
        assertEquals(0, ref.remaining());
        ref.flip();
        assertTrue(buf.equals(ref));

        PacketInfo info2 = PacketInfo.getPacketInfo(buf);
        assertTrue(info2 != null);
        assertEquals(info2.packetLength(), buf.remaining());
        Packet packet2 = info2.decodePacket(buf);
        assertEquals(0, buf.remaining());

        assertEquals(packet2.requestId(), 42);
        assertEquals(ErrorCode.NO_SUCH_METHOD,
                     ((ErrorPacket)packet2).errorCode());
        assertEquals(errStr, ((ErrorPacket)packet2).errorMessage());
    }

}
