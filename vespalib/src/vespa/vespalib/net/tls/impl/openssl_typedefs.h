// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <openssl/ssl.h>
#include <openssl/crypto.h>
#include <openssl/x509.h>

namespace vespalib::net::tls::impl {

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

}
