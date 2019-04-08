// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import java.net.URI;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A role is a combination of a {@link RoleDefinition} and a {@link Context}, which allows evaluation
 * of access control for a given action on a resource. Create using {@link Roles}.
 *
 * @author jonmv
 */
public abstract class Role {

    private final RoleDefinition roleDefinition;
    final Context context;

    Role(RoleDefinition roleDefinition, Context context) {
        this.roleDefinition = requireNonNull(roleDefinition);
        this.context = requireNonNull(context);
    }

    /** Returns the role definition of this bound role. */
    public RoleDefinition definition() { return roleDefinition; }

    /** Returns whether this role is allowed to perform the given action on the given resource. */
    public boolean allows(Action action, URI uri) {
        return roleDefinition.policies().stream().anyMatch(policy -> policy.evaluate(action, uri, context));
    }

    /** Returns whether the other role is a parent of this, and has a context included in this role's context. */
    public boolean implies(Role other) {
        if ( ! context.system().equals(other.context.system()))
            throw new IllegalStateException("Coexisting roles should always be in the same system.");

        return    (context.tenant().isEmpty() || context.tenant().equals(other.context.tenant()))
               && (context.application().isEmpty() || context.application().equals(other.context.application()))
               && roleDefinition.inherited().contains(other.roleDefinition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return roleDefinition == role.roleDefinition &&
               Objects.equals(context, role.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleDefinition, context);
    }

}

