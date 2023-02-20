// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing;

import com.google.common.hash.Hashing;
import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.cloud.config.LbServicesConfig.Tenants.Applications.Endpoints.RoutingMethod.Enum.sharedLayer4;

/**
 * A routing table for a hosted Vespa zone. This holds the details necessary for the routing layer to route traffic to
 * deployments.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class RoutingTable {

    private static final String HOSTED_VESPA_TENANT_NAME = "hosted-vespa";

    private final SortedMap<Endpoint, Target> table;
    private final long generation;

    public RoutingTable(Map<Endpoint, Target> table, long generation) {
        this.table = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(table)));
        this.generation = generation;
    }

    public SortedMap<Endpoint, Target> asMap() {
        return table;
    }

    /** Returns the target for given dnsName, if any */
    public Optional<Target> targetOf(String dnsName, RoutingMethod routingMethod) {
        return Optional.ofNullable(table.get(new Endpoint(dnsName, routingMethod)));
    }

    /** Returns a copy of this containing only endpoints using given routing method */
    public RoutingTable routingMethod(RoutingMethod method) {
        Map<Endpoint, Target> copy = new TreeMap<>(table);
        copy.keySet().removeIf(endpoint -> !endpoint.routingMethod().equals(method));
        return new RoutingTable(copy, generation);
    }

    /** Returns the Vespa config generation this is based on */
    public long generation() {
        return generation;
    }

    @Override
    public String toString() {
        return table.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingTable that = (RoutingTable) o;
        return generation == that.generation && table.equals(that.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(table, generation);
    }

    public static RoutingTable from(LbServicesConfig config, long generation) {
        Map<Endpoint, Target> entries = new TreeMap<>();
        for (var tenants : config.tenants().entrySet()) {
            TenantName tenantName = TenantName.from(tenants.getKey());
            if (tenantName.value().equals(HOSTED_VESPA_TENANT_NAME)) continue;
            for (var applications : tenants.getValue().applications().entrySet()) {
                String[] parts = applications.getKey().split(":");
                if (parts.length != 4) throw new IllegalArgumentException("Invalid deployment ID '" + applications.getKey() + "'");

                ApplicationName application = ApplicationName.from(parts[0]);
                ZoneId zone = ZoneId.from(parts[1], parts[2]);
                InstanceName instance = InstanceName.from(parts[3]);

                for (var configuredEndpoint : applications.getValue().endpoints()) {
                    List<Real> reals = configuredEndpoint.hosts().stream()
                                                         .map(hostname -> new Real(hostname,
                                                                                   4443,
                                                                                   configuredEndpoint.weight(),
                                                                                   applications.getValue().activeRotation()))
                                                         .toList();
                    Endpoint endpoint = new Endpoint(configuredEndpoint.dnsName(), routingMethodFrom(configuredEndpoint));
                    ClusterSpec.Id cluster = ClusterSpec.Id.from(configuredEndpoint.clusterId());
                    Target target;
                    boolean applicationEndpoint = configuredEndpoint.scope() == LbServicesConfig.Tenants.Applications.Endpoints.Scope.Enum.application;
                    if (applicationEndpoint) {
                        target = Target.create(endpoint.dnsName, tenantName, application, cluster, zone, reals);
                    } else {
                        target = Target.create(ApplicationId.from(tenantName, application, instance), cluster, zone, reals);
                    }
                    entries.merge(endpoint, target, (oldValue, value) -> {
                        if (applicationEndpoint) {
                            List<Real> merged = new ArrayList<>(oldValue.reals());
                            merged.addAll(value.reals());
                            return value.withReals(merged);
                        }
                        return oldValue;
                    });
                }
            }
        }
        return new RoutingTable(entries, generation);
    }

    private static RoutingMethod routingMethodFrom(LbServicesConfig.Tenants.Applications.Endpoints endpoint) {
        if (endpoint.routingMethod() == sharedLayer4)
            return RoutingMethod.sharedLayer4;

        throw new IllegalArgumentException("Unhandled routing method: " + endpoint.routingMethod());
    }

    /** The target of an {@link Endpoint} */
    public static class Target implements Comparable<Target> {

        private final String id;

        private final TenantName tenant;
        private final ApplicationName application;
        private final Optional<InstanceName> instance;
        private final ZoneId zone;
        private final ClusterSpec.Id cluster;
        private final List<Real> reals;

        private Target(String id, TenantName tenant, ApplicationName application, Optional<InstanceName> instance,
                       ClusterSpec.Id cluster, ZoneId zone, List<Real> reals) {
            this.id = Objects.requireNonNull(id);
            this.tenant = Objects.requireNonNull(tenant);
            this.application = Objects.requireNonNull(application);
            this.instance = Objects.requireNonNull(instance);
            this.zone = Objects.requireNonNull(zone);
            this.cluster = Objects.requireNonNull(cluster);
            this.reals = Objects.requireNonNull(reals).stream().sorted().toList();
            for (int i = 0; i < reals.size(); i++) {
                for (int j = 0; j < i; j++) {
                    if (reals.get(i).equals(reals.get(j))) {
                        throw new IllegalArgumentException("Found duplicate real server: " + reals.get(i));
                    }
                }
            }
        }

        /** An unique identifier of this target (previously known as "upstreamName") */
        public String id() {
            return id;
        }

        /** Returns whether this is an application-level target, which points to reals of multiple instances */
        public boolean applicationLevel() {
            return instance.isEmpty();
        }

        public TenantName tenant() {
            return tenant;
        }

        public ApplicationName application() {
            return application;
        }

        public Optional<InstanceName> instance() {
            return instance;
        }

        public ZoneId zone() {
            return zone;
        }

        public ClusterSpec.Id cluster() {
            return cluster;
        }

        /** The real servers this points to */
        public List<Real> reals() {
            return reals;
        }

        /** Returns whether this is active and should receive traffic either through a global or application endpoint */
        public boolean active() {
            return reals.stream().anyMatch(Real::active);
        }

        /** Returns a copy of this containing given reals */
        public Target withReals(List<Real> reals) {
            return new Target(id, tenant, application, instance, cluster, zone, reals);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Target target = (Target) o;
            return id.equals(target.id) && tenant.equals(target.tenant) && application.equals(target.application) && instance.equals(target.instance) && zone.equals(target.zone) && cluster.equals(target.cluster) && reals.equals(target.reals);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, tenant, application, instance, zone, cluster, reals);
        }

        @Override
        public String toString() {
            return "target " + id + " -> " +
                   "tenant=" + tenant +
                   ",application=" + application +
                   ",instance=" + instance +
                   ",zone=" + zone +
                   ",cluster=" + cluster +
                   ",reals=" + reals;
        }

        @Override
        public int compareTo(RoutingTable.Target other) {
            return id.compareTo(other.id);
        }

        /** Create an instance-level tartget */
        public static Target create(ApplicationId instance, ClusterSpec.Id cluster, ZoneId zone, List<Real> reals) {
            return new Target(createId("", instance.tenant(), instance.application(), Optional.of(instance.instance()), cluster, zone),
                              instance.tenant(), instance.application(), Optional.of(instance.instance()), cluster, zone, reals);
        }

        /** Create an application-level target */
        public static Target create(String dnsName, TenantName tenant, ApplicationName application, ClusterSpec.Id cluster, ZoneId zone, List<Real> reals) {
            return new Target(createId(Objects.requireNonNull(dnsName), tenant, application, Optional.empty(), cluster, zone),
                              tenant, application, Optional.empty(), cluster, zone, reals);
        }

        /** Create an unique identifier for given dnsName and target */
        private static String createId(String dnsName, TenantName tenant, ApplicationName application,
                                      Optional<InstanceName> instance, ClusterSpec.Id cluster, ZoneId zone) {
            if (instance.isEmpty()) { // Application-scoped endpoint
                if (dnsName.isEmpty()) throw new IllegalArgumentException("dnsName must given for application-scoped endpoint");
                @SuppressWarnings("deprecation")
                String endpointHash = Hashing.sha1().hashString(dnsName, StandardCharsets.UTF_8).toString();
                return "application-" + endpointHash + "." +application.value() + "." + tenant.value();
            } else {
                if (!dnsName.isEmpty()) throw new IllegalArgumentException("dnsName must not be given for instance-level endpoint");
            }
            return Stream.of(nullIfDefault(cluster.value()),
                             nullIfDefault(instance.get().value()),
                             application.value(),
                             tenant.value(),
                             zone.region().value(),
                             zone.environment().value())
                         .filter(Objects::nonNull)
                         .map(Target::sanitize)
                         .collect(Collectors.joining("."));
        }

        private static String nullIfDefault(String value) { // Sublime sadness
            return "default".equals(value) ? null : value;
        }

        private static String sanitize(String id) {
            return id.toLowerCase()
                     .replace('_', '-')
                     .replaceAll("[^a-z0-9-]*", "");
        }

    }

    /** An externally visible endpoint */
    public static class Endpoint implements Comparable<Endpoint> {

        private static final Comparator<Endpoint> COMPARATOR = Comparator.comparing(Endpoint::dnsName)
                                                                         .thenComparing(Endpoint::routingMethod);

        private final String dnsName;
        private final RoutingMethod routingMethod;

        public Endpoint(String dnsName, RoutingMethod routingMethod) {
            this.dnsName = Objects.requireNonNull(dnsName);
            this.routingMethod = Objects.requireNonNull(routingMethod);
        }

        /** The DNS name of this endpoint. This does not contain a trailing dot */
        public String dnsName() {
            return dnsName;
        }

        public RoutingMethod routingMethod() {
            return routingMethod;
        }

        @Override
        public String toString() {
            return "endpoint " + dnsName + " (routing method: " + routingMethod + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Endpoint endpoint = (Endpoint) o;
            return dnsName.equals(endpoint.dnsName) && routingMethod == endpoint.routingMethod;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dnsName, routingMethod);
        }

        @Override
        public int compareTo(Endpoint o) {
            return COMPARATOR.compare(this, o);
        }

    }

    /** A real server, i.e. a node in a Vespa cluster */
    public static class Real implements Comparable<Real> {

        private static final Comparator<Real> COMPARATOR = Comparator.comparing(Real::hostname)
                                                                     .thenComparing(Real::port)
                                                                     .thenComparing(Real::weight)
                                                                     .thenComparing(Real::active);

        private final String hostname;
        private final int port;
        private final int weight;
        private final boolean active;

        public Real(String hostname, int port, int weight, boolean active) {
            this.hostname = Objects.requireNonNull(hostname);
            this.port = port;
            this.weight = weight;
            this.active = active;
        }

        /** The hostname of this */
        public String hostname() {
            return hostname;
        }

        /** The port this is listening on */
        public int port() {
            return port;
        }

        /** The relative weight of this. Controls the amount of traffic this should receive */
        public int weight() {
            return weight;
        }

        /** Returns whether this is active and should receive traffic */
        public boolean active() {
            return active;
        }

        @Override
        public String toString() {
            return "real server " + hostname + "[port=" + port + ",weight=" + weight + ",active=" + active + "]";
        }

        @Override
        public int compareTo(Real other) {
            return COMPARATOR.compare(this, other);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Real real = (Real) o;
            return port == real.port && hostname.equals(real.hostname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, port);
        }

    }

}
