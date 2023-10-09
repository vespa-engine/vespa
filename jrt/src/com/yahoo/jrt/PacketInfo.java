// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;


class PacketInfo
{
    private int packetLength;
    private int flags;
    private int packetCode;
    private int requestId;

    private PacketInfo(ByteBuffer src) {
        packetLength = src.getInt() + 4;
        flags = src.getShort();
        packetCode = src.getShort();
        requestId = src.getInt();
    }

    PacketInfo(int plen, int flags, int pcode, int reqId) {
        this.packetLength = plen;
        this.flags = flags;
        this.packetCode = pcode;
        this.requestId = reqId;
    }

    public int packetLength() {
        return packetLength;
    }

    public int flags() {
        return flags;
    }

    public int packetCode() {
        return packetCode;
    }

    public int requestId() {
        return requestId;
    }

    public boolean reverseByteOrder() {
        return Packet.checkFlag(Packet.FLAG_REVERSE, flags);
    }

    public boolean noReply() {
        return Packet.checkFlag(Packet.FLAG_NOREPLY, flags);
    }

    public static PacketInfo getPacketInfo(ByteBuffer src) {
        if (src.remaining() < Packet.headerLength) {
            return null;
        }
        return new PacketInfo(src.slice());
    }

    public Packet decodePacket(ByteBuffer src) {
        int pos = src.position();
        int end = pos + packetLength;
        int limit = src.limit();
        try {
            src.limit(end);
            src.position(src.position() + Packet.headerLength);
            if (reverseByteOrder()) {
                src.order(ByteOrder.LITTLE_ENDIAN);
            }
            switch (packetCode) {
            case Packet.PCODE_REQUEST:
                return new RequestPacket(flags, requestId, src);
            case Packet.PCODE_REPLY:
                return new ReplyPacket(flags, requestId, src);
            case Packet.PCODE_ERROR:
                return new ErrorPacket(flags, requestId, src);
            }
            throw new IllegalArgumentException();
        } finally {
            src.order(ByteOrder.BIG_ENDIAN);
            src.position(end);
            src.limit(limit);
        }
    }

    public void encodePacket(Packet packet, ByteBuffer dst) {
        int pos = dst.position();
        int end = pos + packetLength;
        int limit = dst.limit();
        try {
            dst.limit(end);
            dst.putInt(packetLength - 4);
            dst.putShort((short)flags);
            dst.putShort((short)packetCode);
            dst.putInt(requestId);
            if (reverseByteOrder()) {
                dst.order(ByteOrder.LITTLE_ENDIAN);
            }
            packet.encode(dst);
        } catch (RuntimeException e) {
            dst.position(pos);
            throw e;
        } finally {
            dst.order(ByteOrder.BIG_ENDIAN);
            dst.limit(limit);
        }
    }
}
