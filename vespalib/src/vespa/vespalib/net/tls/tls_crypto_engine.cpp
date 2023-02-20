// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tls_crypto_engine.h"
#include "crypto_codec.h"
#include "crypto_codec_adapter.h"

namespace vespalib {

TlsCryptoEngine::~TlsCryptoEngine() = default;

TlsCryptoEngine::TlsCryptoEngine(net::tls::TransportSecurityOptions tls_opts, net::tls::AuthorizationMode authz_mode)
    : _tls_ctx(net::tls::TlsContext::create_default_context(tls_opts, authz_mode))
{
}

std::unique_ptr<net::tls::CryptoCodec>
TlsCryptoEngine::create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &peer_spec)
{
    return net::tls::CryptoCodec::create_default_client_codec(_tls_ctx, peer_spec, SocketAddress::peer_address(socket.get()));
}

std::unique_ptr<net::tls::CryptoCodec>
TlsCryptoEngine::create_tls_server_crypto_codec(const SocketHandle &socket)
{
    return net::tls::CryptoCodec::create_default_server_codec(_tls_ctx, SocketAddress::peer_address(socket.get()));
}

CryptoSocket::UP
TlsCryptoEngine::create_client_crypto_socket(SocketHandle socket, const SocketSpec &peer_spec) {
    auto codec = create_tls_client_crypto_codec(socket, peer_spec);
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

CryptoSocket::UP
TlsCryptoEngine::create_server_crypto_socket(SocketHandle socket) {
    auto codec = create_tls_server_crypto_codec(socket);
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

} // namespace vespalib
