// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import static javax.net.ssl.SSLEngineResult.*;

/**
 * A {@link CryptoSocket} using TLS ({@link SSLEngine})
 *
 * @author bjorncs
 */
public class SslSocket implements CryptoSocket {

    private static final Logger log = Logger.getLogger(SslSocket.class.getName());

    private enum HandshakeState { NOT_STARTED, NEED_READ, NEED_WRITE, COMPLETED }

    private final SocketChannel channel;
    private final SSLEngine sslEngine;
    private final ByteBuffer wrapBuffer;
    private final ByteBuffer unwrapBuffer;
    private final ByteBuffer handshakeDummyBuffer;
    private HandshakeState handshakeState;

    public SslSocket(SocketChannel channel, SSLEngine sslEngine) {
        this.channel = channel;
        this.sslEngine = sslEngine;
        SSLSession nullSession = sslEngine.getSession();
        this.wrapBuffer = ByteBuffer.allocate(nullSession.getPacketBufferSize() * 2);
        this.unwrapBuffer = ByteBuffer.allocate(nullSession.getPacketBufferSize() * 2);
        // Note: Dummy buffer as unwrap requires a full size application buffer even though no application data is unwrapped
        this.handshakeDummyBuffer = ByteBuffer.allocate(nullSession.getApplicationBufferSize());
        this.handshakeState = HandshakeState.NOT_STARTED;
    }

    @Override
    public SocketChannel channel() {
        return channel;
    }

    @Override
    public HandshakeResult handshake() throws IOException {
        HandshakeState newHandshakeState = processHandshakeState(this.handshakeState);
        log.fine(() -> String.format("Handshake state '%s -> %s'", this.handshakeState, newHandshakeState));
        this.handshakeState = newHandshakeState;
        return toHandshakeResult(newHandshakeState);
    }

    private HandshakeState processHandshakeState(HandshakeState state) throws IOException {
        switch (state) {
            case NOT_STARTED:
                sslEngine.beginHandshake();
                break;
            case NEED_WRITE:
                channelWrite();
                break;
            case NEED_READ:
                channelRead();
                break;
            case COMPLETED:
                return HandshakeState.COMPLETED;
            default:
                throw new IllegalStateException("Unhandled state: " + state);
        }

        while (true) {
            switch (sslEngine.getHandshakeStatus()) {
                case NOT_HANDSHAKING:
                    if (hasWrapBufferMoreData()) return HandshakeState.NEED_WRITE;
                    sslEngine.setEnableSessionCreation(false); // disable renegotiation
                    return HandshakeState.COMPLETED;
                case NEED_TASK:
                    sslEngine.getDelegatedTask().run();
                    break;
                case NEED_UNWRAP:
                    if (hasWrapBufferMoreData()) return HandshakeState.NEED_WRITE;
                    int bytesUnwrapped = sslEngineUnwrap(handshakeDummyBuffer);
                    if (handshakeDummyBuffer.position() > 0) throw new SSLException("Got application data in handshake unwrap: " + handshakeDummyBuffer);
                    if (bytesUnwrapped == -1) return HandshakeState.NEED_READ;
                    break;
                case NEED_WRAP:
                    int bytesWrapped = sslEngineWrap(handshakeDummyBuffer);
                    if (bytesWrapped == -1) return HandshakeState.NEED_WRITE;
                    break;
                default:
                    throw new IllegalStateException("Unexpected handshake status: " + sslEngine.getHandshakeStatus());
            }
        }
    }

    private static HandshakeResult toHandshakeResult(HandshakeState state) {
        switch (state) {
            case NEED_READ:
                return HandshakeResult.NEED_READ;
            case NEED_WRITE:
                return HandshakeResult.NEED_WRITE;
            case COMPLETED:
                return HandshakeResult.DONE;
            default:
                throw new IllegalStateException("Unhandled state: " + state);
        }
    }

