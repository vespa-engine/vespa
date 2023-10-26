// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        private AuthMethod authMethod = AuthMethod.mtls; // TODO(mpolden): For compatibility with older config-models. Remove when < 8.221 is gone

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

        public Builder sharedRouting() {
            this.routingMethod = RoutingMethod.shared;
            return this;
        }

        public Builder sharedL4Routing() {
            this.routingMethod = RoutingMethod.sharedLayer4;
            return this;
        }

        public Builder routingMethod(RoutingMethod routingMethod) {
            this.routingMethod = routingMethod;
            return this;
        }

        public Builder weight(int weigth) {
            this.weight = weigth;
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

        private static final int MAX_LABEL_LENGTH = 63;

        private final String name;

        private DnsName(String name) {
            this.name = name;
        }

        public String value() {
            return name;
        }

        // TODO(mpolden): Remove when config-models < 8.232 are gone
        public static DnsName sharedL4NameFrom(SystemName systemName, ClusterSpec.Id cluster, ApplicationId applicationId, String suffix) {
            String name = dnsParts(systemName, cluster, applicationId)
                    .filter(Objects::nonNull) // remove null values that were "default"
                    .map(DnsName::sanitize)
                    .collect(Collectors.joining("."));
            return new DnsName(name + suffix);
        }

        public static DnsName from(String name) {
            return new DnsName(name);
        }

        private static Stream<String> dnsParts(SystemName systemName, ClusterSpec.Id cluster, ApplicationId applicationId) {
            return Stream.of(
                    nullIfDefault(cluster.value()),
                    systemPart(systemName),
                    nullIfDefault(applicationId.instance().value()),
                    applicationId.application().value(),
                    applicationId.tenant().value()
            );
        }

        /**
         * Remove any invalid characters from the hostnames
         */
        private static String sanitize(String id) {
            return shortenIfNeeded(id.toLowerCase()
                                           .replace('_', '-')
                                           .replaceAll("[^a-z0-9-]*", ""));
        }

        /**
         * Truncate the given string at the front so its length does not exceed 63 characters.
         */
        private static String shortenIfNeeded(String id) {
            return id.substring(Math.max(0, id.length() - MAX_LABEL_LENGTH));
        }

        private static String nullIfDefault(String string) {
            return Optional.of(string).filter(s -> !s.equals("default")).orElse(null);
        }

        private static String systemPart(SystemName systemName) {
            return "cd".equals(systemName.value()) ? systemName.value() : null;
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
