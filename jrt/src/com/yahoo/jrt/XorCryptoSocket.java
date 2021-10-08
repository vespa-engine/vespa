// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A very simple CryptoSocket that performs connection handshaking and
 * data transformation. Used to test encryption integration separate
 * from TLS.
 *
 * @author havardpe
 */
public class XorCryptoSocket implements CryptoSocket {

    private static final int CHUNK_SIZE = 4096;
    enum OP { READ_KEY, WRITE_KEY }

    private Queue<OP>     opList  = new ArrayDeque<>();
    private byte          myKey   = genKey();
    private byte          peerKey;
    private Buffer        input   = new Buffer(CHUNK_SIZE);
    private Buffer        output  = new Buffer(CHUNK_SIZE);
    private SocketChannel channel;

    private static byte genKey() {
        return (byte) new SecureRandom().nextInt(256);
    }

    private HandshakeResult readKey() throws IOException {
        int res = channel.read(input.getWritable(1));
        if (res > 0) {
            peerKey = input.getReadable().get();
            return HandshakeResult.DONE;
        } else if (res == 0) {
            return HandshakeResult.NEED_READ;
        } else {
            throw new IOException("EOF during handshake");
        }
    }
    private HandshakeResult writeKey() throws IOException {
        if (output.bytes() == 0) {
            output.getWritable(1).put(myKey);
        }
        if (channel.write(output.getReadable()) == 0) {
            return HandshakeResult.NEED_WRITE;
        }
        return HandshakeResult.DONE;
    }
    private HandshakeResult perform(OP op) throws IOException {
        switch (op) {
        case READ_KEY:  return readKey();
        case WRITE_KEY: return writeKey();
        }
        throw new IOException("invalid handshake operation");
    }

    public XorCryptoSocket(SocketChannel channel, boolean isServer) {
        this.channel = channel;
        if (isServer) {
            opList.add(OP.READ_KEY);
            opList.add(OP.WRITE_KEY);
        } else {
            opList.add(OP.WRITE_KEY);
            opList.add(OP.READ_KEY);
        }
    }
    @Override public SocketChannel channel() { return channel; }
    @Override public HandshakeResult handshake() throws IOException {
        while (!opList.isEmpty()) {
            HandshakeResult partialResult = perform(opList.element());
            if (partialResult != HandshakeResult.DONE) {
                return partialResult;
            }
            opList.remove();
        }
        return HandshakeResult.DONE;
    }
    @Override public void doHandshakeWork() {}
    @Override public int getMinimumReadBufferSize() { return 1; }
    @Override public int read(ByteBuffer dst) throws IOException {
        if (input.bytes() == 0) {
            if (channel.read(input.getWritable(CHUNK_SIZE)) == -1) {
                return -1; // EOF
            }
        }
        return drain(dst);
    }
    @Override public int drain(ByteBuffer dst) throws IOException {
        int cnt = 0;
        ByteBuffer src = input.getReadable();
        while (src.hasRemaining() && dst.hasRemaining()) {
            dst.put((byte)(src.get() ^ myKey));
            cnt++;
        }
        return cnt;
    }
    @Override public int write(ByteBuffer src) throws IOException {
        int cnt = 0;
        if (flush() == FlushResult.DONE) {
            ByteBuffer dst = output.getWritable(CHUNK_SIZE);
            while (src.hasRemaining() && dst.hasRemaining()) {
                dst.put((byte)(src.get() ^ peerKey));
                cnt++;
            }
        }
        return cnt;
    }
    @Override public FlushResult flush() throws IOException {
        ByteBuffer src = output.getReadable();
        channel.write(src);
        if (src.hasRemaining()) {
            return FlushResult.NEED_WRITE;
        } else {
            return FlushResult.DONE;
        }
    }
    @Override public void dropEmptyBuffers() {
        input.shrink(0);
        output.shrink(0);
    }
}
