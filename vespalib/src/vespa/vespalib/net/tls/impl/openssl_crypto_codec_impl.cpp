// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "openssl_crypto_codec_impl.h"
#include "openssl_tls_context_impl.h"
#include "direct_buffer_bio.h"

#include <vespa/vespalib/crypto/crypto_exception.h>
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <vespa/vespalib/net/tls/statistics.h>

#include <mutex>
#include <vector>
#include <memory>
#include <stdexcept>

#include <openssl/ssl.h>
#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/pem.h>

#include <vespa/log/bufferedlogger.h>
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

using namespace vespalib::crypto;

namespace vespalib::net::tls::impl {

namespace {

bool verify_buf(const char *buf, size_t len) {
    return ((len < INT32_MAX) && ((len == 0) || (buf != nullptr)));
}

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

HandshakeResult handshake_consumed_bytes_and_is_complete(size_t consumed) noexcept {
    return {consumed, 0, HandshakeResult::State::Done};
}

HandshakeResult handshaked_bytes(size_t consumed, size_t produced, HandshakeResult::State state) noexcept {
    return {consumed, produced, state};
}

HandshakeResult handshake_completed() noexcept {
    return {0, 0, HandshakeResult::State::Done};
}

HandshakeResult handshake_needs_work() noexcept {
    return {0, 0, HandshakeResult::State::NeedsWork};
}

HandshakeResult handshake_needs_peer_data() noexcept {
    return {0, 0, HandshakeResult::State::NeedsMorePeerData};
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

DecodeResult decode_peer_has_closed() noexcept {
    return {0, 0, DecodeResult::State::Closed};
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

BioPtr new_tls_frame_mutable_memory_bio() {
    BioPtr bio(new_mutable_direct_buffer_bio());
    if (!bio) {
        throw CryptoException("new_mutable_direct_buffer_bio() failed; out of memory?");
    }
    return bio;
}

BioPtr new_tls_frame_const_memory_bio() {
    BioPtr bio(new_const_direct_buffer_bio());
    if (!bio) {
        throw CryptoException("new_const_direct_buffer_bio() failed; out of memory?");
    }
    return bio;
}

vespalib::string ssl_error_from_stack() {
    char buf[256];
    ERR_error_string_n(ERR_get_error(), buf, sizeof(buf));
    return vespalib::string(buf);
}

void log_ssl_error(const char* source, const SocketAddress& peer_address, int ssl_error) {
    // Buffer the emitted log messages on the peer's IP address. This prevents a single misbehaving
    // client from flooding our logs, while at the same time ensuring that logs for other clients
    // aren't lost.
    LOGBT(warning, peer_address.ip_address(),
          "%s (with peer '%s') returned unexpected error: %s (%s)",
          source, peer_address.spec().c_str(),
          ssl_error_to_str(ssl_error), ssl_error_from_stack().c_str());
}

} // anon ns

OpenSslCryptoCodecImpl::OpenSslCryptoCodecImpl(std::shared_ptr<OpenSslTlsContextImpl> ctx,
                                               const SocketSpec& peer_spec,
                                               const SocketAddress& peer_address,
                                               Mode mode)
    : _ctx(std::move(ctx)),
      _peer_spec(peer_spec),
      _peer_address(peer_address),
      _ssl(::SSL_new(_ctx->native_context())),
      _mode(mode),
      _deferred_handshake_params(),
      _deferred_handshake_result()
{
    if (!_ssl) {
        throw CryptoException("Failed to create new SSL from SSL_CTX");
    }
    /*
     * We use two separate buffer-wrapping BIOs rather than a BIO pair for writing and
     * reading ciphertext, respectively. This is because it _seems_ quite
     * a bit more straight forward to implement a full duplex API with two
     * separate BIOs, but there is little available documentation as to the
     * 'hows' and 'whys' around this.
     *
     * Our BIOs are used as follows:
     *
     * Handshakes may use both BIOs opaquely:
     *
     *  handshake() : SSL_do_handshake()  --(_output_bio ciphertext)--> [peer]
     *              : SSL_do_handshake() <--(_input_bio ciphertext)--   [peer]
     *
     * Once handshaking is complete, the input BIO is only used for decodes and the output
     * BIO is only used for encodes. We explicitly disallow TLS renegotiation, both for
     * the sake of simplicity and for added security (renegotiation is a bit of a rat's nest).
     *
     *  encode() : SSL_write(plaintext) --(_output_bio ciphertext)--> [peer]
     *  decode() : SSL_read(plaintext) <--(_input_bio ciphertext)--   [peer]
     *
     */
    BioPtr tmp_input_bio  = new_tls_frame_const_memory_bio();
    BioPtr tmp_output_bio = new_tls_frame_mutable_memory_bio();
    // Connect BIOs used internally by OpenSSL. This transfers ownership. No return values to check.
#if (OPENSSL_VERSION_NUMBER >= 0x10100000L)
    ::SSL_set0_rbio(_ssl.get(), tmp_input_bio.get());
    ::SSL_set0_wbio(_ssl.get(), tmp_output_bio.get());
#else
    ::SSL_set_bio(_ssl.get(), tmp_input_bio.get(), tmp_output_bio.get());
#endif
    _input_bio  = tmp_input_bio.release();
    _output_bio = tmp_output_bio.release();
    if (_mode == Mode::Client) {
        ::SSL_set_connect_state(_ssl.get());
        enable_hostname_validation_if_requested();
        set_server_name_indication_extension();
    } else {
        ::SSL_set_accept_state(_ssl.get());
    }
    // Store self-reference that can be fished out of SSL object during certificate verification callbacks
    if (SSL_set_app_data(_ssl.get(), this) != 1) {
        throw CryptoException("SSL_set_app_data() failed");
    }
}

OpenSslCryptoCodecImpl::~OpenSslCryptoCodecImpl() = default;

std::unique_ptr<OpenSslCryptoCodecImpl>
OpenSslCryptoCodecImpl::make_client_codec(std::shared_ptr<OpenSslTlsContextImpl> ctx,
                                          const SocketSpec& peer_spec,
                                          const SocketAddress& peer_address)
{
    // Naked new due to private ctor
    return std::unique_ptr<OpenSslCryptoCodecImpl>(
            new OpenSslCryptoCodecImpl(std::move(ctx), peer_spec, peer_address, Mode::Client));
}
std::unique_ptr<OpenSslCryptoCodecImpl>
OpenSslCryptoCodecImpl::make_server_codec(std::shared_ptr<OpenSslTlsContextImpl> ctx,
                                          const SocketAddress& peer_address)
{
    // Naked new due to private ctor
    return std::unique_ptr<OpenSslCryptoCodecImpl>(
            new OpenSslCryptoCodecImpl(std::move(ctx), SocketSpec::invalid, peer_address, Mode::Server));
}

void OpenSslCryptoCodecImpl::enable_hostname_validation_if_requested() {
    if (_peer_spec.valid() && !_ctx->transport_security_options().disable_hostname_validation()) {
        auto* verify_param = SSL_get0_param(_ssl.get()); // Internal ptr, no refcount bump or alloc. We must not free.
        LOG_ASSERT(verify_param != nullptr);
        vespalib::string host = _peer_spec.host_with_fallback();
        if (X509_VERIFY_PARAM_set1_host(verify_param, host.c_str(), host.size()) != 1) {
            throw CryptoException("X509_VERIFY_PARAM_set1_host() failed");
        }
        // TODO should we set expected IP based on peer address as well?
    }
}

void OpenSslCryptoCodecImpl::set_server_name_indication_extension() {
    if (_peer_spec.valid()) {
        vespalib::string host = _peer_spec.host_with_fallback();
        // OpenSSL tries to cast const char* to void* in a macro, even on 1.1.1. GCC is not overly impressed,
        // so to satiate OpenSSL's quirks we pre-cast away the constness.
        auto* host_cstr_that_trusts_openssl_not_to_mess_up = const_cast<char*>(host.c_str());
        if (SSL_set_tlsext_host_name(_ssl.get(), host_cstr_that_trusts_openssl_not_to_mess_up) != 1) {
            throw CryptoException("SSL_set_tlsext_host_name() failed");
        }
    }
}

std::optional<vespalib::string> OpenSslCryptoCodecImpl::client_provided_sni_extension() const {
    if ((_mode != Mode::Server) || (SSL_get_servername_type(_ssl.get()) != TLSEXT_NAMETYPE_host_name)) {
        return {};
    }
    const char* sni_host_raw = SSL_get_servername(_ssl.get(), TLSEXT_NAMETYPE_host_name);
    if (sni_host_raw == nullptr) {
        return {};
    }
    return vespalib::string(sni_host_raw);
}

HandshakeResult OpenSslCryptoCodecImpl::handshake(const char* from_peer, size_t from_peer_buf_size,
                                                  char* to_peer, size_t to_peer_buf_size) noexcept
{
    LOG_ASSERT(verify_buf(from_peer, from_peer_buf_size) && verify_buf(to_peer, to_peer_buf_size));
    LOG_ASSERT(!_deferred_handshake_params.has_value()); // do_handshake_work() not called as expected

    if (_deferred_handshake_result.has_value()) {
        const auto result = *_deferred_handshake_result;
        _deferred_handshake_result = std::optional<HandshakeResult>();
        return result;
    }
    if (SSL_is_init_finished(_ssl.get())) {
        return handshake_completed();
    }
    // We make the assumption that TLS handshake processing is primarily reactive,
    // i.e. a handshake frame is received from the peer and this either produces
    // output to send back and/or marks the handshake as complete or failed.
    // One exception to this rule is if if we're a client. In this case we have to
    // do work the first time we're called in order to prepare a ClientHello message.
    // At that point there will be nothing on the wire to react to.
    //
    // Note that we will return a "needs work" false positive in the case of a short read,
    // as whether or not a complete TLS frame has been received is entirely opaque to us.
    // The end result will still be correct, as the do_handshake_work() call will signal
    // "needs read" as expected, but we get extra thread round-trips and added latency.
    // It is expected that this is not a common case.
    const bool first_client_send = ((_mode == Mode::Client) && SSL_in_before(_ssl.get()));
    const bool needs_work = ((from_peer_buf_size > 0) || first_client_send);
    if (needs_work) {
        _deferred_handshake_params = DeferredHandshakeParams(from_peer, from_peer_buf_size, to_peer, to_peer_buf_size);
        return handshake_needs_work();
    }
    return handshake_needs_peer_data();
}

void OpenSslCryptoCodecImpl::do_handshake_work() noexcept {
    LOG_ASSERT(_deferred_handshake_params.has_value());  // handshake() not called as expected
    LOG_ASSERT(!_deferred_handshake_result.has_value()); // do_handshake_work() called multiple times without handshake()
    const auto params = *_deferred_handshake_params;
    _deferred_handshake_params = std::optional<DeferredHandshakeParams>();

    ConstBufferViewGuard const_view_guard(*_input_bio, params.from_peer, params.from_peer_buf_size);
    MutableBufferViewGuard mut_view_guard(*_output_bio, params.to_peer, params.to_peer_buf_size);

    const auto consume_res = do_handshake_and_consume_peer_input_bytes();
    LOG_ASSERT(consume_res.bytes_produced == 0); // Measured via BIO_pending below, not from result.
    if (consume_res.failed()) {
        _deferred_handshake_result = consume_res;
        return;
    }
    // SSL_do_handshake() might have produced more data to send. Note: handshake may
    // be complete at this point.
    const int produced = BIO_pending(_output_bio);
    _deferred_handshake_result = handshaked_bytes(consume_res.bytes_consumed,
                                                  static_cast<size_t>(produced),
                                                  consume_res.state);
}

HandshakeResult OpenSslCryptoCodecImpl::do_handshake_and_consume_peer_input_bytes() noexcept {
    // Assumption: SSL_do_handshake will place all required outgoing handshake
    // data in the output memory BIO without requiring WANT_WRITE.
    const long pending_read_before = BIO_pending(_input_bio);

    ::ERR_clear_error();
    int ssl_result = ::SSL_do_handshake(_ssl.get());
    ssl_result = ::SSL_get_error(_ssl.get(), ssl_result);

    const long consumed = pending_read_before - BIO_pending(_input_bio);
    LOG_ASSERT(consumed >= 0);

    if (ssl_result == SSL_ERROR_WANT_READ) {
        LOG(spam, "SSL_do_handshake() returned SSL_ERROR_WANT_READ");
        return handshake_consumed_bytes_and_needs_more_peer_data(static_cast<size_t>(consumed));
    } else if (ssl_result == SSL_ERROR_NONE) {
        // At this point SSL_do_handshake has stated it does not need any more peer data, i.e.
        // the handshake is complete.
        if (!SSL_is_init_finished(_ssl.get())) {
            LOG(error, "SSL handshake is not completed even though no more peer data is requested");
            return handshake_failed();
        }
        LOG(debug, "SSL_do_handshake() with %s is complete, using protocol %s",
            _peer_address.spec().c_str(), SSL_get_version(_ssl.get()));
        ConnectionStatistics::get(_mode == Mode::Server).inc_tls_connections();
        return handshake_consumed_bytes_and_is_complete(static_cast<size_t>(consumed));
    } else {
        log_ssl_error("SSL_do_handshake()", _peer_address, ssl_result);
        ConnectionStatistics::get(_mode == Mode::Server).inc_failed_tls_handshakes();
        return handshake_failed();
    }
}

EncodeResult OpenSslCryptoCodecImpl::encode(const char* plaintext, size_t plaintext_size,
                                            char* ciphertext, size_t ciphertext_size) noexcept {
    LOG_ASSERT(verify_buf(plaintext, plaintext_size) && verify_buf(ciphertext, ciphertext_size));

    if (!SSL_is_init_finished(_ssl.get())) {
        LOG(error, "OpenSslCryptoCodecImpl::encode() called before handshake completed");
        return encode_failed();
    }

    MutableBufferViewGuard mut_view_guard(*_output_bio, ciphertext, ciphertext_size);
    // _input_bio not read from here.

    size_t bytes_consumed = 0;
    if (plaintext_size != 0) {
        ::ERR_clear_error();
        const int to_consume = static_cast<int>(std::min(plaintext_size, MaximumFramePlaintextSize));
        // SSL_write encodes plaintext to ciphertext and writes to _output_bio
        const int consumed = ::SSL_write(_ssl.get(), plaintext, to_consume);
        if (consumed < 0) {
            log_ssl_error("SSL_write()", _peer_address, ::SSL_get_error(_ssl.get(), consumed));
            ConnectionStatistics::get(_mode == Mode::Server).inc_broken_tls_connections();
            return encode_failed(); // TODO explicitly detect and log TLS renegotiation error (SSL_ERROR_WANT_READ)?
        } else if (consumed != to_consume) {
            LOG(error, "SSL_write() returned OK but did not consume all requested plaintext");
            return encode_failed();
        }
        bytes_consumed = static_cast<size_t>(consumed);
    }
    const int produced = BIO_pending(_output_bio);
    return encoded_bytes(bytes_consumed, static_cast<size_t>(produced));
}
DecodeResult OpenSslCryptoCodecImpl::decode(const char* ciphertext, size_t ciphertext_size,
                                            char* plaintext, size_t plaintext_size) noexcept {
    LOG_ASSERT(verify_buf(ciphertext, ciphertext_size) && verify_buf(plaintext, plaintext_size));

    if (!SSL_is_init_finished(_ssl.get())) {
        LOG(error, "OpenSslCryptoCodecImpl::decode() called before handshake completed");
        return decode_failed();
    }
    ConstBufferViewGuard const_view_guard(*_input_bio, ciphertext, ciphertext_size);
    // _output_bio not written to here

    const int input_pending_before = BIO_pending(_input_bio);
    auto produce_res = drain_and_produce_plaintext_from_ssl(plaintext, static_cast<int>(plaintext_size));
    const int input_pending_after = BIO_pending(_input_bio);

    LOG_ASSERT(input_pending_before >= input_pending_after);
    const int consumed = input_pending_before - input_pending_after;
    LOG(spam, "decode: consumed %d bytes (ciphertext buffer %d -> %d bytes), produced %zu bytes. Need read: %s",
        consumed, input_pending_before, input_pending_after, produce_res.bytes_produced,
        (produce_res.state == DecodeResult::State::NeedsMorePeerData) ? "yes" : "no");
    return decoded_bytes(static_cast<size_t>(consumed), produce_res.bytes_produced, produce_res.state);
}

DecodeResult OpenSslCryptoCodecImpl::drain_and_produce_plaintext_from_ssl(
        char* plaintext, size_t plaintext_size) noexcept {
    ::ERR_clear_error();
    // SSL_read() is named a bit confusingly. We read _from_ the SSL-internal state
    // via the input BIO _into_ to the receiving plaintext buffer.
    // This may consume the entire, parts of, or none of the input BIO's data,
    // depending on how much TLS frame data is available and its size relative
    // to the receiving plaintext buffer.
    const int produced = ::SSL_read(_ssl.get(), plaintext, static_cast<int>(plaintext_size));
    if (produced > 0) {
        // At least 1 frame decoded successfully.
        return decoded_frames_with_plaintext_bytes(static_cast<size_t>(produced));
    } else {
        return remap_ssl_read_failure_to_decode_result(produced);
    }
}

DecodeResult OpenSslCryptoCodecImpl::remap_ssl_read_failure_to_decode_result(int read_result) noexcept {
    const int ssl_error = ::SSL_get_error(_ssl.get(), read_result);
    switch (ssl_error) {
    case SSL_ERROR_WANT_READ:
        // SSL_read() was not able to decode a full frame with the ciphertext that
        // we've fed it thus far; caller must feed it some and then try again.
        LOG(spam, "SSL_read() returned SSL_ERROR_WANT_READ, must get more ciphertext");
        return decode_needs_more_peer_data();
    case SSL_ERROR_ZERO_RETURN:
        LOG(debug, "SSL_read() returned SSL_ERROR_ZERO_RETURN; connection has been shut down normally by the peer");
        return decode_peer_has_closed();
    default:
        log_ssl_error("SSL_read()", _peer_address, ssl_error);
        ConnectionStatistics::get(_mode == Mode::Server).inc_broken_tls_connections();
        return decode_failed();
    }
}

EncodeResult OpenSslCryptoCodecImpl::half_close(char* ciphertext, size_t ciphertext_size) noexcept {
    LOG_ASSERT(verify_buf(ciphertext, ciphertext_size));
    MutableBufferViewGuard mut_view_guard(*_output_bio, ciphertext, ciphertext_size);
    const int pending_before = BIO_pending(_output_bio);
    int ssl_result = ::SSL_shutdown(_ssl.get());
    if (ssl_result < 0) {
        log_ssl_error("SSL_shutdown()", _peer_address, ::SSL_get_error(_ssl.get(), ssl_result));
        return encode_failed();
    }
    const int pending_after = BIO_pending(_output_bio);
    LOG_ASSERT(pending_after >= pending_before);
    return encoded_bytes(0, static_cast<size_t>(pending_after - pending_before));
}

}

// External references:
//  [0] http://openssl.6102.n7.nabble.com/nonblocking-implementation-question-tp1728p1732.html
//  [1] https://github.com/grpc/grpc/blob/master/src/core/tsi/ssl_transport_security.cc
//  [2] https://wiki.openssl.org/index.php/Hostname_validation
//  [3] https://wiki.openssl.org/index.php/SSL/TLS_Client
