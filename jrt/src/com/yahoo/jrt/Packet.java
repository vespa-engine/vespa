// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


abstract class Packet
{
    public static final int PCODE_REQUEST = 100;
    public static final int PCODE_REPLY   = 101;
    public static final int PCODE_ERROR   = 102;

    public static final int FLAG_REVERSE  = 0x1; // bit 0
    public static final int FLAG_NOREPLY  = 0x2; // bit 1

    public static final int headerLength  = 12;

    public static boolean checkFlag(int flag, int flags) {
        return (flags & flag) != 0;
    }

    private int flags;
    private int requestId;

    public Packet(int flags, int reqId) {
        this.flags = flags;
        this.requestId = reqId;
    }

    public int requestId() {
        return requestId;
    }

    public boolean reverseByteOrder() {
        return checkFlag(FLAG_REVERSE, flags);
    }

    public boolean noReply() {
        return checkFlag(FLAG_NOREPLY, flags);
    }

    public abstract int bytes();
    public abstract int packetCode();
    public abstract void encode(ByteBuffer dst);

    public PacketInfo getPacketInfo() {
        return new PacketInfo(bytes(), flags, packetCode(), requestId);
    }
}
