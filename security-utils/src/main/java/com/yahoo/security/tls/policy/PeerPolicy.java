// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author bjorncs
 */
public class PeerPolicy {

    private final String policyName;
    private final String description;
    private final Set<Role> assumedRoles;
    private final List<RequiredPeerCredential> requiredCredentials;

    public PeerPolicy(String policyName, Set<Role> assumedRoles, List<RequiredPeerCredential> requiredCredentials) {
        this(policyName, null, assumedRoles, requiredCredentials);
    }

    public PeerPolicy(
            String policyName, String description, Set<Role> assumedRoles, List<RequiredPeerCredential> requiredCredentials) {
        this.policyName = policyName;
        this.description = description;
        this.assumedRoles = assumedRoles;
        this.requiredCredentials = Collections.unmodifiableList(requiredCredentials);
    }

    public String policyName() {
        return policyName;
    }

    public Optional<String> description() { return Optional.ofNullable(description); }

    public Set<Role> assumedRoles() {
        return assumedRoles;
    }

    public List<RequiredPeerCredential> requiredCredentials() {
        return requiredCredentials;
    }

    @Override
    public String toString() {
        return "PeerPolicy{" +
                "policyName='" + policyName + '\'' +
                ", description='" + description + '\'' +
                ", assumedRoles=" + assumedRoles +
                ", requiredCredentials=" + requiredCredentials +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerPolicy that = (PeerPolicy) o;
        return Objects.equals(policyName, that.policyName) &&
                Objects.equals(description, that.description) &&
                Objects.equals(assumedRoles, that.assumedRoles) &&
                Objects.equals(requiredCredentials, that.requiredCredentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyName, description, assumedRoles, requiredCredentials);
    }
}
