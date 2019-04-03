// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A list of roles and their associated contexts. This defines the role membership of a tenant, and in which contexts
 * (see {@link Context}) those roles apply.
 *
 * @author mpolden
 * @author jonmv
 */
public class RoleMembership {

    private final Map<Role, Set<Context>> roles;

    private RoleMembership(Map<Role, Set<Context>> roles) {
        this.roles = roles.entrySet().stream()
                          .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                                entry -> Set.copyOf(entry.getValue())));
    }

    public static RoleMembership everyoneIn(SystemName system) {
        return in(system).add(Role.everyone).build();
    }

    public static Builder in(SystemName system) { return new BuilderWithRole(system); }

    /** Returns whether any role in this allows action to take place in path */
    public boolean allows(Action action, URI uri) {
        return roles.entrySet().stream().anyMatch(kv -> {
            Role role = kv.getKey();
            Set<Context> contexts = kv.getValue();
            return contexts.stream().anyMatch(context -> role.allows(action, uri, context));
        });
    }

    /** Returns the set of contexts for which the given role is valid. */
    public Set<Context> contextsFor(Role role) {
        return roles.getOrDefault(role, Collections.emptySet());
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

    public interface Builder {

        BuilderWithRole add(Role role);

        RoleMembership build();

    }

    public static class BuilderWithRole implements Builder {

        private final SystemName system;
        private final Map<Role, Set<Context>> roles;

        private Role current;

        private BuilderWithRole(SystemName system) {
            this.system = Objects.requireNonNull(system);
            this.roles = new HashMap<>();
        }

        @Override
        public BuilderWithRole add(Role role) {
            consumeCurrent(Context.unlimitedIn(system));
            current = role;
            return this;
        }

        public Builder limitedTo(TenantName tenant) {
            consumeCurrent(Context.limitedTo(tenant, system));
            return this;
        }

        public Builder limitedTo(TenantName tenant, ApplicationName application) {
            consumeCurrent(Context.limitedTo(tenant, application, system));
            return this;
        }

        @Override
        public RoleMembership build() {
            consumeCurrent(Context.unlimitedIn(system));
            return new RoleMembership(roles);
        }

        private void consumeCurrent(Context context) {
            if (current != null) {
                roles.putIfAbsent(current, new HashSet<>());
                roles.get(current).add(context);
            }
            current = null;
        }

    }

}
