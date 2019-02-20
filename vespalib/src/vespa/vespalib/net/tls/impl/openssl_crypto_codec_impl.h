// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "openssl_typedefs.h"
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <memory>
#include <optional>

namespace vespalib::net::tls { struct TlsContext; }

namespace vespalib::net::tls::impl {

class OpenSslTlsContextImpl;

/*
 * Frame-level OpenSSL-backed TLSv1.2/TLSv1.3 (depending on OpenSSL version)
 * crypto codec implementation.
 *
 * NOT generally thread safe per instance, but independent instances may be
 * used by different threads safely. One exception is that handshake() and
 * do_handshake_work() may be called from different threads, as long as it
 * happens with appropriate data visibility synchronization and not concurrently.
 */
class OpenSslCryptoCodecImpl : public CryptoCodec {

    struct DeferredHandshakeParams {
        const char* from_peer          = nullptr;
        size_t      from_peer_buf_size = 0;
        char*       to_peer            = nullptr;
        size_t      to_peer_buf_size   = 0;

        DeferredHandshakeParams(const char* from_peer_, size_t from_peer_buf_size_,
                                char* to_peer_, size_t to_peer_buf_size_) noexcept
            : from_peer(from_peer_),
              from_peer_buf_size(from_peer_buf_size_),
              to_peer(to_peer_),
              to_peer_buf_size(to_peer_buf_size_)
        {}

        DeferredHandshakeParams(const DeferredHandshakeParams&) noexcept = default;
        DeferredHandshakeParams& operator=(const DeferredHandshakeParams&) noexcept = default;
    };

    // The context maintains shared verification callback state, so it must be
    // kept alive explictly for at least as long as any codecs.
    std::shared_ptr<OpenSslTlsContextImpl> _ctx;
    SslPtr _ssl;
    ::BIO* _input_bio;  // Owned by _ssl
    ::BIO* _output_bio; // Owned by _ssl
    Mode _mode;
    std::optional<DeferredHandshakeParams> _deferred_handshake_params;
    std::optional<HandshakeResult> _deferred_handshake_result;
public:
    OpenSslCryptoCodecImpl(std::shared_ptr<OpenSslTlsContextImpl> ctx, Mode mode);
    ~OpenSslCryptoCodecImpl() override;

    /*
     * From RFC 8449 (Record Size Limit Extension for TLS), section 1:
     *   "TLS versions 1.2 [RFC5246] and earlier permit senders to
     *    generate records 16384 octets in size, plus any expansion
     *    from compression and protection up to 2048 octets (though
     *    typically this expansion is only 16 octets). TLS 1.3 reduces
     *    the allowance for expansion to 256 octets."
     *
     * We may be on TLSv1.2, so make room for the worst case.
     */
    static constexpr size_t MaximumTlsFrameSize = 16384 + 2048;
    static constexpr size_t MaximumFramePlaintextSize = 16384;

    size_t min_encode_buffer_size() const noexcept override {
        return MaximumTlsFrameSize;
    }
    size_t min_decode_buffer_size() const noexcept override {
        return MaximumFramePlaintextSize;
    }

    HandshakeResult handshake(const char* from_peer, size_t from_peer_buf_size,
                              char* to_peer, size_t to_peer_buf_size) noexcept override;

    void do_handshake_work() noexcept override;

    EncodeResult encode(const char* plaintext, size_t plaintext_size,
                        char* ciphertext, size_t ciphertext_size) noexcept override;
    DecodeResult decode(const char* ciphertext, size_t ciphertext_size,
                        char* plaintext, size_t plaintext_size) noexcept override;
    EncodeResult half_close(char* ciphertext, size_t ciphertext_size) noexcept override;
private:
    HandshakeResult do_handshake_and_consume_peer_input_bytes() noexcept;
    DecodeResult drain_and_produce_plaintext_from_ssl(char* plaintext, size_t plaintext_size) noexcept;
    // Precondition: read_result < 0
    DecodeResult remap_ssl_read_failure_to_decode_result(int read_result) noexcept;
};

}
