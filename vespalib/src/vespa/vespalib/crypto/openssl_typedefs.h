// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <openssl/ssl.h>
#include <openssl/crypto.h>
#include <openssl/x509.h>

namespace vespalib::crypto {

struct BioDeleter {
    void operator()(::BIO* bio) const noexcept {
        ::BIO_free(bio);
    }
};
using BioPtr = std::unique_ptr<::BIO, BioDeleter>;

struct SslDeleter {
    void operator()(::SSL* ssl) const noexcept {
        ::SSL_free(ssl);
    }
};
using SslPtr = std::unique_ptr<::SSL, SslDeleter>;

struct SslCtxDeleter {
    void operator()(::SSL_CTX* ssl) const noexcept {
        ::SSL_CTX_free(ssl);
    }
};
using SslCtxPtr = std::unique_ptr<::SSL_CTX, SslCtxDeleter>;

struct X509Deleter {
    void operator()(::X509* cert) const noexcept {
        ::X509_free(cert);
    }
};
using X509Ptr = std::unique_ptr<::X509, X509Deleter>;

struct EvpPkeyDeleter {
    void operator()(::EVP_PKEY* pkey) const noexcept {
        ::EVP_PKEY_free(pkey);
    }
};
using EvpPkeyPtr = std::unique_ptr<::EVP_PKEY, EvpPkeyDeleter>;

#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
struct EcKeyDeleter {
    void operator()(::EC_KEY* ec_key) const noexcept {
        ::EC_KEY_free(ec_key);
    }
};
using EcKeyPtr = std::unique_ptr<::EC_KEY, EcKeyDeleter>;
#endif

}
