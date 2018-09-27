// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maybe_tls_crypto_engine.h"
#include "maybe_tls_crypto_socket.h"

namespace vespalib {

CryptoSocket::UP
MaybeTlsCryptoEngine::create_crypto_socket(SocketHandle socket, bool is_server)
{
    if (is_server) {
        return std::make_unique<MaybeTlsCryptoSocket>(std::move(socket), _tls_engine);
    } else if (_use_tls_when_client) {
        return _tls_engine->create_crypto_socket(std::move(socket), false);
    } else {
        return _null_engine->create_crypto_socket(std::move(socket), false);
    }
}

} // namespace vespalib
