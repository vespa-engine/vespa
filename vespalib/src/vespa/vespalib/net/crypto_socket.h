// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <cstdlib>

namespace vespalib {

namespace net { class ConnectionAuthContext; }

/**
 * Abstraction of a low-level async network socket which can produce
 * io events and allows encrypting written data and decrypting read
 * data. The interface is complexified to handle the use of internal
 * buffers that may mask io events and pending work. The interface is
 * simplified by assuming there will be no mid-stream re-negotiation
 * (no read/write cross-dependencies). Handshaking is explicit and
 * up-front. Note that in order to ensure the correct behaviour of the
 * SyncCryptoSocket wrapper, the read function must not call a
 * low-level function that might produce the EWOULDBLOCK/EAGAIN
 * 'error' after any application-level data has been obtained.
 **/
struct CryptoSocket {
    using UP = std::unique_ptr<CryptoSocket>;

    /**
     * Get the underlying file descriptor used to detect io events.
     **/
    virtual int get_fd() const = 0;

    enum class HandshakeResult { FAIL, DONE, NEED_READ, NEED_WRITE, NEED_WORK };

    /**
     * Try to progress the initial connection handshake. Handshaking
     * will be done once, before any normal reads or writes are
     * performed. Re-negotiation at a later stage will not be
     * permitted. This function will be called multiple times until
     * the status is either DONE or FAIL. When NEED_READ or NEED_WRITE
     * is returned, the handshake function will be called again when
     * the appropriate io event has triggered. When NEED_WORK is
     * returned, the 'do_handshake_work' function will be called
     * exactly once before this function is called again.
     **/
    virtual HandshakeResult handshake() = 0;

    /**
     * This function is called to perform possibly expensive work
     * needed by the 'handshake' function. The work is done by a
     * separate function to enable performing it outside the critical
     * path (transport thread).
     **/
    virtual void do_handshake_work() = 0;

    /**
     * This function should be called after handshaking has completed
     * before calling the read function. It dictates the minimum size
     * of the application read buffer presented to the read
     * function. This is needed to support frame-based stateless
     * decryption of incoming data.
     **/
    virtual size_t min_read_buffer_size() const = 0;

    /**
     * Called when the underlying socket has available data. Read
     * through the entire input pipeline. The semantics are the same
     * as with a normal socket read (errno, EOF, etc.).
     **/
    virtual ssize_t read(char *buf, size_t len) = 0;

    /**
     * Similar to read, but this function is not allowed to read from
     * the underlying socket. This is to enable the application to
     * make sure that there is no more input data in the read pipeline
     * that is independent of data not yet read from the actual
     * socket. Draining data from the input pipeline is done to
     * prevent masking read events. NOTE: This function should return
     * 0 when all data has been drained, and the application MUST NOT
     * interpret that as EOF.
     **/
    virtual ssize_t drain(char *buf, size_t len) = 0;

    /**
     * Called when the application has data it wants to write. Write
     * through the entire output pipeline. The semantics are the same
     * as with a normal socket write (errno, etc.).
     **/
    virtual ssize_t write(const char *buf, size_t len) = 0;

    /**
     * Try to flush data in the write pipeline that is not dependent
     * on data not yet written by the application into the underlying
     * socket. This is to enable the application to identify pending
     * work that may not be completed until the underlying socket is
     * ready for writing more data. The semantics are the same as with
     * a normal socket write (errno, etc.) with the exception that 0
     * will be returned when there is no more data to flush and any
     * positive number indicates that we were able to flush something
     * (it does not need to reflect the actual number of bytes written
     * to the underlying socket).
     **/
    virtual ssize_t flush() = 0;

    /**
     * Signal the end of outgoing data. Note that this might require
     * writing data to the underlying socket to notify the client that
     * no more data will be sent. This function should be treated as a
     * combination of write and flush and should be re-tried after the
     * socket becomes writable if EWOULDBLOCK is returned. Neither
     * write nor flush should be called after this function is
     * called. When this function indicates success (returns 0) all
     * pending data has been written to the underlying socket and the
     * write aspect of the socket has been shut down. Performing
     * half_close on one end of a connection will eventually lead to
     * the other end receiving EOF after all application data has been
     * read. Note that closing the socket immediately after performing
     * half_close might still result in data loss since there is no
     * way of knowing when the data has actually been sent on the
     * network.
     *
     * Ideal graceful shutdown is initiated by one end performing
     * half_close on the connection. When the other end receives EOF
     * it performs half_close on its end of the connection. When both
     * ends have received EOF the sockets can be closed. The ideal
     * scenario is broken by two things: (1) the two generals paradox,
     * which proves that both endpoints coming to an agreement about
     * the connection being gracefully shut down is not possible. (2)
     * clients tend to do random things with the connection, leaving
     * it up to the server to be the more responsible party.
     *
     * Real-life graceful-ish shutdown (server-side) should be
     * performed by doing half_close on the server end of the
     * connection. Any incoming data should be read in the hope of
     * getting EOF. The socket should be closed when either EOF is
     * reached, a read error occurred (typically connection reset by
     * peer or similar) or a timeout is reached (application
     * equivalent of the linger option on the socket).
     **/
    virtual ssize_t half_close() = 0;

    /**
     * This function can be called at any time to drop any currently
     * empty internal buffers. Typically called after drain or flush
     * indicates that no further progress can be made.
     **/
    virtual void drop_empty_buffers() = 0;

    /**
     * If the underlying transport channel supports authn/authz,
     * returns a new ConnectionAuthContext object containing the verified
     * credentials of the peer as well as the resulting peer capabilities
     * inferred by our own policy matching.
     *
     * If the underlying transport channel does _not_ support authn/authz
     * (such as a plaintext connection) a dummy context is returned which
     * offers _all_ capabilities.
     */
    [[nodiscard]] virtual std::unique_ptr<net::ConnectionAuthContext> make_auth_context();

    virtual ~CryptoSocket();
};

} // namespace vespalib
