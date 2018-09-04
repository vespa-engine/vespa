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
public class TlsCryptoSocket implements CryptoSocket {

    private static final ByteBuffer NULL_BUFFER = ByteBuffer.allocate(0);

    private static final Logger log = Logger.getLogger(TlsCryptoSocket.class.getName());

    private enum HandshakeState { NOT_STARTED, NEED_READ, NEED_WRITE, COMPLETED }

    private final SocketChannel channel;
    private final SSLEngine sslEngine;
    private final ByteBuffer wrapBuffer;
    private final ByteBuffer unwrapBuffer;
    private ByteBuffer handshakeDummyBuffer;
    private HandshakeState handshakeState;

    public TlsCryptoSocket(SocketChannel channel, SSLEngine sslEngine) {
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
                throw unhandledStateException(state);
        }

        while (true) {
            switch (sslEngine.getHandshakeStatus()) {
                case NOT_HANDSHAKING:
                    if (hasWrapBufferMoreData()) return HandshakeState.NEED_WRITE;
                    sslEngine.setEnableSessionCreation(false); // disable renegotiation
                    handshakeDummyBuffer = null;
                    return HandshakeState.COMPLETED;
                case NEED_TASK:
                    sslEngine.getDelegatedTask().run();
                    break;
                case NEED_UNWRAP:
                    if (hasWrapBufferMoreData()) return HandshakeState.NEED_WRITE;
                    if (!handshakeUnwrap()) return HandshakeState.NEED_READ;
                    break;
                case NEED_WRAP:
                    if (!handshakeWrap()) return HandshakeState.NEED_WRITE;
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
                throw unhandledStateException(state);
        }
    }

    @Override
    public int getMinimumReadBufferSize() {
        return sslEngine.getSession().getApplicationBufferSize();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        verifyHandshakeCompleted();
        int bytesUnwrapped = applicationDataUnwrap(dst);
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
            bytesUnwrapped = applicationDataUnwrap(dst);
            totalBytesUnwrapped += bytesUnwrapped;
        } while (bytesUnwrapped > 0);
        return totalBytesUnwrapped;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (flush() == FlushResult.NEED_WRITE) return 0;
        int totalBytesWrapped = 0;
        while (src.hasRemaining()) {
            int bytesWrapped = applicationDataWrap(src);
            if (bytesWrapped == 0) break;
            totalBytesWrapped += bytesWrapped;
        }
        return totalBytesWrapped;
    }

    @Override
    public FlushResult flush() throws IOException {
        channelWrite();
        return hasWrapBufferMoreData() ? FlushResult.NEED_WRITE : FlushResult.DONE;
    }

    private boolean handshakeWrap() throws IOException {
        SSLEngineResult result = sslEngineWrap(NULL_BUFFER);
        switch (result.getStatus()) {
            case OK:
                return true;
            case BUFFER_OVERFLOW:
                return false;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private int applicationDataWrap(ByteBuffer src) throws IOException {
        SSLEngineResult result = sslEngineWrap(src);
        switch (result.getStatus()) {
            case OK:
                int bytesConsumed = result.bytesConsumed();
                if (bytesConsumed == 0) throw new SSLException("Got handshake data in application data wrap");
                return bytesConsumed;
            case BUFFER_OVERFLOW:
                return 0;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private SSLEngineResult sslEngineWrap(ByteBuffer src) throws IOException {
        SSLEngineResult result = sslEngine.wrap(src, wrapBuffer);
        if (result.getStatus() == Status.CLOSED) throw new ClosedChannelException();
        return result;
    }

    private boolean handshakeUnwrap() throws IOException {
        SSLEngineResult result = sslEngineUnwrap(handshakeDummyBuffer);
        switch (result.getStatus()) {
            case OK:
                if (result.bytesProduced() > 0) throw new SSLException("Got application data in handshake unwrap");
                return true;
            case BUFFER_UNDERFLOW:
                return false;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private int applicationDataUnwrap(ByteBuffer dst) throws IOException {
        SSLEngineResult result = sslEngineUnwrap(dst);
        switch (result.getStatus()) {
            case OK:
                int bytesProduced = result.bytesProduced();
                if (bytesProduced == 0) throw new SSLException("Got handshake data in application data unwrap");
                return bytesProduced;
            case BUFFER_OVERFLOW:
            case BUFFER_UNDERFLOW:
                return 0;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private SSLEngineResult sslEngineUnwrap(ByteBuffer dst) throws IOException {
        unwrapBuffer.flip();
        SSLEngineResult result = sslEngine.unwrap(unwrapBuffer, dst);
        unwrapBuffer.compact();
        if (result.getStatus() == Status.CLOSED) throw new ClosedChannelException();
        return result;
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
        return written;
    }

    private static IllegalStateException unhandledStateException(HandshakeState state) {
        return new IllegalStateException("Unhandled state: " + state);
    }

    private static IllegalStateException unexpectedStatusException(Status status) {
        return new IllegalStateException("Unexpected status: " + status);
    }

    private void verifyHandshakeCompleted() throws SSLException {
        if (handshakeState != HandshakeState.COMPLETED)
            throw new SSLException("Handshake not completed: handshakeState=" + handshakeState);
    }

    private boolean hasWrapBufferMoreData() {
        return wrapBuffer.position() > 0;
    }

}
