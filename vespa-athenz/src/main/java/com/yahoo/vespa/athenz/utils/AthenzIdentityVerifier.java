// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzIdentity;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link HostnameVerifier} that validates Athenz x509 certificates using the identity in the Common Name attribute.
 *
 * @author bjorncs
 */
public class AthenzIdentityVerifier implements HostnameVerifier {

    private static final Logger log = Logger.getLogger(AthenzIdentityVerifier.class.getName());

    private final Set<AthenzIdentity> allowedIdentities;

    public AthenzIdentityVerifier(Set<AthenzIdentity> allowedIdentities) {
        this.allowedIdentities = allowedIdentities;
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        try {
            X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
            return isTrusted(AthenzIdentities.from(cert));
        } catch (SSLPeerUnverifiedException e) {
            log.log(Level.WARNING, "Unverified client: " + hostname);
            return false;
        }
    }

    public boolean isTrusted(AthenzIdentity identity) {
        return allowedIdentities.contains(identity);
    }

}

