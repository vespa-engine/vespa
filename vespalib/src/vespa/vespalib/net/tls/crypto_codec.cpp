// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "crypto_codec.h"
#include <vespa/vespalib/net/tls/impl/openssl_crypto_codec_impl.h>
#include <vespa/vespalib/net/tls/impl/openssl_tls_context_impl.h>
#include <cassert>

namespace vespalib::net::tls {

std::unique_ptr<CryptoCodec>
CryptoCodec::create_default_client_codec(std::shared_ptr<TlsContext> ctx,
                                         const SocketSpec& peer_spec,
                                         const SocketAddress& peer_address)
{
    auto ctx_impl = std::dynamic_pointer_cast<impl::OpenSslTlsContextImpl>(ctx); // only takes by const ref
    assert(ctx_impl);
    return impl::OpenSslCryptoCodecImpl::make_client_codec(std::move(ctx_impl), peer_spec, peer_address);
}

std::unique_ptr<CryptoCodec>
CryptoCodec::create_default_server_codec(std::shared_ptr<TlsContext> ctx,
                                         const SocketAddress& peer_address)
{
    auto ctx_impl = std::dynamic_pointer_cast<impl::OpenSslTlsContextImpl>(ctx); // only takes by const ref
    assert(ctx_impl);
    return impl::OpenSslCryptoCodecImpl::make_server_codec(std::move(ctx_impl), peer_address);
}

}
