// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import org.apache.http.conn.ssl.X509HostnameVerifier;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link HostnameVerifier} / {@link X509HostnameVerifier} that validates
 * Athenz x509 certificates using the identity in the Common Name attribute.
 *
 * @author bjorncs
 */
// TODO Move to dedicated Athenz bundle
public class AthenzIdentityVerifier implements X509HostnameVerifier {

    private static final Logger log = Logger.getLogger(AthenzIdentityVerifier.class.getName());

    private final Set<AthenzIdentity> allowedIdentities;

    public AthenzIdentityVerifier(Set<AthenzIdentity> allowedIdentities) {
        this.allowedIdentities = allowedIdentities;
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        try {
            X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
            return isTrusted(AthenzUtils.createAthenzIdentity(cert));
        } catch (SSLPeerUnverifiedException e) {
            log.log(Level.WARNING, "Unverified client: " + hostname);
            return false;
        }
    }

    @Override
    public void verify(String host, SSLSocket ssl) {
        // all sockets allowed
    }

    @Override
    public void verify(String hostname, X509Certificate certificate) throws SSLException {
        AthenzIdentity identity = AthenzUtils.createAthenzIdentity(certificate);
        if (!isTrusted(identity)) {
            throw new SSLException("Athenz identity is not trusted: " + identity.getFullName());
        }
    }

    @Override
    public void verify(String hostname, String[] cns, String[] subjectAlts) throws SSLException {
        AthenzIdentity identity = AthenzUtils.createAthenzIdentity(cns[0]);
        if (!isTrusted(identity)) {
            throw new SSLException("Athenz identity is not trusted: " + identity.getFullName());
        }
    }

    private boolean isTrusted(AthenzIdentity identity) {
        return allowedIdentities.contains(identity);
    }
}

