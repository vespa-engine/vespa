package com.yahoo.security.tls.authz;

import com.yahoo.security.tls.policy.CapabilitySet;

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
        if (matchedPolicies.isEmpty() && !CapabilitySet.none().equals(capabilities)) throw new AssertionError();
        matchedPolicies = Set.copyOf(matchedPolicies);
    }

    public boolean authorized() { return matchedPolicies.size() > 0; }

    public X509Certificate peerCertificate() { return peerCertificateChain.get(0); }

}
