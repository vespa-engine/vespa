package com.yahoo.security.tls.authz;

import com.yahoo.security.tls.policy.CapabilitySet;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author bjorncs
 */
public record ConnectionAuthContext(List<X509Certificate> peerCertificateChain,
                                    CapabilitySet capabilities,
                                    SortedSet<String> matchedPolicies) {

    public ConnectionAuthContext {
        if (peerCertificateChain.isEmpty()) throw new IllegalArgumentException("Peer certificate chain is empty");
        peerCertificateChain = List.copyOf(peerCertificateChain);
        if (matchedPolicies.isEmpty() && !CapabilitySet.none().equals(capabilities)) throw new AssertionError();
        matchedPolicies = new TreeSet<>(matchedPolicies);
    }

    public boolean authorized() { return matchedPolicies.size() > 0; }

    public X509Certificate peerCertificate() { return peerCertificateChain.get(0); }

}
