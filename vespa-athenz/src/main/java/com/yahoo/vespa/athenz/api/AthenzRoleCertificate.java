// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class AthenzRoleCertificate {

    private final X509Certificate certificate;
    private final PrivateKey privateKey;

    public AthenzRoleCertificate(X509Certificate certificate, PrivateKey privateKey) {
        this.certificate = certificate;
        this.privateKey = privateKey;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
