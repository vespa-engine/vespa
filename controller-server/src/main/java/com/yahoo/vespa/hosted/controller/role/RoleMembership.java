// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A list of roles and their associated contexts. This defines the role membership of a tenant, and in which contexts
 * (see {@link Context}) those roles apply.
 *
 * @author mpolden
 * @author jonmv
 */
public class RoleMembership { // TODO replace with Set<RoleWithContext>

    private final Map<Role, Set<Context>> roles;

    RoleMembership(Map<Role, Set<Context>> roles) {
        this.roles = roles.entrySet().stream()
                          .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                                entry -> Set.copyOf(entry.getValue())));
    }

    public RoleMembership and(RoleMembership other) {
        return new RoleMembership(Stream.concat(this.roles.entrySet().stream(),
                                                other.roles.entrySet().stream())
                                        .collect(Collectors.toMap(Map.Entry::getKey,
                                                                  Map.Entry::getValue,
                                                                  (set1, set2) -> Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toUnmodifiableSet()))));
    }

    /**
     * Returns whether any role in this allows action to take place in path
     */
    public boolean allows(Action action, String path) {
        return roles.entrySet().stream().anyMatch(kv -> {
            Role role = kv.getKey();
            Set<Context> contexts = kv.getValue();
            return contexts.stream().anyMatch(context -> role.allows(action, path, context));
        });
    }

    /**
     * Returns the set of contexts for which the given role is valid.
     */
    public Set<Context> contextsFor(Object role) { // TODO fix.
        return roles.getOrDefault((Role) role, Collections.emptySet());
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

        RoleMembership membership(Principal user, Optional<String> path); // TODO get rid of path.
    }

}
