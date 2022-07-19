package com.yahoo.security.tls;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

/**
 * @author bjorncs
 */
public record ConnectionAuthContext(List<X509Certificate> peerCertificateChain,
                                    CapabilitySet capabilities,
                                    Set<String> matchedPolicies) {

    public ConnectionAuthContext {
        if (peerCertificateChain.isEmpty()) throw new IllegalArgumentException("Peer certificate chain is empty");
        peerCertificateChain = List.copyOf(peerCertificateChain);
        matchedPolicies = Set.copyOf(matchedPolicies);
    }

    public boolean authorized() { return !capabilities.hasNone(); }

    public X509Certificate peerCertificate() { return peerCertificateChain.get(0); }

}
