// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import java.security.Principal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * @author tokle
 */
public class SecurityContext {

    public static final String ATTRIBUTE_NAME = SecurityContext.class.getName();

    private final Principal principal;
    private final Set<Role> roles;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public SecurityContext(Principal principal, Set<Role> roles, Instant issuedAt) {
        this(principal, roles, issuedAt, Instant.MAX);
    }

    public SecurityContext(Principal principal, Set<Role> roles, Instant issuedAt, Instant expiresAt) {
        this.principal = Objects.requireNonNull(principal);
        this.roles = Set.copyOf(roles);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public SecurityContext(Principal principal, Set<Role> roles) {
        this(principal, roles, Instant.EPOCH, Instant.MAX);
    }

    public Principal principal() {
        return principal;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    /** @return credential expiration or {@link Instant#MAX} is not available */
    public Instant expiresAt() { return expiresAt; }

    public SecurityContext withRoles(Set<Role> roles) {
        return new SecurityContext(this.principal, roles, this.issuedAt, this.expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecurityContext that = (SecurityContext) o;
        return Objects.equals(principal, that.principal) && Objects.equals(roles, that.roles)
                && Objects.equals(issuedAt, that.issuedAt) && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal, roles, issuedAt, expiresAt);
    }

    @Override
    public String toString() {
        return "SecurityContext{" +
                "principal=" + principal +
                ", roles=" + roles +
                ", issuedAt=" + issuedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }

}
