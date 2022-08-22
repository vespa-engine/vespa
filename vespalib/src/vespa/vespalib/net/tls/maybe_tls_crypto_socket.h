// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/net/crypto_socket.h>
#include <vespa/vespalib/net/socket_handle.h>
#include "tls_crypto_engine.h"

namespace vespalib {

/**
 * A crypto socket for the server side of a connection that
 * auto-detects whether the connection is tls encrypted or unencrypted
 * using clever heuristics. The assumption is that the client side
 * will send at least 8 bytes of data before expecting anything from
 * the server. These 8 bytes are inspected to see if they look like
 * part of a tls handshake or not.
 **/
class MaybeTlsCryptoSocket : public CryptoSocket
{
private:
    CryptoSocket::UP _socket;
public:
    MaybeTlsCryptoSocket(SocketHandle socket, std::shared_ptr<AbstractTlsCryptoEngine> tls_engine);
    int get_fd() const override { return _socket->get_fd(); }
    HandshakeResult handshake() override { return _socket->handshake(); }
    void do_handshake_work() override { _socket->do_handshake_work(); }
    size_t min_read_buffer_size() const override { return _socket->min_read_buffer_size(); }
    ssize_t read(char *buf, size_t len) override { return _socket->read(buf, len); }
    ssize_t drain(char *buf, size_t len) override { return _socket->drain(buf, len); }
    ssize_t write(const char *buf, size_t len) override { return _socket->write(buf, len); }
    ssize_t flush() override { return _socket->flush(); }
    ssize_t half_close() override { return _socket->half_close(); }
    void drop_empty_buffers() override { _socket->drop_empty_buffers(); }
    std::unique_ptr<net::ConnectionAuthContext> make_auth_context() override;
};

} // namespace vespalib
