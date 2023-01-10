// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/crypto_engine.h>
#include "tls_crypto_socket.h"
#include "transport_security_options.h"
#include "tls_context.h"

namespace vespalib {

namespace net { namespace tls { class CryptoCodec; }}

class AbstractTlsCryptoEngine : public CryptoEngine {
public:
    using CryptoCodec = net::tls::CryptoCodec;
    virtual std::unique_ptr<CryptoCodec> create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &spec) = 0;
    virtual std::unique_ptr<CryptoCodec> create_tls_server_crypto_codec(const SocketHandle &socket) = 0;
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
    std::unique_ptr<CryptoCodec> create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &spec) override;
    std::unique_ptr<CryptoCodec> create_tls_server_crypto_codec(const SocketHandle &socket) override;
    bool use_tls_when_client() const override { return true; }
    bool always_use_tls_when_server() const override { return true; }
    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) override;
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override;
    std::shared_ptr<net::tls::TlsContext> tls_context() const noexcept { return _tls_ctx; };
};

} // namespace vespalib
