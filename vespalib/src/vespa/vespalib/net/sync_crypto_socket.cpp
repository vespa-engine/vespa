// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sync_crypto_socket.h"
#include <cassert>

namespace vespalib {

namespace {

ssize_t read_from_buffer(SmartBuffer &src, char *dst, size_t len) {
    auto data = src.obtain();
    auto chunk = std::min(len, data.size);
    if (chunk > 0) {
        memcpy(dst, data.data, chunk);
        src.evict(chunk);
    }
    return chunk;    
}

bool is_blocked(ssize_t res, int error) {
    return ((res < 0) && ((error == EWOULDBLOCK) || (error == EAGAIN)));
}

void set_blocking(int fd) {
    SocketHandle handle(fd);
    handle.set_blocking(true);
    handle.release();
}

} // namespace vespalib::<unnamed>

SyncCryptoSocket::UP
SyncCryptoSocket::create(CryptoSocket::UP socket)
{
    set_blocking(socket->get_fd());
    for (;;) {
        switch (socket->handshake()) {
        case CryptoSocket::HandshakeResult::FAIL:
            return std::unique_ptr<SyncCryptoSocket>(nullptr);
        case CryptoSocket::HandshakeResult::DONE:
            return UP(new SyncCryptoSocket(std::move(socket)));
        case CryptoSocket::HandshakeResult::NEED_READ:
        case CryptoSocket::HandshakeResult::NEED_WRITE:
            break;
        case CryptoSocket::HandshakeResult::NEED_WORK:
            socket->do_handshake_work();
        }
    }
}

SyncCryptoSocket::~SyncCryptoSocket() = default;

ssize_t
SyncCryptoSocket::read(char *buf, size_t len)
{
    if (_buffer.obtain().size > 0) {
        return read_from_buffer(_buffer, buf, len);
    } else if (len < _socket->min_read_buffer_size()) {
        auto dst = _buffer.reserve(_socket->min_read_buffer_size());
        auto res = _socket->read(dst.data, dst.size);
        while (is_blocked(res, errno)) {
            res = _socket->read(dst.data, dst.size);
        }
        if (res <= 0) {
            return res;
        }
        _buffer.commit(res);
        return read_from_buffer(_buffer, buf, len);
    } else {
        auto res = _socket->read(buf, len);
        while (is_blocked(res, errno)) {
            res = _socket->read(buf, len);
        }
        return res;
    }
}

ssize_t
SyncCryptoSocket::write(const char *buf, size_t len)
{
    size_t written = 0;
    while (written < len) {
        auto write_res = _socket->write(buf + written, len - written);
        assert(write_res != 0);
        if (write_res > 0) {
            written += write_res;
        } else if (!is_blocked(write_res, errno)) {
            return write_res;
        }
    }
    auto flush_res = _socket->flush();
    while ((flush_res > 0) || is_blocked(flush_res, errno)) {
        flush_res = _socket->flush();
    }
    if (flush_res < 0) {
        return flush_res;
    }
    return written;
}

ssize_t
SyncCryptoSocket::half_close()
{
    auto half_close_res = _socket->half_close();
    while (is_blocked(half_close_res, errno)) {
        half_close_res = _socket->half_close();
    }
    return half_close_res;
}

SyncCryptoSocket::UP
SyncCryptoSocket::create_client(CryptoEngine &engine, SocketHandle socket, const SocketSpec &spec)
{
    return create(engine.create_client_crypto_socket(std::move(socket), spec));
}

SyncCryptoSocket::UP
SyncCryptoSocket::create_server(CryptoEngine &engine, SocketHandle socket)
{
    return create(engine.create_server_crypto_socket(std::move(socket)));
}

} // namespace vespalib
