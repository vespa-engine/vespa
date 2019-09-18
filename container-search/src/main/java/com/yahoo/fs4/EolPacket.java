// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

/**
 * A EOL packet signaling end of transmission.
 * This package has no body.
 *
 * @author bratseth
 */
public class EolPacket extends Packet {

    private EolPacket() {
    }

    public static EolPacket create() {
        return new EolPacket();
    }

    public int getCode() { return 200; }

    public void decodeBody(ByteBuffer buffer) {
        // No body
    }

    public void encodeBody(ByteBuffer buffer) {
        // No body
    }

    public String toString() {
        return "EOL packet";
    }

}
