// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

/**
 * Wraps a {@link java.security.cert.X509Certificate} with its {@link java.security.PrivateKey}.
 * Primary motivation is APIs where the callee must correctly observe an atomic update of both certificate and key.
 *
 * @author bjorncs
 */
public class X509CertificateWithKey {

    private final List<X509Certificate> certificate;
    private final PrivateKey privateKey;

    public X509CertificateWithKey(X509Certificate certificate, PrivateKey privateKey) {
        this(Collections.singletonList(certificate), privateKey);
    }

    public X509CertificateWithKey(List<X509Certificate> certificate, PrivateKey privateKey) {
        if (certificate.isEmpty()) throw new IllegalArgumentException();
        this.certificate = certificate;
        this.privateKey = privateKey;
    }

    public X509Certificate certificate() { return certificate.get(0); }
    public List<X509Certificate> certificateWithIntermediates() { return certificate; }
    public PrivateKey privateKey() { return privateKey; }
}
