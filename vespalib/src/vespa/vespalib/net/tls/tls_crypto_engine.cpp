// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tls_crypto_engine.h"
#include "crypto_codec.h"
#include "crypto_codec_adapter.h"

namespace vespalib {

TlsCryptoEngine::TlsCryptoEngine(net::tls::TransportSecurityOptions tls_opts)
    : _tls_ctx(net::tls::TlsContext::create_default_context(tls_opts))
{
}

CryptoSocket::UP
TlsCryptoEngine::create_crypto_socket(SocketHandle socket, bool is_server)
{
    auto mode = is_server ? net::tls::CryptoCodec::Mode::Server : net::tls::CryptoCodec::Mode::Client;
    auto codec = net::tls::CryptoCodec::create_default_codec(*_tls_ctx, mode);
    return std::make_unique<net::tls::CryptoCodecAdapter>(std::move(socket), std::move(codec));
}

} // namespace vespalib
