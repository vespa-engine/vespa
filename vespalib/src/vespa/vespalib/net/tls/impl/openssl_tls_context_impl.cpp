// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "openssl_typedefs.h"
#include "openssl_tls_context_impl.h"
#include <vespa/vespalib/net/tls/crypto_exception.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <mutex>
#include <vector>
#include <memory>
#include <stdexcept>
#include <openssl/ssl.h>
#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/pem.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.tls.openssl_tls_context_impl");

#if (OPENSSL_VERSION_NUMBER < 0x10000000L)
// < 1.0 requires explicit thread ID callback support.
#  error "Provided OpenSSL version is too darn old, need at least 1.0"
#endif

namespace vespalib::net::tls::impl {

namespace {

#if (OPENSSL_VERSION_NUMBER < 0x10100000L)

std::vector<std::unique_ptr<std::mutex>> _g_mutexes;

// Some works on OpenSSL legacy locking: OpenSSL does not implement locking
// itself internally, deferring to user code callbacks that Do The Needful(tm).
// The `n` parameter refers to the nth mutex, which is always < CRYPTO_num_locks().
void openssl_locking_cb(int mode, int n, [[maybe_unused]] const char *file, [[maybe_unused]] int line) {
    if (mode & CRYPTO_LOCK) {
        _g_mutexes[n]->lock();
    } else {
        _g_mutexes[n]->unlock();
    }
}

#endif

struct OpenSslLibraryResources {
    OpenSslLibraryResources();
    ~OpenSslLibraryResources();
};

OpenSslLibraryResources::OpenSslLibraryResources() {
    // Other implementations (Asio, gRPC) disagree on whether main library init
    // itself should take place on >= v1.1. We always do it to be on the safe side..!
    ::SSL_library_init();
    ::SSL_load_error_strings();
    ::OpenSSL_add_all_algorithms();
    // Luckily, the mutex callback madness is not present on >= v1.1
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
    // Since the init path should happen only once globally, but multiple libraries
    // may use OpenSSL, make sure we don't step on any toes if locking callbacks are
    // already set up.
    if (!::CRYPTO_get_locking_callback()) {
        const int num_locks = ::CRYPTO_num_locks();
        LOG_ASSERT(num_locks > 0);
        _g_mutexes.reserve(num_locks);
        for (int i = 0; i < num_locks; ++i) {
            _g_mutexes.emplace_back(std::make_unique<std::mutex>());
        }
        ::CRYPTO_set_locking_callback(openssl_locking_cb);
    }
#endif
}

OpenSslLibraryResources::~OpenSslLibraryResources() {
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
    if (::CRYPTO_get_locking_callback() == openssl_locking_cb) {
        ::CRYPTO_set_locking_callback(nullptr);
    }
#endif
    ERR_free_strings();
    EVP_cleanup();
    CRYPTO_cleanup_all_ex_data();
}

// TODO make global init instead..?
void ensure_openssl_initialized_once() {
    static OpenSslLibraryResources openssl_resources;
    (void) openssl_resources;
}

BioPtr bio_from_string(vespalib::stringref str) {
    LOG_ASSERT(str.size() <= INT_MAX);
#if (OPENSSL_VERSION_NUMBER >= 0x10002000L)
    BioPtr bio(::BIO_new_mem_buf(str.data(), static_cast<int>(str.size())));
#else
    BioPtr bio(::BIO_new_mem_buf(const_cast<char*>(str.data()), static_cast<int>(str.size())));
#endif
    if (!bio) {
        throw CryptoException("BIO_new_mem_buf");
    }
    return bio;
}

bool has_pem_eof_on_stack() {
    const auto err = ERR_peek_last_error();
    if (!err) {
        return false;
    }
    return ((ERR_GET_LIB(err) == ERR_LIB_PEM)
            && (ERR_GET_REASON(err) == PEM_R_NO_START_LINE));
}

vespalib::string ssl_error_from_stack() {
    char buf[256];
    ERR_error_string_n(ERR_get_error(), buf, sizeof(buf));
    return vespalib::string(buf);
}

// Several OpenSSL functions take a magical user passphrase argument with
// potentially horrible default behavior for password protected input.
//
// From OpenSSL docs (https://www.openssl.org/docs/man1.1.0/crypto/PEM_read_bio_PrivateKey.html):
//
// "If the cb parameters is set to NULL and the u parameter is not NULL
//  then the u parameter is interpreted as a null terminated string to use
//  as the passphrase. If both cb and u are NULL then the default callback
//  routine is used which will typically prompt for the passphrase on the
//  current terminal with echoing turned off."
//
// Neat!
//
// Bonus points for being non-const as well.
constexpr inline void *empty_passphrase() {
    return const_cast<void *>(static_cast<const void *>(""));
}

void verify_pem_ok_or_eof(::X509* x509) {
    // It's OK if we don't have an X509 cert returned iff we failed to find
    // something that looks like the start of a PEM entry. This is to catch
    // cases where the PEM itself is malformed, since the X509 read routines
    // just return either nullptr or a cert object, making it hard to debug.
    if (!x509 && !has_pem_eof_on_stack()) {
        throw CryptoException(make_string("Failed to add X509 certificate from PEM: %s",
                                          ssl_error_from_stack().c_str()));
    }
}

// Attempt to read a PEM encoded (trusted) certificate from the given BIO.
// BIO might contain further certificates if function returns non-nullptr.
// Returns nullptr if no certificate could be loaded. This is usually an error,
// as this should be the first certificate in the chain.
X509Ptr read_trusted_x509_from_bio(::BIO& bio) {
    ::ERR_clear_error();
    // "_AUX" means the certificate is trusted. Why they couldn't name this function
    // something with "trusted" instead is left as an exercise to the reader.
    X509Ptr x509(::PEM_read_bio_X509_AUX(&bio, nullptr, nullptr, empty_passphrase()));
    verify_pem_ok_or_eof(x509.get());
    return x509;
}

// Attempt to read a PEM encoded certificate from the given BIO.
// BIO might contain further certificates if function returns non-nullptr.
// Returns nullptr if no certificate could be loaded. This usually implies
// that there are no more certificates left in the chain.
X509Ptr read_untrusted_x509_from_bio(::BIO& bio) {
    ::ERR_clear_error();
    X509Ptr x509(::PEM_read_bio_X509(&bio, nullptr, nullptr, empty_passphrase()));
    verify_pem_ok_or_eof(x509.get());
    return x509;
}

SslCtxPtr new_tls_ctx_with_auto_init() {
    ensure_openssl_initialized_once();
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
    return SslCtxPtr(::SSL_CTX_new(::TLSv1_2_method()));
#else
    SslCtxPtr ctx(::SSL_CTX_new(::TLS_method()));
    if (!::SSL_CTX_set_min_proto_version(ctx.get(), TLS1_2_VERSION)) {
        throw CryptoException("SSL_CTX_set_min_proto_version");
    }
    return ctx;
#endif
}

} // anon ns

OpenSslTlsContextImpl::OpenSslTlsContextImpl(const TransportSecurityOptions& ts_opts)
    : _ctx(new_tls_ctx_with_auto_init())
{
    if (!_ctx) {
        throw CryptoException("Failed to create new TLS context");
    }
    add_certificate_authorities(ts_opts.ca_certs_pem());
    if (!ts_opts.cert_chain_pem().empty() && !ts_opts.private_key_pem().empty()) {
        add_certificate_chain(ts_opts.cert_chain_pem());
        use_private_key(ts_opts.private_key_pem());
        verify_private_key();
    }
    enable_ephemeral_key_exchange();
    disable_compression();
    enforce_peer_certificate_verification();
    // TODO set accepted cipher suites!
    // TODO `--> If not set in options, use Modern spec from https://wiki.mozilla.org/Security/Server_Side_TLS
}

OpenSslTlsContextImpl::~OpenSslTlsContextImpl() = default;

void OpenSslTlsContextImpl::add_certificate_authorities(vespalib::stringref ca_pem) {
    auto bio = bio_from_string(ca_pem);
    ::X509_STORE* cert_store = ::SSL_CTX_get_cert_store(_ctx.get()); // Internal pointer, not owned by us.
    while (true) {
        auto ca_cert = read_untrusted_x509_from_bio(*bio);
        if (!ca_cert) {
            break;
        }
        if (::X509_STORE_add_cert(cert_store, ca_cert.get()) != 1) { // Does _not_ take ownership
            throw CryptoException("X509_STORE_add_cert");
        }
    }
}

void OpenSslTlsContextImpl::add_certificate_chain(vespalib::stringref chain_pem) {
    auto bio = bio_from_string(chain_pem);
    // First certificate in the chain is the node's own (trusted) certificate.
    auto own_cert = read_trusted_x509_from_bio(*bio);
    if (!own_cert) {
        throw CryptoException("No X509 certificates could be found in provided chain");
    }
    // Ownership of certificate is _not_ transferred, OpenSSL makes internal copy.
    // This is not well documented, but is mentioned by other impls.
    if (::SSL_CTX_use_certificate(_ctx.get(), own_cert.get()) != 1) {
        throw CryptoException("SSL_CTX_use_certificate");
    }
    // After the node's own certificate comes any intermediate CA-provided certificates.
    while (true) {
        auto ca_cert = read_untrusted_x509_from_bio(*bio);
        if (!ca_cert) {
            break; // No more certificates in chain, hooray!
        }
        // Ownership of certificate _is_ transferred here!
        if (!::SSL_CTX_add_extra_chain_cert(_ctx.get(), ca_cert.release())) {
            throw CryptoException("SSL_CTX_add_extra_chain_cert");
        }
    }
}

void OpenSslTlsContextImpl::use_private_key(vespalib::stringref key_pem) {
    auto bio = bio_from_string(key_pem);
    EvpPkeyPtr key(::PEM_read_bio_PrivateKey(bio.get(), nullptr, nullptr, empty_passphrase()));
    if (!key) {
        throw CryptoException("Failed to read PEM private key data");
    }
    // Ownership _not_ taken.
    if (::SSL_CTX_use_PrivateKey(_ctx.get(), key.get()) != 1) {
        throw CryptoException("SSL_CTX_use_PrivateKey");
    }
}

void OpenSslTlsContextImpl::verify_private_key() {
    if (::SSL_CTX_check_private_key(_ctx.get()) != 1) {
        throw CryptoException("SSL_CTX_check_private_key failed; mismatch between public and private key?");
    }
}

void OpenSslTlsContextImpl::enable_ephemeral_key_exchange() {
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
#  if (OPENSSL_VERSION_NUMBER >= 0x10002000L)
    // Always enabled by default on higher versions.
    // Auto curve selection is preferred over using SSL_CTX_set_ecdh_tmp
    if (!::SSL_CTX_set_ecdh_auto(_ctx.get(), 1)) {
        throw CryptoException("SSL_CTX_set_ecdh_auto");
    }
    // New ECDH key per connection.
    ::SSL_CTX_set_options(_ctx.get(), SSL_OP_SINGLE_ECDH_USE);
#  else
    // Set explicit P-256 curve used for ECDH purposes.
    EcKeyPtr ec_curve(::EC_KEY_new_by_curve_name(NID_X9_62_prime256v1));
    if (!ec_curve) {
        throw CryptoException("EC_KEY_new_by_curve_name(NID_X9_62_prime256v1)");
    }
    if (!::SSL_CTX_set_tmp_ecdh(_ctx.get(), ec_curve.get())) {
        throw CryptoException("SSL_CTX_set_tmp_ecdh");
    }
#  endif
#endif
}

void OpenSslTlsContextImpl::disable_compression() {
    // TLS stream compression is vulnerable to a host of chosen plaintext
    // attacks (CRIME, BREACH etc), so disable it.
    ::SSL_CTX_set_options(_ctx.get(), SSL_OP_NO_COMPRESSION);
}

void OpenSslTlsContextImpl::enforce_peer_certificate_verification() {
    // We require full mutual certificate verification. No way to configure
    // out of this, at least not for the time being.
    // TODO verification callback for custom CN/SAN etc checks.
    SSL_CTX_set_verify(_ctx.get(), SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT, nullptr);
}

}
