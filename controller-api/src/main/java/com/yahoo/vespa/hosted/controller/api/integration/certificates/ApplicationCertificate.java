// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Represents a certificate chain and a reference to the private key used for generating the certificate
 *
 * @author mortent
 * @author andreer
 */
public class ApplicationCertificate {
    private final List<X509Certificate> certificateChain;
    private final KeyId keyId;

    public ApplicationCertificate(List<X509Certificate> certificateChain, KeyId keyId) {
        this.certificateChain = certificateChain;
        this.keyId = keyId;
    }

    public List<X509Certificate> certificateChain() {
        return certificateChain;
    }

    public KeyId keyId() {
        return keyId;
    }
}
