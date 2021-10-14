// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.authz;

import com.yahoo.security.tls.policy.Role;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 */
public class AuthorizationResult {
    private final Set<Role> assumedRoles;
    private final Set<String> matchedPolicies;

    public AuthorizationResult(Set<Role> assumedRoles, Set<String> matchedPolicies) {
        this.assumedRoles = Collections.unmodifiableSet(assumedRoles);
        this.matchedPolicies = Collections.unmodifiableSet(matchedPolicies);
    }

    public Set<Role> assumedRoles() {
        return assumedRoles;
    }

    public Set<String> matchedPolicies() {
        return matchedPolicies;
    }

    public boolean succeeded() {
        return matchedPolicies.size() > 0;
    }

    @Override
    public String toString() {
        return "AuthorizationResult{" +
                "assumedRoles=" + assumedRoles +
                ", matchedPolicies=" + matchedPolicies +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizationResult that = (AuthorizationResult) o;
        return Objects.equals(assumedRoles, that.assumedRoles) &&
                Objects.equals(matchedPolicies, that.matchedPolicies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assumedRoles, matchedPolicies);
    }
}
