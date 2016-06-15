// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

import com.yahoo.text.Utf8;

/**
 *
 * An error packet signaling that an error occurred.
 *
 * @author Bjørn Borud
 */
public class ErrorPacket extends Packet {
    private int errorCode;
    private int errmsgLen;
    private String message;

    private ErrorPacket() {
    }

    public static ErrorPacket create() {
        return new ErrorPacket();
    }

    public int getCode() { return 203; }

    public void decodeBody(ByteBuffer buffer) {
        errorCode = buffer.getInt();
        errmsgLen = buffer.getInt();

        byte[] tmp = new byte[errmsgLen];
        buffer.get(tmp);

        message = Utf8.toString(tmp);
    }

    public int getErrorCode ()       { return errorCode; }

    public void encodeBody(ByteBuffer buffer) {
        // No body
    }

    public String toString() {
        return (message + " (" + errorCode + ")");
    }

}
