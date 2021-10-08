// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "peer_credentials.h"

namespace vespalib::net::tls {

// Verification callback invoked when a signed X509 certificate is presented
// from a peer during TLS handshaking.
// Only invoked for the leaf peer certificate, _not_ for any CAs (root or intermediate).
// Only invoked iff the certificate has already passed OpenSSL pre-verification.
struct CertificateVerificationCallback {
    virtual ~CertificateVerificationCallback() = default;
    // Return true iff the peer credentials pass verification, false otherwise.
    // Must be thread safe.
    virtual bool verify(const PeerCredentials& peer_creds) const = 0;
};

// Simplest possible certificate verification callback which accepts the certificate
// iff all its pre-verification by OpenSSL has passed. This means its chain is valid
// and it is signed by a trusted CA.
struct AcceptAllPreVerifiedCertificates : CertificateVerificationCallback {
    bool verify([[maybe_unused]] const PeerCredentials& peer_creds) const override {
        return true; // yolo
    }
};

}
