// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include "tls_crypto_engine.h"
#include <vespa/vespalib/net/crypto_engine.h>

namespace vespalib {

/**
 * A crypto engine that supports both tls encrypted connections and
 * unencrypted connections. The use of tls for incoming connections is
 * auto-detected using clever heuristics. The use of tls for outgoing
 * connections is controlled by the use_tls_when_client flag given to
 * the constructor.
 **/
class MaybeTlsCryptoEngine : public AbstractTlsCryptoEngine
{
private:
    std::shared_ptr<NullCryptoEngine> _null_engine;
    std::shared_ptr<AbstractTlsCryptoEngine> _tls_engine;
    bool _use_tls_when_client;

public:
    MaybeTlsCryptoEngine(std::shared_ptr<AbstractTlsCryptoEngine> tls_engine,
                         bool use_tls_when_client)
        : _null_engine(std::make_shared<NullCryptoEngine>()),
          _tls_engine(std::move(tls_engine)),
          _use_tls_when_client(use_tls_when_client) {}
    ~MaybeTlsCryptoEngine() override;
    std::unique_ptr<CryptoCodec> create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &spec) override;
    std::unique_ptr<CryptoCodec> create_tls_server_crypto_codec(const SocketHandle &socket) override;
    bool use_tls_when_client() const override { return _use_tls_when_client; }
    bool always_use_tls_when_server() const override { return false; }
    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) override;
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override;
};

} // namespace vespalib
