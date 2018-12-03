// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "openssl_typedefs.h"
#include <vespa/vespalib/net/tls/tls_context.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/certificate_verification_callback.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::net::tls::impl {

class OpenSslTlsContextImpl : public TlsContext {
    SslCtxPtr _ctx;
    std::shared_ptr<CertificateVerificationCallback> _cert_verify_callback;
    TransportSecurityOptions _redacted_transport_options;
public:
    OpenSslTlsContextImpl(const TransportSecurityOptions& ts_opts,
                          std::shared_ptr<CertificateVerificationCallback> cert_verify_callback);
    ~OpenSslTlsContextImpl() override;

    ::SSL_CTX* native_context() const noexcept { return _ctx.get(); }
    // Transport options this context was created with, but with the private key
    // information scrubbed away.
    const TransportSecurityOptions& transport_security_options() const noexcept {
        return _redacted_transport_options;
    }
private:
    // Note: single use per instance; does _not_ clear existing chain!
    void add_certificate_authorities(stringref ca_pem);
    void add_certificate_chain(stringref chain_pem);
    void use_private_key(stringref key_pem);
    void verify_private_key();
    // Enable use of ephemeral key exchange (ECDHE), allowing forward secrecy.
    void enable_ephemeral_key_exchange();
    void disable_compression();
    // Explicitly disable TLS renegotiation for <= TLSv1.2 on OpenSSL versions
    // that support this. We don't support renegotiation in general (and will break
    // the connection if it's attempted by the peer), but this should signal
    // explicitly to the peer that it's not a supported action.
    void disable_renegotiation();
    void enforce_peer_certificate_verification();
    void set_provided_certificate_verification_callback();
};

}
