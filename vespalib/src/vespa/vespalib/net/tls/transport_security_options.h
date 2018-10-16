// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "certificate_verification_callback.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace vespalib::net::tls {

class TransportSecurityOptions {
    vespalib::string _ca_certs_pem;
    vespalib::string _cert_chain_pem;
    vespalib::string _private_key_pem;
    std::shared_ptr<CertificateVerificationCallback> _cert_verify_callback;
public:
    TransportSecurityOptions() = default;

    // Construct transport options with a default certificate verification callback
    // which accepts all certificates correctly signed by the given CA(s).
    TransportSecurityOptions(vespalib::string ca_certs_pem,
                             vespalib::string cert_chain_pem,
                             vespalib::string private_key_pem);

    TransportSecurityOptions(vespalib::string ca_certs_pem,
                             vespalib::string cert_chain_pem,
                             vespalib::string private_key_pem,
                             std::shared_ptr<CertificateVerificationCallback> cert_verify_callback);
    ~TransportSecurityOptions();

    const vespalib::string& ca_certs_pem() const noexcept { return _ca_certs_pem; }
    const vespalib::string& cert_chain_pem() const noexcept { return _cert_chain_pem; }
    const vespalib::string& private_key_pem() const noexcept { return _private_key_pem; }
    const std::shared_ptr<CertificateVerificationCallback>& cert_verify_callback() const noexcept {
        return _cert_verify_callback;
    }
};

}
