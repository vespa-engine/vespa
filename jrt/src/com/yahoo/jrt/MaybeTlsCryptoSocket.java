// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.tls.ConnectionAuthContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * A crypto socket for the server side of a connection that
 * auto-detects whether the connection is tls encrypted or unencrypted
 * using clever heuristics. The assumption is that the client side
 * will send at least one RPC request before expecting anything from
 * the server. The first 9 bytes are inspected to see if they look
 * like part of a tls handshake or not (RPC packet headers are 12
 * bytes).
 **/
public class MaybeTlsCryptoSocket implements CryptoSocket {

    private static final int SNOOP_SIZE = 9;

    private CryptoSocket socket;

    // 'data' is the first 9 bytes received from the client
    public static boolean looksLikeTlsToMe(byte[] data) {
        if (data.length != SNOOP_SIZE) {
            return false; // wrong data size for tls detection
        }
        if (data[0] != 22) {
            return false; // not tagged as tls handshake
        }
        if (data[1] != 3) {
            return false; // unknown major version
        }
        if ((data[2] != 1) && (data[2] != 3)) {
            return false; // unknown minor version
        }
        int frame_len = (data[3] & 0xff);
        frame_len = ((frame_len << 8) | (data[4] & 0xff));
        if (frame_len > (16384 + 2048)) {
            return false; // frame too large
        }
        if (frame_len < 4) {
            return false; // frame too small
        }
        if (data[5] != 0x1) {
            return false; // not tagges as client hello
        }
        int hello_len = (data[6] & 0xff);
        hello_len = ((hello_len << 8) | (data[7] & 0xff));
        hello_len = ((hello_len << 8) | (data[8] & 0xff));
        if ((frame_len - 4) != hello_len) {
            return false; // inconsistent sizes; frame vs client hello
        }
        return true;
    }

    private class MyCryptoSocket extends NullCryptoSocket {

        private final TransportMetrics metrics = TransportMetrics.getInstance();
        private TlsCryptoEngine factory;
        private Buffer buffer;

        MyCryptoSocket(SocketChannel channel, TlsCryptoEngine factory) {
            super(channel, true);
            this.factory = factory;
            this.buffer = new Buffer(4096);
        }

        @Override public HandshakeResult handshake() throws IOException {
            if (factory != null) {
                if (channel().read(buffer.getWritable(SNOOP_SIZE)) == -1) {
                    throw new IOException("jrt: Connection closed by peer during tls detection");
                }
                if (buffer.bytes() < SNOOP_SIZE) {
                    return HandshakeResult.NEED_READ;
                }
                byte[] data = new byte[SNOOP_SIZE];
                ByteBuffer src = buffer.getReadable();
                for (int i = 0; i < SNOOP_SIZE; i++) {
                    data[i] = src.get(i);
                }
                if (looksLikeTlsToMe(data)) {
                    TlsCryptoSocket tlsSocket = factory.createServerCryptoSocket(channel());
                    tlsSocket.injectReadData(buffer);
                    socket = tlsSocket;
                    return socket.handshake();
                } else {
                    metrics.incrementServerUnencryptedConnectionsEstablished();
                    factory = null;
                }
            }
            return HandshakeResult.DONE;
        }

        @Override public int read(ByteBuffer dst) throws IOException {
            int drainResult = drain(dst);
            if (drainResult != 0) {
                return drainResult;
            }
            return super.read(dst);
        }

        @Override public int drain(ByteBuffer dst) throws IOException {
            int cnt = 0;
            if (buffer != null) {
                ByteBuffer src = buffer.getReadable();
                while (src.hasRemaining() && dst.hasRemaining()) {
                    dst.put(src.get());
                    cnt++;
                }
                if (buffer.bytes() == 0) {
                    buffer = null;
                }
            }
            return cnt;
        }
    }

    public MaybeTlsCryptoSocket(SocketChannel channel, TlsCryptoEngine factory) {
        this.socket = new MyCryptoSocket(channel, factory);
    }

    @Override public SocketChannel channel() { return socket.channel(); }
    @Override public HandshakeResult handshake() throws IOException { return socket.handshake(); }
    @Override public void doHandshakeWork() { socket.doHandshakeWork(); }
    @Override public int getMinimumReadBufferSize() { return socket.getMinimumReadBufferSize(); }
    @Override public int read(ByteBuffer dst) throws IOException { return socket.read(dst); }
    @Override public int drain(ByteBuffer dst) throws IOException { return socket.drain(dst); }
    @Override public int write(ByteBuffer src) throws IOException { return socket.write(src); }
    @Override public FlushResult flush() throws IOException { return socket.flush(); }
    @Override public void dropEmptyBuffers() { socket.dropEmptyBuffers(); }
    @Override public ConnectionAuthContext connectionAuthContext() { return socket.connectionAuthContext(); }
}
