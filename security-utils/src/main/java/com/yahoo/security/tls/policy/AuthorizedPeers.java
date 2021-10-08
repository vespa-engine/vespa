// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 */
public class AuthorizedPeers {

    private final Set<PeerPolicy> peerPolicies;

    public AuthorizedPeers(Set<PeerPolicy> peerPolicies) {
        this.peerPolicies = verifyPeerPolicies(peerPolicies);
    }

    private Set<PeerPolicy> verifyPeerPolicies(Set<PeerPolicy> peerPolicies) {
        long distinctNames = peerPolicies.stream()
                .map(PeerPolicy::policyName)
                .distinct()
                .count();
        if (distinctNames != peerPolicies.size()) {
            throw new IllegalArgumentException("'authorized-peers' contains entries with duplicate names");
        }
        return Collections.unmodifiableSet(peerPolicies);
    }

    public Set<PeerPolicy> peerPolicies() {
        return peerPolicies;
    }

    @Override
    public String toString() {
        return "AuthorizedPeers{" +
                "peerPolicies=" + peerPolicies +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizedPeers that = (AuthorizedPeers) o;
        return Objects.equals(peerPolicies, that.peerPolicies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerPolicies);
    }
}
