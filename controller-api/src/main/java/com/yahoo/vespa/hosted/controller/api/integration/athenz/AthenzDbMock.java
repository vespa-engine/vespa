// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author bjorncs
 */
public class AthenzDbMock {

    public final Map<AthenzDomain, Domain> domains = new HashMap<>();
    public final List<AthenzIdentity> hostedOperators = new ArrayList<>();

    public AthenzDbMock addDomain(Domain domain) {
        domains.put(domain.name, domain);
        return this;
    }

    public Domain getOrCreateDomain(AthenzDomain domain) {
        return this.getOrCreateDomain(domain, Map.of());
    }

    public Domain getOrCreateDomain(AthenzDomain domain, Map<String, Object> attributes) {
        return domains.computeIfAbsent(domain, Domain::new).withAttributes(attributes);
    }

    public AthenzDbMock addHostedOperator(AthenzIdentity athenzIdentity) {
        hostedOperators.add(athenzIdentity);
        return this;
    }

    public static class Domain {

        public final AthenzDomain name;
        public final Set<AthenzIdentity> admins = new HashSet<>();
        public final Set<AthenzIdentity> tenantAdmins = new HashSet<>();
        public final Map<ApplicationId, Application> applications = new HashMap<>();
        public final Map<String, Service> services = new HashMap<>();
        public final List<Role> roles = new ArrayList<>();
        public final Map<String, Policy> policies = new HashMap<>();
        public boolean isVespaTenant = false;
        public final Map<String, Object> attributes = new HashMap<>();

        public Domain(AthenzDomain name) {
            this.name = name;
        }

        public Domain admin(AthenzIdentity identity) {
            admins.add(identity);
            policies.put("admin", new Policy("admin", identity.getFullName(), ".*", ".*"));
            return this;
        }

        public Domain tenantAdmin(AthenzIdentity identity) {
            tenantAdmins.add(identity);
            return this;
        }

        public Domain deleteTenantAdmin(AthenzIdentity identity) {
            tenantAdmins.remove(identity);
            return this;
        }

        public Domain withPolicy(String name, String principalRegex, String operation, String resource) {
            policies.put(name, new Policy(name, principalRegex, operation, resource));
            return this;
        }

        public Domain withAttributes(Map<String, Object> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        /**
         * Simulates establishing Vespa tenancy in Athens.
         */
        public void markAsVespaTenant() {
            isVespaTenant = true;
        }

        public boolean hasRole(String name) { return roles.stream().anyMatch(r -> r.name.equals(name)); }

        public boolean hasPolicy(String name) { return policies.containsKey(name); }

        public boolean checkAccess(AthenzIdentity principal, String action, String resource) {
            return policies.values().stream().anyMatch(a -> a.matches(principal, action, resource));
        }
    }

    public static class Application {

        public final Map<ApplicationAction, Set<AthenzIdentity>> acl = new HashMap<>();

        public Application() {
            acl.put(ApplicationAction.deploy, new HashSet<>());
            acl.put(ApplicationAction.read, new HashSet<>());
            acl.put(ApplicationAction.write, new HashSet<>());
        }

        public Application addRoleMember(ApplicationAction action, AthenzIdentity identity) {
            acl.get(action).add(identity);
            return this;
        }
    }

    public static class Service {

        public final boolean allowLaunch;

        public Service(boolean allowLaunch) {
            this.allowLaunch = allowLaunch;
        }
    }

    public static class Policy {
        private final String name;
        final List<Assertion> assertions = new ArrayList<>();

        public Policy(String name, String principal, String action, String resource) {
            this(name);
            this.assertions.add(new Assertion("grant", principal, action, resource));
        }

        public Policy(String name) { this.name = name; }

        public String name() {
            return name;
        }

        public boolean matches(String assertion) {
            return assertions.stream().anyMatch(a -> a.matches(assertion));
        }

        public boolean matches(AthenzIdentity principal, String action, String resource) {
            return assertions.stream().anyMatch(a -> a.matches(principal, action, resource));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Policy policy = (Policy) o;
            return Objects.equals(name, policy.name) && Objects.equals(assertions, policy.assertions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, assertions);
        }
    }

    public static class Assertion {
        private final String effect;
        private final String role;
        private final String action;
        private final String resource;

        public Assertion(String effect, String role, String action, String resource) {
            this.effect = effect;
            this.role = role;
            this.action = action;
            this.resource = resource;
        }

        public Assertion(String role, String action, String resource) { this("allow", role, action, resource); }

        public String effect() { return effect; }
        public String role() { return role; }
        public String action() { return action; }
        public String resource() { return resource; }

        public boolean matches(AthenzIdentity principal, String action, String resource) {
            return Pattern.compile(this.role).matcher(principal.getFullName()).matches()
                    &&  Pattern.compile(this.action).matcher(action).matches()
                    && Pattern.compile(this.resource).matcher(resource).matches();
        }

        public boolean matches(String assertion) { return asString().equals(assertion); }

        public String asString() { return String.format("%s %s to %s on %s", effect, action, role, resource).toLowerCase(); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Assertion assertion = (Assertion) o;
            return Objects.equals(effect, assertion.effect) && Objects.equals(role, assertion.role)
                    && Objects.equals(action, assertion.action) && Objects.equals(resource, assertion.resource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(effect, role, action, resource);
        }
    }

    public static class Role {
        private final String name;

        public Role(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Role role = (Role) o;
            return Objects.equals(name, role.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
