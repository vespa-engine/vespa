// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib {

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

    enum class HandshakeResult { FAIL, DONE, NEED_READ, NEED_WRITE };

    /**
     * Try to progress the initial connection handshake. Handshaking
     * will be done once, before any normal reads or writes are
     * performed. Re-negotiation at a later stage will not be
     * permitted. This function will be called multiple times until
     * the status is either DONE or FAIL. When NEED_READ or NEED_WRITE
     * is returned, the handshake function will be called again when
     * the appropriate io event has triggered.
     **/
    virtual HandshakeResult handshake() = 0;

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

    virtual ~CryptoSocket();
};

} // namespace vespalib
