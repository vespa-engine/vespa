// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "openssl_typedefs.h"
#include <vespa/vespalib/net/tls/tls_context.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::net::tls::impl {

class OpenSslTlsContextImpl : public TlsContext {
    ::SSL_CTX* _ctx;
public:
    explicit OpenSslTlsContextImpl(const TransportSecurityOptions&);
    ~OpenSslTlsContextImpl() override;

    ::SSL_CTX* native_context() const noexcept { return _ctx; }
private:
    // Note: single use per instance; does _not_ clear existing chain!
    void add_certificate_authorities(stringref ca_pem);
    void add_certificate_chain(stringref chain_pem);
    void use_private_key(stringref key_pem);
    void verify_private_key();
    // Enable use of ephemeral key exchange (ECDHE), allowing forward secrecy.
    void enable_ephemeral_key_exchange();
    void disable_compression();
};

}