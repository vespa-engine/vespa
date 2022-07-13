// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Set;

/**
 * @author bjorncs
 */
public record AuthorizedPeers(Set<PeerPolicy> peerPolicies) {

    public AuthorizedPeers {
        peerPolicies = verifyPeerPolicies(peerPolicies);
    }

    private static Set<PeerPolicy> verifyPeerPolicies(Set<PeerPolicy> peerPolicies) {
        long distinctNames = peerPolicies.stream()
                .map(PeerPolicy::policyName)
                .distinct()
                .count();
        if (distinctNames != peerPolicies.size()) {
            throw new IllegalArgumentException("'authorized-peers' contains entries with duplicate names");
        }
        return Set.copyOf(peerPolicies);
    }

}
