// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "async_crypto_socket.h"
#include <vespa/vespalib/net/tls/protocol_snooping.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <vespa/vespalib/data/smart_buffer.h>

namespace vespalib::coro {

namespace {

using net::tls::CryptoCodec;
using net::tls::HandshakeResult;
using net::tls::EncodeResult;
using net::tls::DecodeResult;

struct InvalidSocket : AsyncCryptoSocket {
    Lazy<ssize_t> read(char *, size_t) override { co_return -EINVAL; }
    Lazy<ssize_t> write(const char *, size_t) override { co_return -EINVAL; }
};

struct RawSocket : AsyncCryptoSocket {
    AsyncIo::SP async;
    SocketHandle handle;
    RawSocket(AsyncIo &async_in, SocketHandle handle_in)
        : async(async_in.shared_from_this()), handle(std::move(handle_in)) {}
    RawSocket(RawSocket &&) noexcept;
    ~RawSocket() override;
    Lazy<ssize_t> read(char *buf, size_t len) override {
        return async->read(handle, buf, len);
    }
    Lazy<ssize_t> write(const char *buf, size_t len) override {
        return async->write(handle, buf, len);
    }
};

RawSocket::~RawSocket() = default;

struct SnoopedRawSocket : AsyncCryptoSocket {
    AsyncIo::SP async;
    SocketHandle handle;
    SmartBuffer data;
    SnoopedRawSocket(AsyncIo &async_in, SocketHandle handle_in)
        : async(async_in.shared_from_this()), handle(std::move(handle_in)), data(0) {}
    SnoopedRawSocket(SnoopedRawSocket &&) noexcept;
    ~SnoopedRawSocket() override;
    void inject_data(const char *buf, size_t len) {
        if (len > 0) {
            auto dst = data.reserve(len);
            memcpy(dst.data, buf, len);
            data.commit(len);
        }
    }
    Lazy<ssize_t> read_from_buffer(char *buf, size_t len) {
        auto src = data.obtain();
        size_t frame = std::min(len, src.size);
        if (frame > 0) {
            memcpy(buf, src.data, frame);
            data.evict(frame);
            data.drop_if_empty();
        }
        co_return frame;
    }
    Lazy<ssize_t> read(char *buf, size_t len) override {
        if (data.empty()) {
            return async->read(handle, buf, len);
        } else {
            return read_from_buffer(buf, len);
        }
    }
    Lazy<ssize_t> write(const char *buf, size_t len) override {
        return async->write(handle, buf, len);
    }
};

SnoopedRawSocket::~SnoopedRawSocket() = default;

struct TlsSocket : AsyncCryptoSocket {
    AsyncIo::SP async;
    SocketHandle handle;
    std::unique_ptr<CryptoCodec> codec;
    SmartBuffer app_input;
    SmartBuffer enc_input;
    SmartBuffer enc_output;
    TlsSocket(AsyncIo &async_in, SocketHandle handle_in, std::unique_ptr<CryptoCodec> codec_in)
      : async(async_in.shared_from_this()), handle(std::move(handle_in)), codec(std::move(codec_in)),
        app_input(0), enc_input(0), enc_output(0) {}
    TlsSocket(TlsSocket &&) noexcept;
    ~TlsSocket() override;
    void inject_enc_input(const char *buf, size_t len) {
        if (len > 0) {
            auto dst = enc_input.reserve(len);
            memcpy(dst.data, buf, len);
            enc_input.commit(len);
        }
    }
    Lazy<bool> flush_enc_output() {
        while (!enc_output.empty()) {
            auto pending = enc_output.obtain();
            auto res = co_await async->write(handle, pending.data, pending.size);
            if (res > 0) {
                enc_output.evict(res);
            } else {
                co_return false;
            }
        }
        co_return true;
    }
    Lazy<bool> fill_enc_input() {
        auto dst = enc_input.reserve(codec->min_encode_buffer_size());
        ssize_t res = co_await async->read(handle, dst.data, dst.size);
        if (res > 0) {
            enc_input.commit(res);
            co_return true;
        } else {
            co_return false;
        }
    }
    Lazy<bool> handshake() {
        for (;;) {
            auto in = enc_input.obtain();
            auto out = enc_output.reserve(codec->min_encode_buffer_size());
            auto hs_res = codec->handshake(in.data, in.size, out.data, out.size);
            enc_input.evict(hs_res.bytes_consumed);
            enc_output.commit(hs_res.bytes_produced);
            switch (hs_res.state) {
            case ::vespalib::net::tls::HandshakeResult::State::Failed: co_return false;
            case ::vespalib::net::tls::HandshakeResult::State::Done: co_return co_await flush_enc_output();
            case ::vespalib::net::tls::HandshakeResult::State::NeedsWork:
                codec->do_handshake_work();
                break;
            case ::vespalib::net::tls::HandshakeResult::State::NeedsMorePeerData:
                bool flush_ok = co_await flush_enc_output();
                if (!flush_ok) {
                    co_return false;
                }
                bool fill_ok = co_await fill_enc_input();
                if (!fill_ok) {
                    co_return false;
                }
            }
        }
    }
    Lazy<ssize_t> read(char *buf, size_t len) override {
        while (app_input.empty()) {
            auto src = enc_input.obtain();
            auto dst = app_input.reserve(codec->min_decode_buffer_size());
            auto res = codec->decode(src.data, src.size, dst.data, dst.size);
            app_input.commit(res.bytes_produced);
            enc_input.evict(res.bytes_consumed);
            if (res.failed()) {
                co_return -EIO;
            }
            if (res.closed()) {
                co_return 0;
            }
            if (app_input.empty()) {
                bool fill_ok = co_await fill_enc_input();
                if (!fill_ok) {
                    co_return -EIO;
                }
            }
        }
        auto src = app_input.obtain();
        size_t frame = std::min(len, src.size);
        if (frame > 0) {
            memcpy(buf, src.data, frame);
            app_input.evict(frame);
        }
        co_return frame;
    }
    Lazy<ssize_t> write(const char *buf, size_t len) override {
        auto dst = enc_output.reserve(codec->min_encode_buffer_size());
        auto res = codec->encode(buf, len, dst.data, dst.size);
        if (res.failed) {
            co_return -EIO;
        }
        enc_output.commit(res.bytes_produced);
        bool flush_ok = co_await flush_enc_output();
        if (!flush_ok) {
            co_return -EIO;
        }
        co_return res.bytes_consumed;
    }
};

TlsSocket::~TlsSocket() = default;

Lazy<AsyncCryptoSocket::UP> try_handshake(std::unique_ptr<TlsSocket> tls_socket) {
    bool hs_ok = co_await tls_socket->handshake();
    if (hs_ok) {
        co_return std::move(tls_socket);
    } else {
        co_return std::make_unique<InvalidSocket>();
    }
}

Lazy<AsyncCryptoSocket::UP> accept_tls(AsyncIo &async, AbstractTlsCryptoEngine &crypto, SocketHandle handle) {
    auto tls_codec = crypto.create_tls_server_crypto_codec(handle);
    auto tls_socket = std::make_unique<TlsSocket>(async, std::move(handle), std::move(tls_codec));
    co_return co_await try_handshake(std::move(tls_socket));
}

Lazy<AsyncCryptoSocket::UP> accept_maybe_tls(AsyncIo &async, AbstractTlsCryptoEngine &crypto, SocketHandle handle) {
    char buf[net::tls::snooping::min_header_bytes_to_observe()];
    memset(buf, 0, sizeof(buf));
    size_t snooped = 0;
    while (snooped < sizeof(buf)) {
        auto res = co_await async.read(handle, buf + snooped, sizeof(buf) - snooped);
        if (res <= 0) {
            co_return std::make_unique<InvalidSocket>();
        }
        snooped += res;
    }
    if (net::tls::snooping::snoop_client_hello_header(buf) == net::tls::snooping::TlsSnoopingResult::ProbablyTls) {
        auto tls_codec = crypto.create_tls_server_crypto_codec(handle);
        auto tls_socket = std::make_unique<TlsSocket>(async, std::move(handle), std::move(tls_codec));
        tls_socket->inject_enc_input(buf, snooped);
        co_return co_await try_handshake(std::move(tls_socket));
    } else {
        auto plain_socket = std::make_unique<SnoopedRawSocket>(async, std::move(handle));
        plain_socket->inject_data(buf, snooped);
        co_return plain_socket;
    }
}

Lazy<AsyncCryptoSocket::UP> connect_tls(AsyncIo &async, AbstractTlsCryptoEngine &crypto, SocketHandle handle, SocketSpec spec) {
    auto tls_codec = crypto.create_tls_client_crypto_codec(handle, spec);
    auto tls_socket = std::make_unique<TlsSocket>(async, std::move(handle), std::move(tls_codec));
    co_return co_await try_handshake(std::move(tls_socket));
}

}

AsyncCryptoSocket::~AsyncCryptoSocket() = default;

Lazy<AsyncCryptoSocket::UP>
AsyncCryptoSocket::accept(AsyncIo &async, CryptoEngine &crypto,
                          SocketHandle handle)
{
    if (dynamic_cast<NullCryptoEngine*>(&crypto)) {
        co_return std::make_unique<RawSocket>(async, std::move(handle));
    }
    if (auto *tls_engine = dynamic_cast<AbstractTlsCryptoEngine*>(&crypto)) {
        if (tls_engine->always_use_tls_when_server()) {
            co_return co_await accept_tls(async, *tls_engine, std::move(handle));
        } else {
            co_return co_await accept_maybe_tls(async, *tls_engine, std::move(handle));
        }
    }
    co_return std::make_unique<InvalidSocket>();
}

Lazy<AsyncCryptoSocket::UP>
AsyncCryptoSocket::connect(AsyncIo &async, CryptoEngine &crypto,
                           SocketHandle handle, SocketSpec spec)
{
    if (dynamic_cast<NullCryptoEngine*>(&crypto)) {
        (void) spec; // no SNI for plaintext sockets
        co_return std::make_unique<RawSocket>(async, std::move(handle));
    }
    if (auto *tls_engine = dynamic_cast<AbstractTlsCryptoEngine*>(&crypto)) {
        if (tls_engine->use_tls_when_client()) {
            co_return co_await connect_tls(async, *tls_engine, std::move(handle), spec);
        } else {
            co_return std::make_unique<RawSocket>(async, std::move(handle));
        }
    }
    co_return std::make_unique<InvalidSocket>();
}

}
