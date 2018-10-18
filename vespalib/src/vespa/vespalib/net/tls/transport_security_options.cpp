// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_security_options.h"
#include <openssl/crypto.h>
#include <cassert>

namespace vespalib::net::tls {

TransportSecurityOptions::TransportSecurityOptions(vespalib::string ca_certs_pem,
                                                   vespalib::string cert_chain_pem,
                                                   vespalib::string private_key_pem)
    : _ca_certs_pem(std::move(ca_certs_pem)),
      _cert_chain_pem(std::move(cert_chain_pem)),
      _private_key_pem(std::move(private_key_pem)),
      _cert_verify_callback(std::make_shared<AcceptAllPreVerifiedCertificates>())
{
}

TransportSecurityOptions::TransportSecurityOptions(vespalib::string ca_certs_pem,
                                                   vespalib::string cert_chain_pem,
                                                   vespalib::string private_key_pem,
                                                   std::shared_ptr<CertificateVerificationCallback> cert_verify_callback)
        : _ca_certs_pem(std::move(ca_certs_pem)),
          _cert_chain_pem(std::move(cert_chain_pem)),
          _private_key_pem(std::move(private_key_pem)),
          _cert_verify_callback(std::move(cert_verify_callback))
{
    assert(_cert_verify_callback.get() != nullptr);
}

TransportSecurityOptions::~TransportSecurityOptions() {
    OPENSSL_cleanse(&_private_key_pem[0], _private_key_pem.size());
}

}