    @Override
    public int getMinimumReadBufferSize() {
        return sslEngine.getSession().getApplicationBufferSize();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        verifyHandshakeCompleted();
        int bytesUnwrapped = sslEngineAppDataUnwrap(dst);
        if (bytesUnwrapped > 0) return bytesUnwrapped;

        int bytesRead = channelRead();
        if (bytesRead == 0) return 0;
        return drain(dst);
    }

    @Override
    public int drain(ByteBuffer dst) throws IOException {
        verifyHandshakeCompleted();
        int totalBytesUnwrapped = 0;
        int bytesUnwrapped;
        do {
            bytesUnwrapped = sslEngineAppDataUnwrap(dst);
            totalBytesUnwrapped += bytesUnwrapped;
        } while (bytesUnwrapped > 0);
        return totalBytesUnwrapped;
    }

    private int sslEngineAppDataUnwrap(ByteBuffer dst) throws IOException {
        int bytesUnwrapped = sslEngineUnwrap(dst);
        if (bytesUnwrapped == 0) throw new SSLException("Got handshake data in application data unwrap");
        if (bytesUnwrapped == -1) return 0;
        return bytesUnwrapped;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        channelWrite();
        if (hasWrapBufferMoreData()) return 0;
        int totalBytesWrapped = 0;
        while (src.hasRemaining()) {
            int bytesWrapped = sslEngineAppDataWrap(src);
            if (bytesWrapped == -1) break;
            totalBytesWrapped += bytesWrapped;
        }
        return totalBytesWrapped;
    }

    @Override
    public FlushResult flush() throws IOException {
        channelWrite();
        return hasWrapBufferMoreData() ? FlushResult.NEED_WRITE : FlushResult.DONE;
    }

    private int sslEngineAppDataWrap(ByteBuffer src) throws IOException {
        int bytesWrapped = sslEngineWrap(src);
        if (bytesWrapped == 0) throw new SSLException("Got handshake data in application data wrap");
        return bytesWrapped;
    }

    // returns number of bytes produced or -1 if unwrap buffer does not contain a full ssl frame
    private int sslEngineUnwrap(ByteBuffer dst) throws IOException {
        unwrapBuffer.flip();
        SSLEngineResult result = sslEngine.unwrap(unwrapBuffer, dst);
        unwrapBuffer.compact();
        Status status = result.getStatus();
        switch (status) {
            case OK:
                return result.bytesProduced();
            case BUFFER_UNDERFLOW:
                return -1;
            case CLOSED:
                throw new ClosedChannelException();
            default:
                throw new IllegalStateException("Unexpected status: " + status);
        }
    }

    // returns number of bytes consumed or -1 if wrap buffer remaining capacity is too small
    private int sslEngineWrap(ByteBuffer src) throws IOException {
        SSLEngineResult result = sslEngine.wrap(src, wrapBuffer);
        Status status = result.getStatus();
        switch (status) {
            case OK:
                return result.bytesConsumed();
            case BUFFER_OVERFLOW:
                return -1;
            case CLOSED:
                throw new ClosedChannelException();
            default:
                throw new IllegalStateException("Unexpected status: " + status);
        }
    }

    // returns number of bytes read
    private int channelRead() throws IOException {
        int read = channel.read(unwrapBuffer);
        if (read == -1) throw new ClosedChannelException();
        return read;
    }

    // returns number of bytes written
    private int channelWrite() throws IOException {
        wrapBuffer.flip();
        int written = channel.write(wrapBuffer);
        wrapBuffer.compact();
        if (written == -1) throw new ClosedChannelException();
        return written;
    }

    private void verifyHandshakeCompleted() throws SSLException {
        if (handshakeState != HandshakeState.COMPLETED)
            throw new SSLException("Handshake not completed: handshakeState=" + handshakeState);
    }

    private boolean hasWrapBufferMoreData() {
        return wrapBuffer.position() > 0;
    }

}
