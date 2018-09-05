// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "crypto_codec.h"
#include <vespa/vespalib/net/tls/impl/openssl_crypto_codec_impl.h>
#include <vespa/vespalib/net/tls/impl/openssl_tls_context_impl.h>
#include <cassert>

namespace vespalib::net::tls {

std::unique_ptr<CryptoCodec> CryptoCodec::create_default_codec(TlsContext& ctx, Mode mode) {
    auto* ssl_ctx = dynamic_cast<impl::OpenSslTlsContextImpl*>(&ctx);
    assert(ssl_ctx != nullptr);
    return std::make_unique<impl::OpenSslCryptoCodecImpl>(*ssl_ctx->native_context(), mode);
}

}
