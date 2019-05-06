// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author bjorncs
 */
public class SecurityContext {

    private final List<X509Certificate> peerCertificateChain;

    public SecurityContext(List<X509Certificate> peerCertificateChain) {
        this.peerCertificateChain = peerCertificateChain;
    }

    /**
     * @return the peer certificate chain if the peer was authenticated, empty list if not.
     */
    public List<X509Certificate> peerCertificateChain() {
        return peerCertificateChain;
    }
}
