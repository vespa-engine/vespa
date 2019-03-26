// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.config.provision.SystemName;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A list of roles and their associated contexts. This defines the role membership of a tenant, and in which contexts
 * (see {@link Context}) those roles apply.
 *
 * @author mpolden
 */
public class RoleMembership {

    private final Map<Role, Set<Context>> roles;

    public RoleMembership(Map<Role, Set<Context>> roles) {
        if (roles.values().stream()
                 .flatMap(Set::stream)
                 .map(Context::system)
                 .distinct().count() != 1)
            throw new IllegalArgumentException("A RoleMembership should be defined only for a single system.");

        this.roles = Map.copyOf(roles);
    }

    public static RoleMembership everyoneIn(SystemName system) {
        return new RoleMembership(Map.of(Role.everyone, Set.of(Context.unlimitedIn(system))));
    }

    /** Returns whether any role in this allows action to take place in path */
    public boolean allows(Action action, String path) {
        return roles.entrySet().stream().anyMatch(kv -> {
            Role role = kv.getKey();
            Set<Context> contexts = kv.getValue();
            return contexts.stream().anyMatch(context -> role.allows(action, path, context));
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
        RoleMembership membership(Principal user);
    }

}
