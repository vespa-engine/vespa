// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * @author bjorncs
 */
class AthenzCredentials {

    private final String nToken;
    private final X509Certificate certificate;
    private final KeyPair keyPair;
    private final SignedIdentityDocument identityDocument;
    private final Instant createdAt;

    AthenzCredentials(String nToken,
                      X509Certificate certificate,
                      KeyPair keyPair,
                      SignedIdentityDocument identityDocument,
                      Instant createdAt) {
        this.nToken = nToken;
        this.certificate = certificate;
        this.keyPair = keyPair;
        this.identityDocument = identityDocument;
        this.createdAt = createdAt;
    }

    String getNToken() {
        return nToken;
    }

    X509Certificate getCertificate() {
        return certificate;
    }

    KeyPair getKeyPair() {
        return keyPair;
    }

    SignedIdentityDocument getIdentityDocument() {
        return identityDocument;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

}
