// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_security_options.h"
#include <openssl/crypto.h>
#include <cassert>

namespace vespalib::net::tls {

TransportSecurityOptions::TransportSecurityOptions(Params params)
    : _ca_certs_pem(std::move(params._ca_certs_pem)),
      _cert_chain_pem(std::move(params._cert_chain_pem)),
      _private_key_pem(std::move(params._private_key_pem)),
      _authorized_peers(std::move(params._authorized_peers)),
      _accepted_ciphers(std::move(params._accepted_ciphers)),
      _disable_hostname_validation(params._disable_hostname_validation)
{
}

TransportSecurityOptions::TransportSecurityOptions(vespalib::string ca_certs_pem,
                                                   vespalib::string cert_chain_pem,
                                                   vespalib::string private_key_pem,
                                                   AuthorizedPeers authorized_peers,
                                                   bool disable_hostname_validation)
    : _ca_certs_pem(std::move(ca_certs_pem)),
      _cert_chain_pem(std::move(cert_chain_pem)),
      _private_key_pem(std::move(private_key_pem)),
      _authorized_peers(std::move(authorized_peers)),
      _disable_hostname_validation(disable_hostname_validation)
{
}

TransportSecurityOptions::TransportSecurityOptions(const TransportSecurityOptions&) = default;
TransportSecurityOptions::TransportSecurityOptions(TransportSecurityOptions&&) noexcept = default;
TransportSecurityOptions& TransportSecurityOptions::operator=(TransportSecurityOptions&&) noexcept = default;

TransportSecurityOptions::~TransportSecurityOptions() {
    secure_memzero(&_private_key_pem[0], _private_key_pem.size());
}

TransportSecurityOptions TransportSecurityOptions::copy_without_private_key() const {
    return TransportSecurityOptions(_ca_certs_pem, _cert_chain_pem, "",
                                    _authorized_peers, _disable_hostname_validation);
}

void secure_memzero(void* buf, size_t size) noexcept {
    OPENSSL_cleanse(buf, size);
}

TransportSecurityOptions::Params::Params()
    : _ca_certs_pem(),
      _cert_chain_pem(),
      _private_key_pem(),
      _authorized_peers(),
      _accepted_ciphers(),
      _disable_hostname_validation(false)
{
}

TransportSecurityOptions::Params::~Params() {
    secure_memzero(&_private_key_pem[0], _private_key_pem.size());
}

TransportSecurityOptions::Params::Params(const Params&) = default;

TransportSecurityOptions::Params::Params(Params&&) noexcept = default;

TransportSecurityOptions::Params&
TransportSecurityOptions::Params::operator=(TransportSecurityOptions::Params&&) noexcept = default;

} // vespalib::net::tls
