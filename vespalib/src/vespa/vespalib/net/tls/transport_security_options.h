// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib::net::tls {

class TransportSecurityOptions {
    std::string _ca_certs_pem;
    std::string _cert_chain_pem;
    std::string _private_key_pem;
public:
    TransportSecurityOptions() = default;

    TransportSecurityOptions(std::string ca_certs_pem,
                             std::string cert_chain_pem,
                             std::string private_key_pem)
         : _ca_certs_pem(std::move(ca_certs_pem)),
           _cert_chain_pem(std::move(cert_chain_pem)),
           _private_key_pem(std::move(private_key_pem))
    {}
    ~TransportSecurityOptions();

    const std::string& ca_certs_pem() const noexcept { return _ca_certs_pem; }
    const std::string& cert_chain_pem() const noexcept { return _cert_chain_pem; }
    const std::string& private_key_pem() const noexcept { return _private_key_pem; }
};

}
