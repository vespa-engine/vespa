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

    // These assumptions are cheekily hoisted from gRPC.
    // As is mentioned there, the max protocol overhead per frame is not available
    // dynamically, so an educated guess is made.
    static constexpr size_t MaximumTlsFrameSize = 16*1024;
    static constexpr size_t MaximumTlsFrameProtocolOverhead = 100;
    static constexpr size_t MaximumFramePayloadSize = MaximumTlsFrameSize - MaximumTlsFrameProtocolOverhead;

    size_t min_encode_buffer_size() const noexcept override {
        return MaximumTlsFrameSize;
    }
    size_t min_decode_buffer_size() const noexcept override {
        // Technically this would be MaximumFramePayloadSize, but we like power
        // of two numbers for buffer sizes, yes we do.
        return MaximumTlsFrameSize;
    }

    HandshakeResult handshake(const char* from_peer, size_t from_peer_buf_size,
                              char* to_peer, size_t to_peer_buf_size) noexcept override;

    EncodeResult encode(const char* plaintext, size_t plaintext_size,
                        char* ciphertext, size_t ciphertext_size) noexcept override;
    DecodeResult decode(const char* ciphertext, size_t ciphertext_size,
                        char* plaintext, size_t plaintext_size) noexcept override;
private:
    /*
     * Returns
     *   n > 0 if n bytes written to `to_peer`. Always <= to_peer_buf_size
     *   n == 0 if no bytes pending in output BIO
     *   n < 0 on error
     */
    int drain_outgoing_network_bytes_if_any(char *to_peer, size_t to_peer_buf_size) noexcept;
    /*
     * Returns
     *   n > 0 if n bytes written to `ciphertext`. Always <= ciphertext_size
     *   n == 0 if no bytes pending in input BIO
     *   n < 0 on error
     */
    int consume_peer_input_bytes(const char* ciphertext, size_t ciphertext_size) noexcept;
    HandshakeResult do_handshake_and_consume_peer_input_bytes(const char *from_peer, size_t from_peer_buf_size) noexcept;
    DecodeResult drain_and_produce_plaintext_from_ssl(char* plaintext, size_t plaintext_size) noexcept;
};

}
