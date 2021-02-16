// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "crypto_engine.h"
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/crypto/crypto_exception.h>
#include <vespa/vespalib/net/tls/authorization_mode.h>
#include <vespa/vespalib/net/tls/auto_reloading_tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/statistics.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/transport_security_options_reading.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vector>
#include <chrono>
#include <thread>
#include <xxhash.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.crypto_engine");

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
    void do_handshake_work() override {}
    size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override { return _socket.read(buf, len); }
    ssize_t drain(char *, size_t) override { return 0; }
    ssize_t write(const char *buf, size_t len) override { return _socket.write(buf, len); }
    ssize_t flush() override { return 0; }
    ssize_t half_close() override { return _socket.half_close(); }
};

class XorCryptoSocket : public CryptoSocket
{
private:
    static constexpr size_t CHUNK_SIZE = 16_Ki;
    enum class OP { READ_KEY, WRITE_KEY };
    std::vector<OP> _op_stack;
    char            _my_key;
    char            _peer_key;
    SmartBuffer     _input;
    SmartBuffer     _output;
    SocketHandle    _socket;

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
          _input(CHUNK_SIZE * 2),
          _output(CHUNK_SIZE * 2),
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
    void do_handshake_work() override {}
    size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override {
        if (_input.obtain().size == 0) {
            auto dst = _input.reserve(CHUNK_SIZE);
            ssize_t res = _socket.read(dst.data, dst.size);
            if (res > 0) {
                _input.commit(res);
            } else {
                return res; // eof/error
            }
        }
        return drain(buf, len);
    }
    ssize_t drain(char *buf, size_t len) override {
        auto src = _input.obtain();
        size_t frame = std::min(len, src.size);
        for (size_t i = 0; i < frame; ++i) {
            buf[i] = (src.data[i] ^ _my_key);
        }
        _input.evict(frame);
        return frame;
    }
    ssize_t write(const char *buf, size_t len) override {
        if (_output.obtain().size >= CHUNK_SIZE) {
            if (flush() < 0) {
                return -1;
            }
            if (_output.obtain().size > 0) {
                errno = EWOULDBLOCK;
                return -1;
            }
        }
        size_t frame = std::min(len, CHUNK_SIZE);
        auto dst = _output.reserve(frame);
        for (size_t i = 0; i < frame; ++i) {
            dst.data[i] = (buf[i] ^ _peer_key);
        }
        _output.commit(frame);
        return frame;
    }
    ssize_t flush() override {
        auto pending = _output.obtain();
        if (pending.size > 0) {
            ssize_t res = _socket.write(pending.data, pending.size);
            if (res > 0) {
                _output.evict(res);
                return 1; // progress
            } else {
                assert(res < 0);
                return -1; // error
            }
        }
        return 0; // done
    }
    ssize_t half_close() override {
        auto flush_res = flush();
        while (flush_res > 0) {
            flush_res = flush();
        }
        if (flush_res < 0) {
            return flush_res;
        }
        return _socket.half_close();
    }
};

using net::tls::AuthorizationMode;

AuthorizationMode authorization_mode_from_env() {
    const char* env = getenv("VESPA_TLS_INSECURE_AUTHORIZATION_MODE");
    vespalib::string mode = env ? env : "";
    if (mode == "enforce") {
        return AuthorizationMode::Enforce;
    } else if (mode == "log_only") {
        return AuthorizationMode::LogOnly;
    } else if (mode == "disable") {
        return AuthorizationMode::Disable;
    } else if (!mode.empty()) {
        LOG(warning, "VESPA_TLS_INSECURE_AUTHORIZATION_MODE environment variable has "
                     "an unsupported value (%s). Falling back to 'enforce'", mode.c_str());
    }
    return AuthorizationMode::Enforce;
}

CryptoEngine::SP create_default_crypto_engine() {
    const char *env = getenv("VESPA_TLS_CONFIG_FILE");
    vespalib::string cfg_file = env ? env : "";
    if (cfg_file.empty()) {
        return std::make_shared<NullCryptoEngine>();
    }
    auto mode = authorization_mode_from_env();
    LOG(debug, "Using TLS crypto engine with config file '%s'", cfg_file.c_str());
    auto tls = std::make_shared<net::tls::AutoReloadingTlsCryptoEngine>(cfg_file, mode);
    env = getenv("VESPA_TLS_INSECURE_MIXED_MODE");
    vespalib::string mixed_mode = env ? env : "";
    if (mixed_mode == "plaintext_client_mixed_server") {
        LOG(debug, "TLS insecure mixed-mode activated: plaintext client, mixed server");
        return std::make_shared<MaybeTlsCryptoEngine>(std::move(tls), false);
    } else if (mixed_mode == "tls_client_mixed_server") {
        LOG(debug, "TLS insecure mixed-mode activated: TLS client, mixed server");
        return std::make_shared<MaybeTlsCryptoEngine>(std::move(tls), true);
    } else if (!mixed_mode.empty() && (mixed_mode != "tls_client_tls_server")) {
        LOG(warning, "bad TLS insecure mixed-mode specified: '%s' (ignoring)",
            mixed_mode.c_str());
    }
    return tls;
}

CryptoEngine::SP try_create_default_crypto_engine() {
    try {
        return create_default_crypto_engine();
    } catch (crypto::CryptoException &e) {
        LOG(error, "failed to create default crypto engine: %s", e.what());
        std::_Exit(78);
    }
}

} // namespace vespalib::<unnamed>

CryptoEngine::~CryptoEngine() = default;

CryptoEngine::SP
CryptoEngine::get_default()
{
    static const CryptoEngine::SP shared_default = try_create_default_crypto_engine();
    return shared_default;
}

CryptoSocket::UP
NullCryptoEngine::create_client_crypto_socket(SocketHandle socket, const SocketSpec &)
{
    net::tls::ConnectionStatistics::get(false).inc_insecure_connections();
    return std::make_unique<NullCryptoSocket>(std::move(socket));
}

CryptoSocket::UP
NullCryptoEngine::create_server_crypto_socket(SocketHandle socket)
{
    net::tls::ConnectionStatistics::get(true).inc_insecure_connections();
    return std::make_unique<NullCryptoSocket>(std::move(socket));
}

CryptoSocket::UP
XorCryptoEngine::create_client_crypto_socket(SocketHandle socket, const SocketSpec &)
{
    return std::make_unique<XorCryptoSocket>(std::move(socket), false);
}

CryptoSocket::UP
XorCryptoEngine::create_server_crypto_socket(SocketHandle socket)
{
    return std::make_unique<XorCryptoSocket>(std::move(socket), true);
}

} // namespace vespalib
