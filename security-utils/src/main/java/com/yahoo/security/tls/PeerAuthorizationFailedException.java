// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author bjorncs
 */
public class PeerAuthorizationFailedException extends CertificateException {
    private final List<X509Certificate> certChain;

    public PeerAuthorizationFailedException(String msg, List<X509Certificate> certChain) {
        super(msg);
        this.certChain = certChain;
    }

    public PeerAuthorizationFailedException(String msg) { this(msg, List.of()); }

    public List<X509Certificate> peerCertificateChain() { return certChain; }
}

