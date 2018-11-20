// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "tls_context.h"
#include <vespa/vespalib/net/tls/impl/openssl_tls_context_impl.h>
#include <vespa/vespalib/net/tls/policy_checking_certificate_verifier.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>

namespace vespalib::net::tls {

std::shared_ptr<TlsContext> TlsContext::create_default_context(const TransportSecurityOptions& opts) {
    auto verifier = create_verify_callback_from(opts.authorized_peers());
    return std::make_shared<impl::OpenSslTlsContextImpl>(opts, std::move(verifier));
}

std::shared_ptr<TlsContext> TlsContext::create_default_context(
        const TransportSecurityOptions& opts,
        std::shared_ptr<CertificateVerificationCallback> cert_verify_callback) {
    return std::make_shared<impl::OpenSslTlsContextImpl>(opts, std::move(cert_verify_callback));
}

}
