// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.security.tls.ConnectionAuthContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


/**
 * Abstraction of a low-level async network socket which can produce
 * io events and allows encrypting written data and decrypting read
 * data. The interface is complexified to handle the use of internal
 * buffers that may mask io events and pending work. The interface is
 * simplified by assuming there will be no mid-stream re-negotiation
 * (no read/write cross-dependencies). Handshaking is explicit and
 * up-front. This interface is initially designed for persistent
 * transport connections where closing the connection has no
 * application-level semantics.
 **/
public interface CryptoSocket {

    /**
     * Obtain the underlying socket channel used by this CryptoSocket.
     **/
    public SocketChannel channel();

    public enum HandshakeResult { DONE, NEED_READ, NEED_WRITE, NEED_WORK }

    /**
     * Try to progress the initial connection handshake. Handshaking
     * will be done once, before any normal reads or writes are
     * performed. Re-negotiation at a later stage will not be
     * permitted. This function will be called multiple times until
     * the status is either DONE or an IOException is thrown. When
     * NEED_READ or NEED_WRITE is returned, the handshake function
     * will be called again when the appropriate io event has
     * triggered. When NEED_WORK is returned, the {@link #doHandshakeWork()}
     * will be called (possibly in another thread) before this function is called again.
     **/
    public HandshakeResult handshake() throws IOException;


    /**
     * Called when {@link #handshake()} returns {@link HandshakeResult#NEED_WORK} to perform compute-heavy tasks.
     * This method may be called from another thread to avoid blocking the transport thread.
     */
    public void doHandshakeWork();

    /**
     * This function should be called after handshaking has completed
     * before calling the read function. It dictates the minimum size
     * of the application read buffer presented to the read
     * function. This is needed to support frame-based stateless
     * decryption of incoming data.
     **/
    public int getMinimumReadBufferSize();

    /**
     * Called when the underlying socket has available data. Read
     * through the entire input pipeline. The semantics are the same
     * as with a normal socket read except it can also fail for
     * cryptographic reasons.
     **/
    public int read(ByteBuffer dst) throws IOException;

    /**
     * Similar to read, but this function is not allowed to read from
     * the underlying socket. This is to enable the application to
     * make sure that there is no more input data in the read pipeline
     * that is independent of data not yet read from the actual
     * socket. Draining data from the input pipeline is done to
     * prevent masking read events.
     **/
    public int drain(ByteBuffer dst) throws IOException;

    /**
     * Called when the application has data it wants to write. Write
     * through the entire output pipeline. The semantics are the same
     * as with a normal socket write.
     **/
    public int write(ByteBuffer src) throws IOException;

    public enum FlushResult { DONE, NEED_WRITE }

    /**
     * Try to flush data in the write pipeline that is not depenedent
     * on data not yet written by the application into the underlying
     * socket. This is to enable the application to identify pending
     * work that may not be completed until the underlying socket is
     * ready for writing more data. When NEED_WRITE is returned,
     * either write or flush will be called again when the appropriate
     * io event has triggered.
     **/
    public FlushResult flush() throws IOException;

    /**
     * This function can be called at any time to drop any currently
     * empty internal buffers. Typically called after drain or flush
     * indicates that no further progress can be made.
     **/
    public void dropEmptyBuffers();

    /** Returns the auth context for the current connection (given handshake completed) */
    default ConnectionAuthContext connectionAuthContext() { return ConnectionAuthContext.defaultAllCapabilities(); }
}
