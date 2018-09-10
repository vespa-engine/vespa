// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.search.Query;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/**
 * @author ollivir
 */
public class CloseableChannel implements Closeable {
    private FS4Channel channel;
    private final Optional<Integer> distributionKey;

    public CloseableChannel(Backend backend) {
        this(backend, Optional.empty());
    }

    public CloseableChannel(Backend backend, Optional<Integer> distributionKey) {
        this.channel = backend.openChannel();
        this.distributionKey = distributionKey;
    }

    public void setQuery(Query query) {
        channel.setQuery(query);
    }

    public boolean sendPacket(BasicPacket packet) throws InvalidChannelException, IOException {
        return channel.sendPacket(packet);
    }

    public BasicPacket[] receivePackets(long timeout, int packetCount) throws InvalidChannelException, ChannelTimeoutException {
        return channel.receivePackets(timeout, packetCount);
    }

    public Optional<Integer> distributionKey() {
        return distributionKey;
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }
}
