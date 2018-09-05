// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "openssl_typedefs.h"
#include "openssl_tls_context_impl.h"
#include <vespa/vespalib/net/tls/crypto_exception.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
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
    ::ERR_free_strings();
    ::EVP_cleanup();
    ::CRYPTO_cleanup_all_ex_data();
}

// TODO make global init instead..?
void ensure_openssl_initialized_once() {
    static OpenSslLibraryResources openssl_resources;
    (void) openssl_resources;
}

BioPtr bio_from_string(vespalib::stringref str) {
    LOG_ASSERT(str.size() <= INT_MAX);
    BioPtr bio(::BIO_new_mem_buf(str.data(), static_cast<int>(str.size())));
    if (!bio) {
        throw CryptoException("BIO_new_mem_buf");
    }
    return bio;
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

// Attempt to read a PEM encoded (trusted) certificate from the given BIO.
// BIO might contain further certificates if function returns non-nullptr.
// Returns nullptr if no certificate could be loaded. This is usually an error,
// as this should be the first certificate in the chain.
X509Ptr read_trusted_x509_from_bio(::BIO& bio) {
    // "_AUX" means the certificate is trusted. Why they couldn't name this function
    // something with "trusted" instead is left as an exercise to the reader.
    return X509Ptr(::PEM_read_bio_X509_AUX(&bio, nullptr, nullptr, empty_passphrase()));
}

// Attempt to read a PEM encoded certificate from the given BIO.
// BIO might contain further certificates if function returns non-nullptr.
// Returns nullptr if no certificate could be loaded. This usually implies
// that there are no more certificates left in the chain.
X509Ptr read_untrusted_x509_from_bio(::BIO& bio) {
    return X509Ptr(::PEM_read_bio_X509(&bio, nullptr, nullptr, empty_passphrase()));
}

::SSL_CTX* new_tls1_2_ctx_with_auto_init() {
    ensure_openssl_initialized_once();
    return ::SSL_CTX_new(::TLSv1_2_method());
}

} // anon ns

OpenSslTlsContextImpl::OpenSslTlsContextImpl(const TransportSecurityOptions& ts_opts)
    : _ctx(new_tls1_2_ctx_with_auto_init())
{
    if (!_ctx) {
        throw CryptoException("Failed to create new TLSv1.2 context");
    }
    add_certificate_authorities(ts_opts.ca_certs_pem());
    add_certificate_chain(ts_opts.cert_chain_pem());
    use_private_key(ts_opts.private_key_pem());
    verify_private_key();
    enable_ephemeral_key_exchange();
    disable_compression();
    // TODO set accepted cipher suites!
    // TODO `--> If not set in options, use Modern spec from https://wiki.mozilla.org/Security/Server_Side_TLS
    // TODO set peer verification flags!
}

OpenSslTlsContextImpl::~OpenSslTlsContextImpl() {
    ::SSL_CTX_free(_ctx);
}

void OpenSslTlsContextImpl::add_certificate_authorities(vespalib::stringref ca_pem) {
    // TODO support empty CA set...? Ever useful?
    auto bio = bio_from_string(ca_pem);
    ::X509_STORE* cert_store = ::SSL_CTX_get_cert_store(_ctx); // Internal pointer, not owned by us.
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
    ::ERR_clear_error();
    auto bio = bio_from_string(chain_pem);
    // First certificate in the chain is the node's own (trusted) certificate.
    auto own_cert = read_trusted_x509_from_bio(*bio);
    if (!own_cert) {
        throw CryptoException("No X509 certificates could be found in provided chain");
    }
    // Ownership of certificate is _not_ transferred, OpenSSL makes internal copy.
    // This is not well documented, but is mentioned by other impls.
    if (::SSL_CTX_use_certificate(_ctx, own_cert.get()) != 1) {
        throw CryptoException("SSL_CTX_use_certificate");
    }
    // After the node's own certificate comes any intermediate CA-provided certificates.
    while (true) {
        auto ca_cert = read_untrusted_x509_from_bio(*bio);
        if (!ca_cert) {
            // No more certificates in chain, hooray!
            ::ERR_clear_error();
            break;
        }
        // Ownership of certificate _is_ transferred here!
        if (!::SSL_CTX_add_extra_chain_cert(_ctx, ca_cert.release())) {
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
    if (::SSL_CTX_use_PrivateKey(_ctx, key.get()) != 1) {
        throw CryptoException("SSL_CTX_use_PrivateKey");
    }
}

void OpenSslTlsContextImpl::verify_private_key() {
    if (::SSL_CTX_check_private_key(_ctx) != 1) {
        throw CryptoException("SSL_CTX_check_private_key failed; mismatch between public and private key?");
    }
}

void OpenSslTlsContextImpl::enable_ephemeral_key_exchange() {
    // Always enabled by default on higher versions.
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
    // Auto curve selection is preferred over using SSL_CTX_set_ecdh_tmp
    if (!::SSL_CTX_set_ecdh_auto(_ctx, 1)) {
        throw CryptoException("SSL_CTX_set_ecdh_auto");
    }
#endif
    // New ECDH key per connection.
    ::SSL_CTX_set_options(_ctx, SSL_OP_SINGLE_ECDH_USE);
}

void OpenSslTlsContextImpl::disable_compression() {
    // TLS stream compression is vulnerable to a host of chosen plaintext
    // attacks (CRIME, BREACH etc), so disable it.
    ::SSL_CTX_set_options(_ctx, SSL_OP_NO_COMPRESSION);
}

}
