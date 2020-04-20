// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include "crypto_socket.h"
#include "crypto_engine.h"
#include <vespa/vespalib/data/smart_buffer.h>

namespace vespalib {

/**
 * A synchronous wrapper around a CryptoSocket. The create function
 * will perform connection handshaking. If handshaking fails, an empty
 * unique pointer is returned. The read function blocks until at least
 * 1 byte of data can be read, EOF is reached or an error occurs. The
 * write function blocks until all data can be written or an error
 * occurs. Note that this class is not thread-safe.
 **/
class SyncCryptoSocket
{
public:
    using UP = std::unique_ptr<SyncCryptoSocket>;
private:
    CryptoSocket::UP _socket;
    SmartBuffer _buffer;
    SyncCryptoSocket(CryptoSocket::UP socket) : _socket(std::move(socket)), _buffer(0) {}
    static UP create(CryptoSocket::UP socket);
public:
    ~SyncCryptoSocket();
    ssize_t read(char *buf, size_t len);
    ssize_t write(const char *buf, size_t len);
    ssize_t half_close();
    static UP create_client(CryptoEngine &engine, SocketHandle socket, const SocketSpec &spec);
    static UP create_server(CryptoEngine &engine, SocketHandle socket);
};

} // namespace vespalib
