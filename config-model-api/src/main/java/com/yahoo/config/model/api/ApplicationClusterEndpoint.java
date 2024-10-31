// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import java.util.List;
import java.util.Objects;

/**
 * Represents one endpoint for an application cluster
 *
 * @author mortent
 */
public class ApplicationClusterEndpoint {

    private final DnsName dnsName;
    private final Scope scope;
    private final RoutingMethod routingMethod;
    private final int weight;
    private final List<String> hostNames;
    private final String clusterId;
    private final AuthMethod authMethod;

    private ApplicationClusterEndpoint(DnsName dnsName, Scope scope, RoutingMethod routingMethod, int weight, List<String> hostNames, String clusterId, AuthMethod authMethod) {
        this.dnsName = Objects.requireNonNull(dnsName);
        this.scope = Objects.requireNonNull(scope);
        this.routingMethod = Objects.requireNonNull(routingMethod);
        this.weight = weight;
        this.hostNames = List.copyOf(Objects.requireNonNull(hostNames));
        this.clusterId = Objects.requireNonNull(clusterId);
        this.authMethod = Objects.requireNonNull(authMethod);
    }

    public DnsName dnsName() {
        return dnsName;
    }

    public Scope scope() {
        return scope;
    }

    public RoutingMethod routingMethod() {
        return routingMethod;
    }

    public int weight() {
        return weight;
    }

    public List<String> hostNames() {
        return hostNames;
    }

    public String clusterId() {
        return clusterId;
    }

    public AuthMethod authMethod() {
        return authMethod;
    }

    @Override
    public String toString() {
        return "ApplicationClusterEndpoint{" +
               "dnsName=" + dnsName +
               ", scope=" + scope +
               ", routingMethod=" + routingMethod +
               ", weight=" + weight +
               ", hostNames=" + hostNames +
               ", clusterId='" + clusterId + '\'' +
               ", authMethod=" + authMethod +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum Scope { application, global, zone }

    public enum RoutingMethod { shared, sharedLayer4, exclusive }

    public enum AuthMethod { mtls, token }

    public static class Builder {

        private DnsName dnsName;
        private Scope scope;
        private RoutingMethod routingMethod;
        private int weight = 1;
        private List<String> hosts;
        private String clusterId;
        private AuthMethod authMethod;

        public Builder dnsName(DnsName name) {
            this.dnsName = name;
            return this;
        }

        public Builder zoneScope() {
            this.scope = Scope.zone;
            return this;
        }

        public Builder scope(Scope scope) {
            this.scope = scope;
            return this;
        }

        public Builder routingMethod(RoutingMethod routingMethod) {
            this.routingMethod = routingMethod;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder hosts(List<String> hosts) {
            this.hosts = List.copyOf(hosts);
            return this;
        }

        public Builder clusterId(String clusterId) {
            this.clusterId = clusterId;
            return this;
        }

        public Builder authMethod(AuthMethod authMethod) {
            this.authMethod = authMethod;
            return this;
        }

        public ApplicationClusterEndpoint build() {
            return new ApplicationClusterEndpoint(dnsName, scope, routingMethod, weight, hosts, clusterId, authMethod);
        }

    }

    public static class DnsName implements Comparable<DnsName> {

        private final String name;

        private DnsName(String name) {
            this.name = name;
        }

        public String value() {
            return name;
        }

        public static DnsName from(String name) {
            return new DnsName(name);
        }

        @Override
        public String toString() {
            return "DnsName{" +
                   "name='" + name + '\'' +
                   '}';
        }

        @Override
        public int compareTo(DnsName o) {
            return name.compareTo(o.name);
        }
    }
}
