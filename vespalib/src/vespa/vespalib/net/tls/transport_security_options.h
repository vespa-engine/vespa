// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::net::tls {

class TransportSecurityOptions {
    string _ca_certs_pem;
    string _cert_chain_pem;
    string _private_key_pem;
public:
    TransportSecurityOptions() = default;

    TransportSecurityOptions(string ca_certs_pem,
                             string cert_chain_pem,
                             string private_key_pem)
         : _ca_certs_pem(std::move(ca_certs_pem)),
           _cert_chain_pem(std::move(cert_chain_pem)),
           _private_key_pem(std::move(private_key_pem))
    {}
    ~TransportSecurityOptions();

    const string& ca_certs_pem() const noexcept { return _ca_certs_pem; }
    const string& cert_chain_pem() const noexcept { return _cert_chain_pem; }
    const string& private_key_pem() const noexcept { return _private_key_pem; }
};

}
