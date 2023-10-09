// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


class RequestPacket extends Packet
{
    private StringValue methodName;
    private Values      parameters;

    public RequestPacket(int flags, int reqId,
                         String methodName,
                         Values parameters)
    {
        super(flags, reqId);
        this.methodName = new StringValue(methodName);
        this.parameters = parameters;
    }

    public RequestPacket(int flags, int reqId,
                         ByteBuffer src)
    {
        super(flags, reqId);
        methodName = new StringValue(src);
        parameters = new Values(src);
    }

    public int bytes() {
        return (headerLength +
                methodName.bytes() +
                parameters.bytes());
    }

    public int packetCode() {
        return PCODE_REQUEST;
    }

    public void encode(ByteBuffer dst) {
        methodName.encode(dst);
        parameters.encode(dst);
    }

    public String methodName() {
        return methodName.asString();
    }

    public Values parameters() {
        return parameters;
    }
}
