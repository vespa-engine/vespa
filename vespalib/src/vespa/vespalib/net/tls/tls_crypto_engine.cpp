// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tls_crypto_engine.h"
#include "crypto_codec.h"
#include "crypto_codec_adapter.h"

namespace vespalib {

TlsCryptoEngine::TlsCryptoEngine(net::tls::TransportSecurityOptions tls_opts, net::tls::AuthorizationMode authz_mode)
    : _tls_ctx(net::tls::TlsContext::create_default_context(tls_opts, authz_mode))
{
}

std::unique_ptr<TlsCryptoSocket>
TlsCryptoEngine::create_tls_client_crypto_socket(SocketHandle socket, const SocketSpec &)
{
    auto mode = net::tls::CryptoCodec::Mode::Client;
    auto codec = net::tls::CryptoCodec::create_default_codec(_tls_ctx, SocketAddress::peer_address(socket.get()), mode);
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

std::unique_ptr<TlsCryptoSocket>
TlsCryptoEngine::create_tls_server_crypto_socket(SocketHandle socket)
{
    auto mode = net::tls::CryptoCodec::Mode::Server;
    auto codec = net::tls::CryptoCodec::create_default_codec(_tls_ctx, SocketAddress::peer_address(socket.get()), mode);
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

} // namespace vespalib
