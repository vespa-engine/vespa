// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::net::tls {

class TransportSecurityOptions {
    vespalib::string _ca_certs_pem;
    vespalib::string _cert_chain_pem;
    vespalib::string _private_key_pem;
public:
    TransportSecurityOptions() = default;

    TransportSecurityOptions(vespalib::string ca_certs_pem,
                             vespalib::string cert_chain_pem,
                             vespalib::string private_key_pem)
         : _ca_certs_pem(std::move(ca_certs_pem)),
           _cert_chain_pem(std::move(cert_chain_pem)),
           _private_key_pem(std::move(private_key_pem))
    {}
    ~TransportSecurityOptions();

    const vespalib::string& ca_certs_pem() const noexcept { return _ca_certs_pem; }
    const vespalib::string& cert_chain_pem() const noexcept { return _cert_chain_pem; }
    const vespalib::string& private_key_pem() const noexcept { return _private_key_pem; }
};

}
