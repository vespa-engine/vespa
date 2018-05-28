// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;

import javax.net.ssl.SSLContext;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
class AthenzCredentials {

    private final String nToken;
    private final X509Certificate certificate;
    private final KeyPair keyPair;
    private final SignedIdentityDocument identityDocument;
    private final SSLContext identitySslContext;

    AthenzCredentials(String nToken,
                      X509Certificate certificate,
                      KeyPair keyPair,
                      SignedIdentityDocument identityDocument,
                      SSLContext identitySslContext) {
        this.nToken = nToken;
        this.certificate = certificate;
        this.keyPair = keyPair;
        this.identityDocument = identityDocument;
        this.identitySslContext = identitySslContext;
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

    SSLContext getIdentitySslContext() {
        return identitySslContext;
    }
}
