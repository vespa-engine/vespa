// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

import com.yahoo.fs4.mplex.FS4Channel;

/**
 * Interface for recieving notifications of packets sent or recieved.
 *
 * @author tonytv
 */
public interface PacketListener {
    void packetSent(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm);
    void packetReceived(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm);
}
