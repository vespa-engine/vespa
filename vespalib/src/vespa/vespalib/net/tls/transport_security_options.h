// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "certificate_verification_callback.h"
#include "peer_policies.h"
#include <memory>

namespace vespalib::net::tls {

class TransportSecurityOptions {
    vespalib::string _ca_certs_pem;
    vespalib::string _cert_chain_pem;
    vespalib::string _private_key_pem;
    AuthorizedPeers  _authorized_peers;
    std::vector<vespalib::string> _accepted_ciphers;
public:
    TransportSecurityOptions() = default;

    struct Builder {
        vespalib::string _ca_certs_pem;
        vespalib::string _cert_chain_pem;
        vespalib::string _private_key_pem;
        AuthorizedPeers  _authorized_peers;
        std::vector<vespalib::string> _accepted_ciphers;

        Builder();
        ~Builder();

        Builder& ca_certs_pem(vespalib::stringref pem) { _ca_certs_pem = pem; return *this; }
        Builder& cert_chain_pem(vespalib::stringref pem) { _cert_chain_pem = pem; return *this; }
        Builder& private_key_pem(vespalib::stringref pem) { _private_key_pem = pem; return *this; }
        Builder& authorized_peers(AuthorizedPeers auth) { _authorized_peers = std::move(auth); return *this; }
        Builder& accepted_ciphers(std::vector<vespalib::string> ciphers) {
            _accepted_ciphers = std::move(ciphers);
            return *this;
        }
    };

    explicit TransportSecurityOptions(Builder builder);

    TransportSecurityOptions(vespalib::string ca_certs_pem,
                             vespalib::string cert_chain_pem,
                             vespalib::string private_key_pem);

    TransportSecurityOptions(vespalib::string ca_certs_pem,
                             vespalib::string cert_chain_pem,
                             vespalib::string private_key_pem,
                             AuthorizedPeers authorized_peers);

    ~TransportSecurityOptions();

    const vespalib::string& ca_certs_pem() const noexcept { return _ca_certs_pem; }
    const vespalib::string& cert_chain_pem() const noexcept { return _cert_chain_pem; }
    const vespalib::string& private_key_pem() const noexcept { return _private_key_pem; }
    const AuthorizedPeers& authorized_peers() const noexcept { return _authorized_peers; }

    TransportSecurityOptions copy_without_private_key() const {
        return TransportSecurityOptions(_ca_certs_pem, _cert_chain_pem, "", _authorized_peers);
    }
    const std::vector<vespalib::string>& accepted_ciphers() const noexcept { return _accepted_ciphers; }
};

} // vespalib::net::tls
