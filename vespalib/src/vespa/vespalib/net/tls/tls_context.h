// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "authorization_mode.h"
#include <memory>

namespace vespalib::net::tls {

class TransportSecurityOptions;
struct CertificateVerificationCallback;

struct TlsContext {
    virtual ~TlsContext() = default;

    // Create a TLS context which verifies certificates according to the provided options'
    // CA trust roots AND authorized peer policies
    static std::shared_ptr<TlsContext> create_default_context(const TransportSecurityOptions&,
                                                              AuthorizationMode);
    // Create a TLS context where the certificate verification callback is explicitly provided.
    // IMPORTANT: This does NOT verify that the peer satisfies the authorized peer policies!
    //            It only verifies that a peer is signed by a trusted CA. This function should
    //            therefore only be used in very special circumstances, such as unit tests.
    static std::shared_ptr<TlsContext> create_default_context(
            const TransportSecurityOptions&,
            std::shared_ptr<CertificateVerificationCallback> cert_verify_callback,
            AuthorizationMode);
};

}
