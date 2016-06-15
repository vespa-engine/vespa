// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

/**
 * Responsible for decoding pong packets from a search node.
 * @author tonytv
 */
public class SearchNodePongPacket extends PongPacket {
    private int timeStamp;

    public static SearchNodePongPacket create() {
        return new SearchNodePongPacket();
    }

    @Override
    public int getCode() { return 207; }

    @Override
    public void decodeBody(ByteBuffer buffer) {
        @SuppressWarnings("unused")
        int partitionId = buffer.getInt();

        timeStamp = buffer.getInt();
    }

    @Override
    public int getDocstamp() {
        return timeStamp;
    }
}
