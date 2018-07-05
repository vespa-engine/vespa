// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

import com.yahoo.fs4.mplex.FS4Channel;

/**
 * Broadcasts packet notifications to a list of listeners.
 *
 * @author Tony Vaagenes
 */
public class PacketNotificationsBroadcaster implements PacketListener {

    private final PacketListener[] listeners;

    public PacketNotificationsBroadcaster(PacketListener... listeners) {
        this.listeners = listeners;
    }

    @Override
    public void packetSent(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        if (channel == null) return;
        for (PacketListener listener : listeners)
            listener.packetSent(channel, packet, serializedForm);
    }

    @Override
    public void packetReceived(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        if (channel == null) return;
        for (PacketListener listener : listeners)
            listener.packetReceived(channel, packet, serializedForm);
    }
}
