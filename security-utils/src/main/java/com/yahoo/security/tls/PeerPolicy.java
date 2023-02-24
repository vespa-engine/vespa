// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author bjorncs
 */
public record PeerPolicy(String policyName, Optional<String> description, Set<String> capabilityNames,
                         CapabilitySet capabilities, List<RequiredPeerCredential> requiredCredentials) {

    public PeerPolicy {
        requiredCredentials = List.copyOf(requiredCredentials);
        capabilityNames = Set.copyOf(capabilityNames);
    }

    public PeerPolicy(String policyName, Optional<String> description,
                      CapabilitySet capabilities, List<RequiredPeerCredential> requiredCredentials) {
        this(policyName, description, capabilities.resolveNames(), capabilities, requiredCredentials);
    }

    public PeerPolicy(String policyName, List<RequiredPeerCredential> requiredCredentials) {
        this(policyName, Optional.empty(), CapabilitySet.all(), requiredCredentials);
    }

    public PeerPolicy(String policyName, String description, List<RequiredPeerCredential> requiredCredentials) {
        this(policyName, Optional.ofNullable(description), CapabilitySet.all(), requiredCredentials);
    }

    public PeerPolicy(String policyName, Optional<String> description, Collection<ToCapabilitySet> capabilities,
                      List<RequiredPeerCredential> requiredCredentials) {
        this(policyName, description, CapabilitySet.resolveNames(capabilities),
             CapabilitySet.unionOf(capabilities), requiredCredentials);
    }

    public PeerPolicy(String policyName, Optional<String> description, Set<String> capabilities,
                      List<RequiredPeerCredential> requiredCredentials) {
        this(policyName, description, capabilities, CapabilitySet.fromNames(capabilities),
             requiredCredentials);
    }
}
