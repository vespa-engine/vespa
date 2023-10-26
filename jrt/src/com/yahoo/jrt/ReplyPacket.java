// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


class ReplyPacket extends Packet
{
    private Values returnValues;

    public ReplyPacket(int flags, int reqId,
                       Values returnValues)
    {
        super(flags, reqId);
        this.returnValues = returnValues;
    }

    public ReplyPacket(int flags, int reqId,
                       ByteBuffer src)
    {
        super(flags, reqId);
        returnValues = new Values(src);
    }

    public int bytes() {
        return (headerLength +
                returnValues.bytes());
    }

    public int packetCode() {
        return PCODE_REPLY;
    }

    public void encode(ByteBuffer dst) {
        returnValues.encode(dst);
    }

    public Values returnValues() {
        return returnValues;
    }
}
