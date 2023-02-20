// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maybe_tls_crypto_engine.h"
#include "maybe_tls_crypto_socket.h"
#include "crypto_codec.h"

namespace vespalib {

MaybeTlsCryptoEngine::~MaybeTlsCryptoEngine() = default;

std::unique_ptr<net::tls::CryptoCodec>
MaybeTlsCryptoEngine::create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &spec) {
    return _tls_engine->create_tls_client_crypto_codec(socket, spec);
}

std::unique_ptr<net::tls::CryptoCodec>
MaybeTlsCryptoEngine::create_tls_server_crypto_codec(const SocketHandle &socket) {
    return _tls_engine->create_tls_server_crypto_codec(socket);
}

CryptoSocket::UP
MaybeTlsCryptoEngine::create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec)
{
    if (_use_tls_when_client) {
        return _tls_engine->create_client_crypto_socket(std::move(socket), spec);
    } else {
        return _null_engine->create_client_crypto_socket(std::move(socket), spec);
    }
}

CryptoSocket::UP
MaybeTlsCryptoEngine::create_server_crypto_socket(SocketHandle socket)
{
    return std::make_unique<MaybeTlsCryptoSocket>(std::move(socket), _tls_engine);
}

} // namespace vespalib
