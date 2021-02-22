// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.tls.authz.AuthorizationResult;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static javax.net.ssl.SSLEngineResult.Status;

/**
 * A {@link CryptoSocket} using TLS ({@link SSLEngine})
 *
 * @author bjorncs
 */
public class TlsCryptoSocket implements CryptoSocket {

    private static final ByteBuffer NULL_BUFFER = ByteBuffer.allocate(0);

    private static final Logger log = Logger.getLogger(TlsCryptoSocket.class.getName());

    private enum HandshakeState { NOT_STARTED, NEED_READ, NEED_WRITE, NEED_WORK, COMPLETED }

    private final TransportMetrics metrics = TransportMetrics.getInstance();
    private final SocketChannel channel;
    private final SSLEngine sslEngine;
    private final Buffer wrapBuffer;
    private final Buffer unwrapBuffer;
    private int sessionPacketBufferSize;
    private int sessionApplicationBufferSize;
    private ByteBuffer handshakeDummyBuffer;
    private HandshakeState handshakeState;
    private AuthorizationResult authorizationResult;

    public TlsCryptoSocket(SocketChannel channel, SSLEngine sslEngine) {
        this.channel = channel;
        this.sslEngine = sslEngine;
        SSLSession nullSession = sslEngine.getSession();
        this.wrapBuffer = new Buffer(Math.max(0x8000, nullSession.getPacketBufferSize()));
        this.unwrapBuffer = new Buffer(Math.max(0x8000, nullSession.getPacketBufferSize()));
        // Note: Dummy buffer as unwrap requires a full size application buffer even though no application data is unwrapped
        this.handshakeDummyBuffer = ByteBuffer.allocate(nullSession.getApplicationBufferSize());
        this.handshakeState = HandshakeState.NOT_STARTED;
        log.fine(() -> "Initialized with " + sslEngine.toString());
    }

    // inject pre-read data into the read pipeline (typically called by MaybeTlsCryptoSocket)
    public void injectReadData(Buffer data) {
        unwrapBuffer.getWritable(data.bytes()).put(data.getReadable());
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

    @Override
    public void doHandshakeWork() {
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null) {
            task.run();
        }
    }

    private HandshakeState processHandshakeState(HandshakeState state) throws IOException {
        try {
            switch (state) {
                case NOT_STARTED:
                    log.fine(() -> "Initiating handshake");
                    sslEngine.beginHandshake();
                    break;
                case NEED_WRITE:
                    channelWrite();
                    break;
                case NEED_READ:
                    channelRead();
                    break;
                case NEED_WORK:
                    if (authorizationResult == null) {
                        PeerAuthorizerTrustManager.getAuthorizationResult(sslEngine) // only available during handshake
                                .ifPresent(result ->  {
                                    if (!result.succeeded()) {
                                        metrics.incrementPeerAuthorizationFailures();
                                    }
                                    authorizationResult = result;
                                });
                    }
                    break;
                case COMPLETED:
                    return HandshakeState.COMPLETED;
                default:
                    throw unhandledStateException(state);
            }
            while (true) {
                log.fine(() -> "SSLEngine.getHandshakeStatus(): " + sslEngine.getHandshakeStatus());
                switch (sslEngine.getHandshakeStatus()) {
                    case NOT_HANDSHAKING:
                        if (wrapBuffer.bytes() > 0) return HandshakeState.NEED_WRITE;
                        sslEngine.setEnableSessionCreation(false); // disable renegotiation
                        handshakeDummyBuffer = null;
                        SSLSession session = sslEngine.getSession();
                        sessionApplicationBufferSize = session.getApplicationBufferSize();
                        sessionPacketBufferSize = session.getPacketBufferSize();
                        log.fine(() -> String.format("Handshake complete: protocol=%s, cipherSuite=%s", session.getProtocol(), session.getCipherSuite()));
                        if (sslEngine.getUseClientMode()) {
                            metrics.incrementClientTlsConnectionsEstablished();
                        } else {
                            metrics.incrementServerTlsConnectionsEstablished();
                        }
                        return HandshakeState.COMPLETED;
                    case NEED_TASK:
                        return HandshakeState.NEED_WORK;
                    case NEED_UNWRAP:
                        if (wrapBuffer.bytes() > 0) return HandshakeState.NEED_WRITE;
                        if (!handshakeUnwrap()) return HandshakeState.NEED_READ;
                        break;
                    case NEED_WRAP:
                        if (!handshakeWrap()) return HandshakeState.NEED_WRITE;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected handshake status: " + sslEngine.getHandshakeStatus());
                }
            }
        } catch (SSLHandshakeException e) {
            // sslEngine.getDelegatedTask().run() and handshakeWrap() may throw SSLHandshakeException, potentially handshakeUnwrap() and sslEngine.beginHandshake() as well.
            if (authorizationResult == null || authorizationResult.succeeded()) { // don't include handshake failures due from PeerAuthorizerTrustManager
                metrics.incrementTlsCertificateVerificationFailures();
            }
            throw e;
        }
    }

