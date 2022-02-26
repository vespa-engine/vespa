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

std::unique_ptr<TlsCryptoSocket>
TlsCryptoEngine::create_tls_client_crypto_socket(SocketHandle socket, const SocketSpec &peer_spec)
{
    auto codec = net::tls::CryptoCodec::create_default_client_codec(_tls_ctx, peer_spec, SocketAddress::peer_address(socket.get()));
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

std::unique_ptr<TlsCryptoSocket>
TlsCryptoEngine::create_tls_server_crypto_socket(SocketHandle socket)
{
    auto codec = net::tls::CryptoCodec::create_default_server_codec(_tls_ctx, SocketAddress::peer_address(socket.get()));
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

} // namespace vespalib
