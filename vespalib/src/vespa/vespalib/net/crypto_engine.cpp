// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "crypto_engine.h"
#include <vector>
#include <chrono>
#include <thread>
#include <vespa/vespalib/xxhash/xxhash.h>
#include <assert.h>

namespace vespalib {

namespace {

struct HashState {
    using clock = std::chrono::high_resolution_clock;
    const void       *self;
    clock::time_point now;
    HashState() : self(this), now(clock::now()) {}
};

char gen_key() {
    HashState hash_state;
    std::this_thread::sleep_for(std::chrono::microseconds(42));
    return XXH64(&hash_state, sizeof(hash_state), 0);
}

class NullCryptoSocket : public CryptoSocket
{
private:
    SocketHandle _socket;
public:
    NullCryptoSocket(SocketHandle socket) : _socket(std::move(socket)) {}
    int get_fd() const override { return _socket.get(); }
    HandshakeResult handshake() override { return HandshakeResult::DONE; }
    size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override { return _socket.read(buf, len); }
    ssize_t drain(char *, size_t) override { return 0; }
    ssize_t write(const char *buf, size_t len) override { return _socket.write(buf, len); }
    ssize_t flush() override { return 0; }
};

class XorCryptoSocket : public CryptoSocket
{
private:
    static constexpr size_t CHUNK_SIZE = 4096;
    enum class OP { READ_KEY, WRITE_KEY };
    std::vector<OP> _op_stack;
    char              _my_key;
    char              _peer_key;
    std::vector<char> _readbuf;
    std::vector<char> _writebuf;    
    SocketHandle      _socket;

    bool is_blocked(ssize_t res, int error) const {
        return ((res < 0) && ((error == EWOULDBLOCK) || (error == EAGAIN)));
    }

    HandshakeResult try_read_key() {
        ssize_t res = _socket.read(&_peer_key, 1);
        if (is_blocked(res, errno)) {
            return HandshakeResult::NEED_READ;
        }
        return (res == 1)
            ? HandshakeResult::DONE
            : HandshakeResult::FAIL;
    }

    HandshakeResult try_write_key() {
        ssize_t res = _socket.write(&_my_key, 1);
        if (is_blocked(res, errno)) {
            return HandshakeResult::NEED_WRITE;
        }
        return (res == 1)
            ? HandshakeResult::DONE
            : HandshakeResult::FAIL;
    }

    HandshakeResult perform_hs_op(OP op) {
        if (op == OP::READ_KEY) {
            return try_read_key();
        } else {
            assert(op == OP::WRITE_KEY);
            return try_write_key();
        }
    }

public:
    XorCryptoSocket(SocketHandle socket, bool is_server)
        : _op_stack(is_server
                    ? std::vector<OP>({OP::WRITE_KEY, OP::READ_KEY})
                    : std::vector<OP>({OP::READ_KEY, OP::WRITE_KEY})),
          _my_key(gen_key()),
          _peer_key(0),
          _readbuf(),
          _writebuf(),
          _socket(std::move(socket)) {}
    int get_fd() const override { return _socket.get(); }
    HandshakeResult handshake() override {
        while (!_op_stack.empty()) {
            HandshakeResult partial_result = perform_hs_op(_op_stack.back());
            if (partial_result != HandshakeResult::DONE) {
                return partial_result;
            }
            _op_stack.pop_back();
        }
        return HandshakeResult::DONE;
    }
    size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override {
        if (_readbuf.empty()) {
            _readbuf.resize(CHUNK_SIZE);
            ssize_t res = _socket.read(&_readbuf[0], _readbuf.size());
            if (res > 0) {
                _readbuf.resize(res);
            } else {
                _readbuf.clear();
                return res;
            }
        }
        return drain(buf, len);
    }
    ssize_t drain(char *buf, size_t len) override {
        size_t frame = std::min(len, _readbuf.size());
        for (size_t i = 0; i < frame; ++i) {
            buf[i] = (_readbuf[i] ^ _my_key);
        }
        _readbuf.erase(_readbuf.begin(), _readbuf.begin() + frame);
        return frame;
    }
    ssize_t write(const char *buf, size_t len) override {
        ssize_t res = flush();
        while (res > 0) {
            res = flush();
        }
        if (res < 0) {
            return res;
        }
        size_t frame = std::min(len, CHUNK_SIZE);
        for (size_t i = 0; i < frame; ++i) {
            _writebuf.push_back(buf[i] ^ _peer_key);
        }
        return frame;
    }
    ssize_t flush() override {
        if (!_writebuf.empty()) {
            ssize_t res = _socket.write(&_writebuf[0], _writebuf.size());
            if (res > 0) {
                _writebuf.erase(_writebuf.begin(), _writebuf.begin() + res);                
            } else {
                assert(res < 0);
            }
            return res;
        }
        return 0;
    }
};

CryptoEngine::SP create_default_crypto_engine() {
    // TODO: check VESPA_TLS_CONFIG_FILE here
    // return std::make_shared<XorCryptoEngine>();
    return std::make_shared<NullCryptoEngine>();
}

} // namespace vespalib::<unnamed>

std::mutex CryptoEngine::_shared_lock;
CryptoEngine::SP CryptoEngine::_shared_default(nullptr);

CryptoEngine::~CryptoEngine() = default;

CryptoEngine::SP
CryptoEngine::get_default()
{
    std::lock_guard guard(_shared_lock);
    if (!_shared_default) {
        _shared_default = create_default_crypto_engine();
    }
    return _shared_default;
}

CryptoSocket::UP
NullCryptoEngine::create_crypto_socket(SocketHandle socket, bool)
{
    return std::make_unique<NullCryptoSocket>(std::move(socket));
}

CryptoSocket::UP
XorCryptoEngine::create_crypto_socket(SocketHandle socket, bool is_server)
{
    return std::make_unique<XorCryptoSocket>(std::move(socket), is_server);
}

} // namespace vespalib
