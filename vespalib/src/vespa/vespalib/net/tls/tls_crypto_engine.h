// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/crypto_engine.h>
#include "tls_crypto_socket.h"
#include "transport_security_options.h"
#include "tls_context.h"

namespace vespalib {

class AbstractTlsCryptoEngine : public CryptoEngine {
public:
    virtual std::unique_ptr<TlsCryptoSocket> create_tls_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) = 0;
    virtual std::unique_ptr<TlsCryptoSocket> create_tls_server_crypto_socket(SocketHandle socket) = 0;
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
    ~TlsCryptoEngine() override;
    std::unique_ptr<TlsCryptoSocket> create_tls_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) override;
    std::unique_ptr<TlsCryptoSocket> create_tls_server_crypto_socket(SocketHandle socket) override;
    bool use_tls_when_client() const override { return true; }
    bool always_use_tls_when_server() const override { return true; }
    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) override {
        return create_tls_client_crypto_socket(std::move(socket), spec);
    }
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override {
        return create_tls_server_crypto_socket(std::move(socket));
    }

    std::shared_ptr<net::tls::TlsContext> tls_context() const noexcept { return _tls_ctx; };
};

} // namespace vespalib
