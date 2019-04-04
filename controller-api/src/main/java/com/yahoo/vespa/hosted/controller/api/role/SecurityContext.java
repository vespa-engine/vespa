// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class SecurityContext {

    public static final String ATTRIBUTE_NAME = SecurityContext.class.getName();

    private final Principal principal;
    private final Set<Role> roles;

    public SecurityContext(Principal principal, Set<Role> roles) {
        this.principal = requireNonNull(principal);
        this.roles = Set.copyOf(roles);
    }

    public Principal principal() {
        return principal;
    }

    public Set<Role> roles() {
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecurityContext that = (SecurityContext) o;
        return Objects.equals(principal, that.principal) &&
               Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal, roles);
    }

    @Override
    public String toString() {
        return "SecurityContext{" +
               "principal=" + principal +
               ", roles=" + roles +
               '}';
    }
}
