// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
class AthenzCredentials {

    private final X509Certificate certificate;
    private final KeyPair keyPair;
    private final SignedIdentityDocument identityDocument;

    AthenzCredentials(X509Certificate certificate,
                      KeyPair keyPair,
                      SignedIdentityDocument identityDocument) {
        this.certificate = certificate;
        this.keyPair = keyPair;
        this.identityDocument = identityDocument;
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

}
