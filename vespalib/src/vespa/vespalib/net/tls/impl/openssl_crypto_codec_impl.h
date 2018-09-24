// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "openssl_typedefs.h"
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <memory>

namespace vespalib::net::tls { class TlsContext; }

namespace vespalib::net::tls::impl {

/*
 * Frame-level OpenSSL-backed TLSv1.2 crypto codec implementation.
 *
 * Currently has sub-optimal buffer management, and is mostly intended
 * as a starting point.
 *
 * NOT thread safe per instance, but independent instances may be
 * used by different threads safely.
 */
class OpenSslCryptoCodecImpl : public CryptoCodec {
    SslPtr _ssl;
    ::BIO* _input_bio;  // Owned by _ssl
    ::BIO* _output_bio; // Owned by _ssl
    Mode _mode;
public:
    OpenSslCryptoCodecImpl(::SSL_CTX& ctx, Mode mode);

    /*
     * From RFC 8449 (Record Size Limit Extension for TLS), section 1:
     *   "TLS versions 1.2 [RFC5246] and earlier permit senders to
     *    generate records 16384 octets in size, plus any expansion
     *    from compression and protection up to 2048 octets (though
     *    typically this expansion is only 16 octets). TLS 1.3 reduces
     *    the allowance for expansion to 256 octets."
     *
     * We're on TLSv1.2, so make room for the worst case.
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

    EncodeResult encode(const char* plaintext, size_t plaintext_size,
                        char* ciphertext, size_t ciphertext_size) noexcept override;
    DecodeResult decode(const char* ciphertext, size_t ciphertext_size,
                        char* plaintext, size_t plaintext_size) noexcept override;
private:
    HandshakeResult do_handshake_and_consume_peer_input_bytes() noexcept;
    DecodeResult drain_and_produce_plaintext_from_ssl(char* plaintext, size_t plaintext_size) noexcept;
};

}
