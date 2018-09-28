// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/crypto_engine.h>
#include "tls_crypto_socket.h"
#include "transport_security_options.h"
#include "tls_context.h"

namespace vespalib {

/**
 * Crypto engine implementing TLS.
 **/
class TlsCryptoEngine : public CryptoEngine
{
private:
    std::unique_ptr<net::tls::TlsContext> _tls_ctx;    
public:
    TlsCryptoEngine(net::tls::TransportSecurityOptions tls_opts);
    std::unique_ptr<TlsCryptoSocket> create_tls_crypto_socket(SocketHandle socket, bool is_server);
    CryptoSocket::UP create_crypto_socket(SocketHandle socket, bool is_server) override {
        return create_tls_crypto_socket(std::move(socket), is_server);
    }
};

} // namespace vespalib
