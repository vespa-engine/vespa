// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import java.util.Map;
import java.util.Set;

/**
 * A list of roles and their associated contexts. This defines the role membership of a tenant, and in which contexts
 * (see {@link Context}) those roles apply.
 *
 * @author mpolden
 */
public class RoleMembership {

    private final Map<Role, Set<Context>> roles;

    public RoleMembership(Map<Role, Set<Context>> roles) {
        this.roles = Map.copyOf(roles);
    }

    /** Returns whether any role in this allows action to take place in path */
    public boolean allow(Action action, String path) {
        return roles.entrySet().stream().anyMatch(kv -> {
            Role role = kv.getKey();
            Set<Context> contexts = kv.getValue();
            return contexts.stream().anyMatch(context -> role.allow(action, path, context));
        });
    }

    @Override
    public String toString() {
        return "roles " + roles;
    }

    /**
     * A role resolver. Identity providers can implement this to translate their internal representation of role
     * membership to a {@link RoleMembership}.
     */
    public interface Resolver {
        RoleMembership membership();
    }

}
