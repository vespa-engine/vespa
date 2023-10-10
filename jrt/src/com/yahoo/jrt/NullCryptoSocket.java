// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


/**
 * A CryptoSocket with no encryption
 **/
public class NullCryptoSocket implements CryptoSocket {
    private final boolean isServer;
    private SocketChannel channel;
    private final TransportMetrics metrics = TransportMetrics.getInstance();
    public NullCryptoSocket(SocketChannel channel, boolean isServer) { this.channel = channel; this.isServer = isServer; }
    @Override public SocketChannel channel() { return channel; }
    @Override public HandshakeResult handshake() throws IOException {
        if (isServer) {
            metrics.incrementServerUnencryptedConnectionsEstablished();
        } else {
            metrics.incrementClientUnencryptedConnectionsEstablished();
        }
        return HandshakeResult.DONE;
    }
    @Override public void doHandshakeWork() {}
    @Override public int getMinimumReadBufferSize() { return 1; }
    @Override public int read(ByteBuffer dst) throws IOException { return channel.read(dst); }
    @Override public int drain(ByteBuffer dst) throws IOException { return 0; }
    @Override public int write(ByteBuffer src) throws IOException { return channel.write(src); }
    @Override public FlushResult flush() throws IOException { return FlushResult.DONE; }
    @Override public void dropEmptyBuffers() {}
}
