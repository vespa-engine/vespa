// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/crypto_socket.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include "crypto_codec.h"

namespace vespalib::net::tls {

/**
 * Component adapting an underlying CryptoCodec to the CryptoSocket
 * interface by performing buffer and socket management.
 **/
class CryptoCodecAdapter : public CryptoSocket
{
private:
    SmartBuffer                  _input;
    SmartBuffer                  _output;
    SocketHandle                 _socket;
    std::unique_ptr<CryptoCodec> _codec;

    bool is_blocked(ssize_t res, int error) const {
        return ((res < 0) && ((error == EWOULDBLOCK) || (error == EAGAIN)));
    }
    HandshakeResult hs_try_flush();
    HandshakeResult hs_try_fill();
    ssize_t fill_input(); // -1/0/1 -> error/eof/ok
    ssize_t flush_all();  // -1/0 -> error/ok
public:
    CryptoCodecAdapter(SocketHandle socket, std::unique_ptr<CryptoCodec> codec)
        : _input(64 * 1024), _output(64 * 1024), _socket(std::move(socket)), _codec(std::move(codec)) {}
    int get_fd() const override { return _socket.get(); }
    HandshakeResult handshake() override;
    size_t min_read_buffer_size() const override { return _codec->min_decode_buffer_size(); }
    ssize_t read(char *buf, size_t len) override;
    ssize_t drain(char *, size_t) override;
    ssize_t write(const char *buf, size_t len) override;
    ssize_t flush() override;
};

} // namespace vespalib::net::tls
