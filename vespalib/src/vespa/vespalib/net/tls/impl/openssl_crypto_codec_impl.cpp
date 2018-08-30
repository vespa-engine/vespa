// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "openssl_crypto_codec_impl.h"
#include "openssl_tls_context_impl.h"
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <vespa/vespalib/net/tls/crypto_exception.h>
#include <mutex>
#include <vector>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <openssl/ssl.h>
#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/pem.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.tls.openssl_crypto_codec_impl");

#if (OPENSSL_VERSION_NUMBER < 0x10000000L)
// < 1.0 requires explicit thread ID callback support.
#  error "Provided OpenSSL version is too darn old, need at least 1.0"
#endif

/*
 * Beware all ye who dare enter, for this is OpenSSL integration territory.
 * Dragons are known to roam the skies. Strange whispers are heard at night
 * in the mist-covered lands where the forest meets the lake. Rumors of a
 * tome that contains best practices and excellent documentation are heard
 * at the local inn, but no one seems to know where it exists, or even if
 * it ever existed. Be it best that people carry on with their lives and
 * pretend to not know of the beasts that lurk beyond where the torch's
 * light fades and turns to all-enveloping darkness.
 */

namespace vespalib::net::tls::impl {

namespace {

const char* ssl_error_to_str(int ssl_error) noexcept {
    // From https://www.openssl.org/docs/manmaster/man3/SSL_get_error.html
    // Our code paths shouldn't trigger most of these, but included for completeness
    switch (ssl_error) {
        case SSL_ERROR_NONE:
            return "SSL_ERROR_NONE";
        case SSL_ERROR_ZERO_RETURN:
            return "SSL_ERROR_ZERO_RETURN";
        case SSL_ERROR_WANT_READ:
            return "SSL_ERROR_WANT_READ";
        case SSL_ERROR_WANT_WRITE:
            return "SSL_ERROR_WANT_WRITE";
        case SSL_ERROR_WANT_CONNECT:
            return "SSL_ERROR_WANT_CONNECT";
        case SSL_ERROR_WANT_ACCEPT:
            return "SSL_ERROR_WANT_ACCEPT";
        case SSL_ERROR_WANT_X509_LOOKUP:
            return "SSL_ERROR_WANT_X509_LOOKUP";
#if (OPENSSL_VERSION_NUMBER >= 0x10100000L)
        case SSL_ERROR_WANT_ASYNC:
            return "SSL_ERROR_WANT_ASYNC";
        case SSL_ERROR_WANT_ASYNC_JOB:
            return "SSL_ERROR_WANT_ASYNC_JOB";
#endif
#if (OPENSSL_VERSION_NUMBER >= 0x10101000L)
        case SSL_ERROR_WANT_CLIENT_HELLO_CB:
            return "SSL_ERROR_WANT_CLIENT_HELLO_CB";
#endif
        case SSL_ERROR_SYSCALL:
            return "SSL_ERROR_SYSCALL";
        case SSL_ERROR_SSL:
            return "SSL_ERROR_SSL";
        default:
            return "Unknown SSL error code";
    }
}

HandshakeResult handshake_consumed_bytes_and_needs_more_peer_data(size_t consumed) noexcept {
    return {consumed, 0, HandshakeResult::State::NeedsMorePeerData};
}

HandshakeResult handshake_produced_bytes_and_needs_more_peer_data(size_t produced) noexcept {
    return {0, produced, HandshakeResult::State::NeedsMorePeerData};
}

HandshakeResult handshake_consumed_bytes_and_is_complete(size_t consumed) noexcept {
    return {consumed, 0, HandshakeResult::State::Done};
}

HandshakeResult handshaked_bytes(size_t consumed, size_t produced, HandshakeResult::State state) noexcept {
    return {consumed, produced, state};
}

HandshakeResult handshake_completed() noexcept {
    return {0, 0, HandshakeResult::State::Done};
}

HandshakeResult handshake_failed() noexcept {
    return {0, 0, HandshakeResult::State::Failed};
}

EncodeResult encode_failed() noexcept {
    return {0, 0, true};
}

EncodeResult encoded_bytes(size_t consumed, size_t produced) noexcept {
    return {consumed, produced, false};
}

DecodeResult decode_failed() noexcept {
    return {0, 0, DecodeResult::State::Failed};
}

DecodeResult decoded_frames_with_plaintext_bytes(size_t produced_bytes) noexcept {
    return {0, produced_bytes, DecodeResult::State::OK};
}

DecodeResult decode_needs_more_peer_data() noexcept {
    return {0, 0, DecodeResult::State::NeedsMorePeerData};
}

DecodeResult decoded_bytes(size_t consumed, size_t produced, DecodeResult::State state) noexcept {
    return {consumed, produced, state};
}

BioPtr new_tls_frame_memory_bio() {
    BioPtr bio(::BIO_new(BIO_s_mem()));
    if (!bio) {
        throw CryptoException("IO_new(BIO_s_mem()) failed; out of memory?");
    }
    BIO_set_write_buf_size(bio.get(), 0); // 0 ==> default max frame size
    return bio;
}

} // anon ns

OpenSslCryptoCodecImpl::OpenSslCryptoCodecImpl(::SSL_CTX& ctx, Mode mode)
    : _ssl(::SSL_new(&ctx)),
      _mode(mode)
{
    if (!_ssl) {
        throw CryptoException("Failed to create new SSL from SSL_CTX");
    }
    /*
     * We use two separate memory BIOs rather than a BIO pair for writing and
     * reading ciphertext, respectively. This is because it _seems_ quite
     * a bit more straight forward to implement a full duplex API with two
     * separate BIOs, but there is little available documentation as to the
     * 'hows' and 'whys' around this.
     * There are claims from core OpenSSL devs[0] that BIO pairs are more efficient,
     * so we may reconsider the current approach (or just use the "OpenSSL controls
     * the file descriptor" yolo approach for simplicity, assuming they do optimal
     * stuff internally).
     *
     * Our BIOs are used as follows:
     *
     * Handshakes may use both BIOs opaquely:
     *
     *  handshake() : SSL_do_handshake()  --(_output_bio ciphertext)--> BIO_read  --> [peer]
     *              : SSL_do_handshake() <--(_input_bio ciphertext)--   BIO_write <-- [peer]
     *
     * Once handshaking is complete, the input BIO is only used for decodes and the output
     * BIO is only used for encodes. We explicitly disallow TLS renegotiation, both for
     * the sake of simplicity and for added security (renegotiation is a bit of a rat's nest).
     *
     *  encode() : SSL_write(plaintext) --(_output_bio ciphertext)--> BIO_read  --> [peer]
     *  decode() : SSL_read(plaintext) <--(_input_bio ciphertext)--   BIO_write <-- [peer]
     *
     * To avoid blowing the sizes of BIOs out of the water, we do our best to encode and decode
     * on a per-TLS frame granularity (16K) maximum.
     */
    BioPtr tmp_input_bio  = new_tls_frame_memory_bio();
    BioPtr tmp_output_bio = new_tls_frame_memory_bio();
    // Connect BIOs used internally by OpenSSL. This transfers ownership. No return value to check.
    // TODO replace with explicit SSL_set0_rbio/SSL_set0_wbio on OpenSSL >= v1.1
    ::SSL_set_bio(_ssl.get(), tmp_input_bio.get(), tmp_output_bio.get());
    _input_bio  = tmp_input_bio.release();
    _output_bio = tmp_output_bio.release();
    if (_mode == Mode::Client) {
        ::SSL_set_connect_state(_ssl.get());
    } else {
        ::SSL_set_accept_state(_ssl.get());
    }
}

// TODO remove spammy logging once code is stable

// Produces bytes previously written to _output_bio by SSL_do_handshake or SSL_write
int OpenSslCryptoCodecImpl::drain_outgoing_network_bytes_if_any(
        char *to_peer, size_t to_peer_buf_size) noexcept {
    int out_pending = BIO_pending(_output_bio);
    if (out_pending > 0) {
        int copied = ::BIO_read(_output_bio, to_peer, static_cast<int>(to_peer_buf_size));
        // TODO BIO_should_retry here? Semantics are unclear, especially for memory BIOs.
        LOG(spam, "BIO_read copied out %d bytes of ciphertext from _output_bio", copied);
        if (copied < 0) {
            LOG(error, "Memory BIO_read() failed with BIO_pending() > 0");
        }
        return copied;
    }
    return out_pending;
}

HandshakeResult OpenSslCryptoCodecImpl::handshake(const char* from_peer, size_t from_peer_buf_size,
                                                  char* to_peer, size_t to_peer_buf_size) noexcept {
    LOG_ASSERT(from_peer != nullptr && to_peer != nullptr
               && from_peer_buf_size < INT32_MAX && to_peer_buf_size < INT32_MAX);

    if (SSL_is_init_finished(_ssl.get())) {
        return handshake_completed();
    }
    // Still ciphertext data left? If so, get rid of it before we start a new operation
    // that wants to fill the output BIO.
    int produced = drain_outgoing_network_bytes_if_any(to_peer, to_peer_buf_size);
    if (produced > 0) {
        // Handshake isn't complete yet and we've got stuff to send. Need to continue handshake
        // once more data is available from the peer.
        return handshake_produced_bytes_and_needs_more_peer_data(static_cast<size_t>(produced));
    } else if (produced < 0) {
        return handshake_failed();
    }
    const auto consume_res = do_handshake_and_consume_peer_input_bytes(from_peer, from_peer_buf_size);
    LOG_ASSERT(consume_res.bytes_produced == 0);
    if (consume_res.failed()) {
        return consume_res;
    }
    // SSL_do_handshake() might have produced more data to send. Note: handshake may
    // be complete at this point.
    produced = drain_outgoing_network_bytes_if_any(to_peer, to_peer_buf_size);
    if (produced < 0) {
        return handshake_failed();
    }
    return handshaked_bytes(consume_res.bytes_consumed, static_cast<size_t>(produced), consume_res.state);
}

HandshakeResult OpenSslCryptoCodecImpl::do_handshake_and_consume_peer_input_bytes(
        const char *from_peer, size_t from_peer_buf_size) noexcept {
    // Feed the SSL session input in frame-sized chunks between each call to SSL_do_handshake().
    // This is primarily to ensure we don't shove unbounded amounts of data into the BIO
    // in the case that someone naughty is sending us tons of garbage over the socket.
    size_t consumed_total = 0;
    while (true) {
        // Assumption: SSL_do_handshake will place all required outgoing handshake
        // data in the output memory BIO without requiring WANT_WRITE. Freestanding
        // memory BIOs are _supposedly_ auto-resizing, so this should work transparently.
        // At the very least, if this is not the case we'll auto-fail the connection
        // and quickly find out..!
        // TODO test multi-frame sized handshake
        // TODO should we invoke ::ERR_clear_error() prior?
        int ssl_result = ::SSL_do_handshake(_ssl.get());
        ssl_result = ::SSL_get_error(_ssl.get(), ssl_result);

        if (ssl_result == SSL_ERROR_WANT_READ) {
            LOG(spam, "SSL_do_handshake() returned SSL_ERROR_WANT_READ");
            if (from_peer_buf_size - consumed_total > 0) {
                int consumed = ::BIO_write(_input_bio, from_peer + consumed_total,
                                           static_cast<int>(std::min(MaximumTlsFrameSize, from_peer_buf_size - consumed_total)));
                LOG(spam, "BIO_write copied in %d bytes of ciphertext to _input_bio", consumed);
                if (consumed < 0) {
                    LOG(error, "Memory BIO_write() returned %d", consumed); // TODO BIO_need_retry?
                    return handshake_failed();
                }
                consumed_total += consumed; // TODO protect against consumed == 0?
                continue;
            } else {
                return handshake_consumed_bytes_and_needs_more_peer_data(consumed_total);
            }
        } else if (ssl_result == SSL_ERROR_NONE) {
            // At this point SSL_do_handshake has stated it does not need any more peer data, i.e.
            // the handshake is complete.
            if (!SSL_is_init_finished(_ssl.get())) {
                LOG(error, "SSL handshake is not completed even though no more peer data is requested");
                return handshake_failed();
            }
            return handshake_consumed_bytes_and_is_complete(consumed_total);
        } else {
            LOG(error, "SSL_do_handshake() returned unexpected error: %s", ssl_error_to_str(ssl_result));
            return handshake_failed();
        }
    };
}

EncodeResult OpenSslCryptoCodecImpl::encode(const char* plaintext, size_t plaintext_size,
                                            char* ciphertext, size_t ciphertext_size) noexcept {
    LOG_ASSERT(plaintext != nullptr && ciphertext != nullptr
               && plaintext_size < INT32_MAX && ciphertext_size < INT32_MAX);

    if (!SSL_is_init_finished(_ssl.get())) {
        LOG(error, "OpenSslCryptoCodecImpl::encode() called before handshake completed");
        return encode_failed();
    }
    size_t bytes_consumed = 0;
    if (plaintext_size != 0) {
        int to_consume = static_cast<int>(std::min(plaintext_size, MaximumFramePayloadSize));
        // SSL_write encodes plaintext to ciphertext and writes to _output_bio
        int consumed = ::SSL_write(_ssl.get(), plaintext, to_consume);
        LOG(spam, "After SSL_write() -> %d, _input_bio pending=%d, _output_bio pending=%d",
            consumed, BIO_pending(_input_bio), BIO_pending(_output_bio));
        if (consumed < 0) {
            int ssl_error = ::SSL_get_error(_ssl.get(), consumed);
            LOG(error, "SSL_write() failed to write frame, got error %s", ssl_error_to_str(ssl_error));
            // TODO explicitly detect and log TLS renegotiation error (SSL_ERROR_WANT_READ)?
            return encode_failed();
        } else if (consumed != to_consume) {
            LOG(error, "SSL_write() returned OK but did not consume all requested plaintext");
            return encode_failed();
        }
        bytes_consumed = static_cast<size_t>(consumed);
    }

    int produced = drain_outgoing_network_bytes_if_any(ciphertext, ciphertext_size);
    if (produced < 0) {
        return encode_failed();
    }
    if (BIO_pending(_output_bio) != 0) {
        LOG(error, "Residual data left in output BIO on encode(); provided buffer is too small");
        return encode_failed();
    }
    return encoded_bytes(bytes_consumed, static_cast<size_t>(produced));
}
DecodeResult OpenSslCryptoCodecImpl::decode(const char* ciphertext, size_t ciphertext_size,
                                            char* plaintext, size_t plaintext_size) noexcept {
    LOG_ASSERT(ciphertext != nullptr && plaintext != nullptr
               && ciphertext_size < INT32_MAX && plaintext_size < INT32_MAX);

    if (!SSL_is_init_finished(_ssl.get())) {
        LOG(error, "OpenSslCryptoCodecImpl::decode() called before handshake completed");
        return decode_failed();
    }
    auto produce_res = drain_and_produce_plaintext_from_ssl(plaintext, static_cast<int>(plaintext_size));
    if ((produce_res.bytes_produced > 0) || produce_res.failed()) {
        return produce_res; // TODO gRPC [1] handles this differently... allows fallthrough
    }
    int consumed = consume_peer_input_bytes(ciphertext, ciphertext_size);
    if (consumed < 0) {
        return decode_failed();
    }
    produce_res = drain_and_produce_plaintext_from_ssl(plaintext, static_cast<int>(plaintext_size));
    return decoded_bytes(static_cast<size_t>(consumed), produce_res.bytes_produced, produce_res.state);
}

DecodeResult OpenSslCryptoCodecImpl::drain_and_produce_plaintext_from_ssl(
        char* plaintext, size_t plaintext_size) noexcept {
    // SSL_read() is named a bit confusingly. We read _from_ the SSL-internal state
    // via the input BIO _into_ to the receiving plaintext buffer.
    // This may consume the entire, parts of, or none of the input BIO's data,
    // depending on how much TLS frame data is available and its size relative
    // to the receiving plaintext buffer.
    int produced = ::SSL_read(_ssl.get(), plaintext, static_cast<int>(plaintext_size));
    LOG(spam, "After SSL_read() -> %d, _input_bio pending=%d, _output_bio pending=%d",
        produced, BIO_pending(_input_bio), BIO_pending(_output_bio));
    if (produced > 0) {
        // At least 1 frame decoded successfully.
        return decoded_frames_with_plaintext_bytes(static_cast<size_t>(produced));
    } else {
        int ssl_error = ::SSL_get_error(_ssl.get(), produced);
        switch (ssl_error) {
            case SSL_ERROR_WANT_READ:
                // SSL_read() was not able to decode a full frame with the ciphertext that
                // we've fed it thus far; caller must feed it some and then try again.
                LOG(spam, "SSL_read() returned SSL_ERROR_WANT_READ, must get more ciphertext");
                return decode_needs_more_peer_data();
            default:
                LOG(error, "SSL_read() returned unexpected error: %s", ssl_error_to_str(ssl_error));
                return decode_failed();
        }
    }
}

int OpenSslCryptoCodecImpl::consume_peer_input_bytes(
        const char* ciphertext, size_t ciphertext_size) noexcept {
    // TODO BIO_need_retry on failure? Can this even happen for memory BIOs?
    int consumed = ::BIO_write(_input_bio, ciphertext, static_cast<int>(std::min(MaximumTlsFrameSize, ciphertext_size)));
    LOG(spam, "BIO_write copied in %d bytes of ciphertext to _input_bio", consumed);
    if (consumed < 0) {
        LOG(error, "Memory BIO_write() returned %d", consumed);
    }
    return consumed;
}

}

// External references:
//  [0] http://openssl.6102.n7.nabble.com/nonblocking-implementation-question-tp1728p1732.html
//  [1] https://github.com/grpc/grpc/blob/master/src/core/tsi/ssl_transport_security.cc
