// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maybe_tls_crypto_socket.h"
#include "statistics.h"
#include "tls_crypto_socket.h"
#include "protocol_snooping.h"
#include "crypto_codec_adapter.h"
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/util/size_literals.h>

namespace vespalib {

namespace {

class MyCryptoSocket : public CryptoSocket
{
private:
    static constexpr size_t SNOOP_SIZE = net::tls::snooping::min_header_bytes_to_observe();

    CryptoSocket::UP                        &_self;
    SocketHandle                             _socket;
    std::shared_ptr<AbstractTlsCryptoEngine> _factory;
    SmartBuffer                              _buffer;

    bool is_blocked(ssize_t res, int error) const {
        return ((res < 0) && ((error == EWOULDBLOCK) || (error == EAGAIN)));
    }

    bool looksLikeTlsToMe(const char *buf) {
        return (net::tls::snooping::snoop_client_hello_header(buf) == net::tls::snooping::TlsSnoopingResult::ProbablyTls);
    }

public:
    MyCryptoSocket(CryptoSocket::UP &self, SocketHandle socket, std::shared_ptr<AbstractTlsCryptoEngine> tls_engine)
        : _self(self), _socket(std::move(socket)), _factory(std::move(tls_engine)), _buffer(4_Ki)
    {
        static_assert(SNOOP_SIZE == 8);
    }
    int get_fd() const override { return _socket.get(); }
    HandshakeResult handshake() override {
        if (_factory) {
            auto dst = _buffer.reserve(SNOOP_SIZE);
            ssize_t res = _socket.read(dst.data, dst.size);
            if (res > 0) {
                _buffer.commit(res);
            } else if (!is_blocked(res, errno)) {
                return HandshakeResult::FAIL;
            }
            auto src = _buffer.obtain();
            if (src.size < SNOOP_SIZE) {
                return HandshakeResult::NEED_READ;                
            }
            if (looksLikeTlsToMe(src.data)) {
                CryptoSocket::UP &self = _self; // need copy due to self destruction
                auto tls_codec = _factory->create_tls_server_crypto_codec(_socket);
                auto tls_socket = std::make_unique<net::tls::CryptoCodecAdapter>(std::move(_socket), std::move(tls_codec));
                tls_socket->inject_read_data(src.data, src.size);
                self = std::move(tls_socket);
                return self->handshake();
            } else {
                net::tls::ConnectionStatistics::get(true).inc_insecure_connections();
                _factory.reset();
            }
        }
        return HandshakeResult::DONE;
    }
    void do_handshake_work() override {}
    size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override {
        int drain_result = drain(buf, len);
        if (drain_result != 0) {
            return drain_result;
        }
        return _socket.read(buf, len);
    }
    ssize_t drain(char *buf, size_t len) override {
        auto src = _buffer.obtain();
        size_t frame = std::min(len, src.size);
        if (frame > 0) {
            memcpy(buf, src.data, frame);
            _buffer.evict(frame);
            _buffer.drop_if_empty();
        }
        return frame;
    }
    ssize_t write(const char *buf, size_t len) override { return _socket.write(buf, len); }
    ssize_t flush() override { return 0; }
    ssize_t half_close() override { return _socket.half_close(); }
    void drop_empty_buffers() override {}
};

} // namespace vespalib::<unnamed>

MaybeTlsCryptoSocket::MaybeTlsCryptoSocket(SocketHandle socket, std::shared_ptr<AbstractTlsCryptoEngine> tls_engine)
    : _socket(std::make_unique<MyCryptoSocket>(_socket, std::move(socket), std::move(tls_engine)))
{
}

std::unique_ptr<net::ConnectionAuthContext> MaybeTlsCryptoSocket::make_auth_context() {
    return _socket->make_auth_context();
}

} // namespace vespalib
