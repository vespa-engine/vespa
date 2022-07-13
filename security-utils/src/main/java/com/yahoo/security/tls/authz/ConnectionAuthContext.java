package com.yahoo.security.tls.authz;

import com.yahoo.security.tls.policy.CapabilitySet;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author bjorncs
 */
public record ConnectionAuthContext(List<X509Certificate> peerCertificate,
                                    CapabilitySet capabilities,
                                    SortedSet<String> matchedPolicies) {

    public ConnectionAuthContext {
        matchedPolicies = new TreeSet<>(matchedPolicies);
    }

    public boolean succeeded() { return matchedPolicies.size() > 0; }

}
