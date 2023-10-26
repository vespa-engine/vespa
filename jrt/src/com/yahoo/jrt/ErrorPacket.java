// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


class ErrorPacket extends Packet
{
    private int         errorCode;
    private StringValue errorMessage;

    public ErrorPacket(int flags, int reqId,
                       int errorCode,
                       String errorMessage)
    {
        super(flags, reqId);
        this.errorCode = errorCode;
        this.errorMessage = new StringValue(errorMessage);
    }

    public ErrorPacket(int flags, int reqId,
                       ByteBuffer src)
    {
        super(flags, reqId);
        errorCode = src.getInt();
        errorMessage = new StringValue(src);
    }

    public int bytes() {
        return (headerLength +
                4 +
                errorMessage.bytes());
    }

    public int packetCode() {
        return PCODE_ERROR;
    }

    public void encode(ByteBuffer dst) {
        dst.putInt(errorCode);
        errorMessage.encode(dst);
    }

    public int errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage.asString();
    }
}
