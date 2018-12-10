// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/crypto_engine.h>
#include "tls_crypto_socket.h"
#include "transport_security_options.h"
#include "tls_context.h"

namespace vespalib {

class AbstractTlsCryptoEngine : public CryptoEngine {
public:
    virtual std::unique_ptr<TlsCryptoSocket> create_tls_crypto_socket(SocketHandle socket, bool is_server) = 0;
};

/**
 * Crypto engine implementing TLS.
 **/
class TlsCryptoEngine : public AbstractTlsCryptoEngine
{
private:
    std::shared_ptr<net::tls::TlsContext> _tls_ctx;
public:
    explicit TlsCryptoEngine(net::tls::TransportSecurityOptions tls_opts,
                             net::tls::AuthorizationMode authz_mode = net::tls::AuthorizationMode::Enforce);
    std::unique_ptr<TlsCryptoSocket> create_tls_crypto_socket(SocketHandle socket, bool is_server) override;
    CryptoSocket::UP create_crypto_socket(SocketHandle socket, bool is_server) override {
        return create_tls_crypto_socket(std::move(socket), is_server);
    }

    std::shared_ptr<net::tls::TlsContext> tls_context() const noexcept { return _tls_ctx; };
};

} // namespace vespalib