    private static HandshakeResult toHandshakeResult(HandshakeState state) {
        switch (state) {
            case NEED_READ:
                return HandshakeResult.NEED_READ;
            case NEED_WRITE:
                return HandshakeResult.NEED_WRITE;
            case NEED_WORK:
                return HandshakeResult.NEED_WORK;
            case COMPLETED:
                return HandshakeResult.DONE;
            default:
                throw unhandledStateException(state);
        }
    }

    @Override
    public int getMinimumReadBufferSize() {
        return sessionApplicationBufferSize;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        verifyHandshakeCompleted();
        int bytesUnwrapped = drain(dst);
        if (bytesUnwrapped > 0) return bytesUnwrapped;

        int bytesRead = channelRead();
        if (bytesRead == 0) return 0;
        return drain(dst);
    }

    @Override
    public int drain(ByteBuffer dst) throws IOException {
        verifyHandshakeCompleted();
        int totalBytesUnwrapped = 0;
        while (true) {
            int result = applicationDataUnwrap(dst);
            if (result < 0) return totalBytesUnwrapped;
            totalBytesUnwrapped += result;
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        verifyHandshakeCompleted();
        if (flush() == FlushResult.NEED_WRITE) return 0;
        int totalBytesWrapped = 0;
        int bytesWrapped;
        do {
            bytesWrapped = applicationDataWrap(src);
            totalBytesWrapped += bytesWrapped;
        } while (bytesWrapped > 0 && wrapBuffer.bytes() < sessionPacketBufferSize);
        return totalBytesWrapped;
    }

    @Override
    public FlushResult flush() throws IOException {
        verifyHandshakeCompleted();
        channelWrite();
        return wrapBuffer.bytes() > 0 ? FlushResult.NEED_WRITE : FlushResult.DONE;
    }

    @Override
    public Optional<SecurityContext> getSecurityContext() {
        try {
            if (handshakeState != HandshakeState.COMPLETED) {
                return Optional.empty();
            }
            List<X509Certificate> peerCertificateChain =
                    Arrays.stream(sslEngine.getSession().getPeerCertificates())
                            .map(X509Certificate.class::cast)
                            .collect(toList());
            return Optional.of(new SecurityContext(peerCertificateChain));
        } catch (SSLPeerUnverifiedException e) { // unverified peer: non-certificate based ciphers or peer did not provide a certificate
            return Optional.of(new SecurityContext(List.of())); // secure connection, but peer does not have a certificate chain.
        }
    }

    private boolean handshakeWrap() throws IOException {
        SSLEngineResult result = sslEngineWrap(NULL_BUFFER);
        switch (result.getStatus()) {
            case OK:
                return true;
            case BUFFER_OVERFLOW:
                // This is to ensure we have large enough buffer during handshake phase too.
                sessionPacketBufferSize = sslEngine.getSession().getPacketBufferSize();
                return false;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private int applicationDataWrap(ByteBuffer src) throws IOException {
        SSLEngineResult result = sslEngineWrap(src);
        failIfRenegotiationDetected(result);
        switch (result.getStatus()) {
            case OK:
                return result.bytesConsumed();
            case BUFFER_OVERFLOW:
                return 0;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private SSLEngineResult sslEngineWrap(ByteBuffer src) throws IOException {
        SSLEngineResult result = sslEngine.wrap(src, wrapBuffer.getWritable(sessionPacketBufferSize));
        failIfCloseSignalDetected(result);
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
        failIfRenegotiationDetected(result);
        switch (result.getStatus()) {
            case OK:
                return result.bytesProduced();
            case BUFFER_OVERFLOW:
            case BUFFER_UNDERFLOW:
                return -1;
            default:
                throw unexpectedStatusException(result.getStatus());
        }
    }

    private SSLEngineResult sslEngineUnwrap(ByteBuffer dst) throws IOException {
        SSLEngineResult result = sslEngine.unwrap(unwrapBuffer.getReadable(), dst);
        failIfCloseSignalDetected(result);
        return result;
    }

    // returns number of bytes read
    private int channelRead() throws IOException {
        int read = channel.read(unwrapBuffer.getWritable(sessionPacketBufferSize));
        if (read == -1) throw new ClosedChannelException();
        return read;
    }

    // returns number of bytes written
    private int channelWrite() throws IOException {
        return channel.write(wrapBuffer.getReadable());
    }

    private static void failIfCloseSignalDetected(SSLEngineResult result) throws ClosedChannelException {
        if (result.getStatus() == Status.CLOSED) throw new ClosedChannelException();
    }

    private static void failIfRenegotiationDetected(SSLEngineResult result) throws SSLException {
        if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING
                && result.getHandshakeStatus() != HandshakeStatus.FINISHED) {
            throw new SSLException("Renegotiation detected");
        }
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

}
