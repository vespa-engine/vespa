// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/crypto/openssl_typedefs.h>
#include "private_key.h"
#include "x509_certificate.h"

namespace vespalib::crypto::openssl_impl {

class PrivateKeyImpl : public PrivateKey {
    EvpPkeyPtr _pkey;
    Type _type;
public:
    PrivateKeyImpl(EvpPkeyPtr pkey, Type type) noexcept
        : _pkey(std::move(pkey)),
          _type(type)
    {}
    ~PrivateKeyImpl() override = default;

    ::EVP_PKEY* native_key() noexcept { return _pkey.get(); }
    const ::EVP_PKEY* native_key() const noexcept { return _pkey.get(); }

    Type type() const noexcept override { return _type; }
    vespalib::string private_to_pem() const override;

    static std::shared_ptr<PrivateKeyImpl> generate_openssl_p256_ec_key();
};

class X509CertificateImpl : public X509Certificate {
    X509Ptr _cert;
public:
    explicit X509CertificateImpl(X509Ptr cert) noexcept : _cert(std::move(cert)) {}
    ~X509CertificateImpl() override;

    ::X509* native_cert() noexcept { return _cert.get(); }
    const ::X509* native_cert() const noexcept { return _cert.get(); }

    vespalib::string to_pem() const override;

    // Generates an X509 certificate using a SHA-256 digest
    static std::shared_ptr<X509CertificateImpl> generate_openssl_x509_from(Params params);
};

}
