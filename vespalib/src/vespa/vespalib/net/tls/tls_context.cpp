// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "tls_context.h"
#include <vespa/vespalib/net/tls/impl/openssl_tls_context_impl.h>

namespace vespalib::net::tls {

std::shared_ptr<TlsContext> TlsContext::create_default_context(const TransportSecurityOptions& opts) {
    return std::make_shared<impl::OpenSslTlsContextImpl>(opts);
}

}
