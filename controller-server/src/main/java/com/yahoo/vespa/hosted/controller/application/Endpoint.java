// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents an application or instance endpoint in hosted Vespa.
 *
 * This encapsulates the logic for building URLs and DNS names for applications in all hosted Vespa systems.
 *
 * @author mpolden
 */
public class Endpoint {

    private static final String YAHOO_DNS_SUFFIX = ".vespa.yahooapis.com";
    private static final String OATH_DNS_SUFFIX = ".vespa.oath.cloud";
    private static final String PUBLIC_DNS_SUFFIX = ".vespa-app.cloud";
    private static final String PUBLIC_CD_DNS_SUFFIX = ".cd.vespa-app.cloud";

    private final EndpointId id;
    private final ClusterSpec.Id cluster;
    private final Optional<InstanceName> instance;
    private final URI url;
    private final List<Target> targets;
    private final Scope scope;
    private final boolean legacy;
    private final RoutingMethod routingMethod;
    private final boolean tls;

    private Endpoint(TenantAndApplicationId application, Optional<InstanceName> instanceName, EndpointId id,
                     ClusterSpec.Id cluster, URI url, List<Target> targets, Scope scope, Port port, boolean legacy,
                     RoutingMethod routingMethod) {
        Objects.requireNonNull(application, "application must be non-null");
        Objects.requireNonNull(instanceName, "instanceName must be non-null");
        Objects.requireNonNull(cluster, "cluster must be non-null");
        Objects.requireNonNull(targets, "deployment must be non-null");
        Objects.requireNonNull(scope, "scope must be non-null");
        Objects.requireNonNull(port, "port must be non-null");
        Objects.requireNonNull(routingMethod, "routingMethod must be non-null");
        if (scope.multiDeployment()) {
            if (id == null) throw new IllegalArgumentException("Endpoint ID must be set for multi-deployment endpoints");
        } else {
            if (scope == Scope.zone && id != null) throw new IllegalArgumentException("Endpoint ID cannot be set for " + scope + " endpoints");
            if (targets.size() != 1) throw new IllegalArgumentException("A single target must be given for " + scope + " endpoints");
        }
        if (scope != Scope.application && instanceName.isEmpty()) {
            throw new IllegalArgumentException("Instance must be set for scope " + scope);
        }
        for (var target : targets) {
            if (scope == Scope.application) {
                TenantAndApplicationId owner = TenantAndApplicationId.from(target.deployment().applicationId());
                if (!owner.equals(application)) {
                    throw new IllegalArgumentException(id + " has target owned by " + owner +
                                                       ", which does not match application of this endpoint: " +
                                                       application);
                }
            } else {
                ApplicationId owner = target.deployment.applicationId();
                ApplicationId instance = application.instance(instanceName.get());
                if (!owner.equals(instance)) {
                    throw new IllegalArgumentException(id + " has target owned by " + owner +
                                                       ", which does not match instance of this endpoint: " + instance);
                }
            }
        }
        this.id = id;
        this.cluster = cluster;
        this.instance = instanceName;
        this.url = url;
        this.targets = List.copyOf(targets);
        this.scope = scope;
        this.legacy = legacy;
        this.routingMethod = routingMethod;
        this.tls = port.tls;
    }

    private Endpoint(EndpointId id, ClusterSpec.Id cluster, TenantAndApplicationId application,
                     Optional<InstanceName> instance, List<Target> targets, Scope scope, SystemName system, Port port,
                     boolean legacy, RoutingMethod routingMethod) {
        this(application,
             instance,
             id,
             cluster,
             createUrl(endpointOrClusterAsString(id, cluster),
                       Objects.requireNonNull(application, "application must be non-null"),
                       Objects.requireNonNull(instance, "instance must be non-null"),
                       targets,
                       scope,
                       Objects.requireNonNull(system, "system must be non-null"),
                       Objects.requireNonNull(port, "port must be non-null"),
                       legacy,
                       routingMethod),
             targets, scope, port, legacy, routingMethod);
    }

    /**
     * Returns the name of this endpoint (the first component of the DNS name). This can be one of the following:
     *
     * - The wildcard character '*' (for wildcard endpoints, with any scope)
     * - The cluster ID (zone scope)
     * - The endpoint ID (global scope)
     */
    public String name() {
        return endpointOrClusterAsString(id, cluster);
    }

    /** Returns the cluster ID to which this routes traffic */
    public ClusterSpec.Id cluster() {
        return cluster;
    }

    /** The specific instance this endpoint points to, if any */
    public Optional<InstanceName> instance() {
        return instance;
    }

    /** Returns the URL used to access this */
    public URI url() {
        return url;
    }

    /** Returns the DNS name of this */
    public String dnsName() {
        // because getHost returns "null" for wildcard endpoints
        return url.getAuthority().replaceAll(":.*", "");
    }

    /** Returns the target(s) to which this routes traffic */
    public List<Target> targets() {
        return targets;
    }

    /** Returns the deployments(s) to which this routes traffic */
    public List<DeploymentId> deployments() {
        return targets.stream().map(Target::deployment).collect(Collectors.toUnmodifiableList());
    }

    /** Returns the scope of this */
    public Scope scope() {
        return scope;
    }

    /** Returns whether this is considered a legacy DNS name that is due for removal */
    public boolean legacy() {
        return legacy;
    }

    /** Returns the routing method used for this */
    public RoutingMethod routingMethod() {
        return routingMethod;
    }

    /** Returns whether this endpoint supports TLS connections */
    public boolean tls() {
        return tls;
    }

    /** Returns whether this requires a rotation to be reachable */
    public boolean requiresRotation() {
        return routingMethod.isShared() && scope == Scope.global;
    }

    /** Returns the upstream ID of given deployment. This *must* match what the routing layer generates */
    public String upstreamIdOf(DeploymentId deployment) {
        if (!routingMethod.isShared()) throw new IllegalArgumentException("Routing method " + routingMethod + " does not have upstream name");
        return upstreamIdOf(cluster.value(), deployment.applicationId(), deployment.zoneId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endpoint endpoint = (Endpoint) o;
        return url.equals(endpoint.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return Text.format("endpoint %s [scope=%s, legacy=%s, routingMethod=%s]", url, scope, legacy, routingMethod);
    }

    private static String endpointOrClusterAsString(EndpointId id, ClusterSpec.Id cluster) {
        return id == null ? cluster.value() : id.id();
    }

    private static URI createUrl(String name, TenantAndApplicationId application, Optional<InstanceName> instance,
                                 List<Target> targets, Scope scope, SystemName system, Port port, boolean legacy,
                                 RoutingMethod routingMethod) {
        String scheme = port.tls ? "https" : "http";
        String separator = separator(system, routingMethod, port.tls);
        String portPart = port.isDefault() ? "" : ":" + port.port;
        return URI.create(scheme + "://" +
                          sanitize(namePart(name, separator)) +
                          systemPart(system, separator) +
                          sanitize(instancePart(instance, separator)) +
                          sanitize(application.application().value()) +
                          separator +
                          sanitize(application.tenant().value()) +
                          "." +
                          scopePart(scope, targets, system, legacy) +
                          dnsSuffix(system, legacy) +
                          portPart +
                          "/");
    }

    private static String sanitize(String part) { // TODO: Reject reserved words
        return part.replace('_', '-');
    }

    private static String separator(SystemName system, RoutingMethod routingMethod, boolean tls) {
        if (!tls) return ".";
        if (routingMethod.isDirect()) return ".";
        if (system.isPublic()) return ".";
        return "--";
    }

    private static String namePart(String name, String separator) {
        if ("default".equals(name)) return "";
        return name + separator;
    }

    private static String scopePart(Scope scope, List<Target> targets, SystemName system, boolean legacy) {
        String scopeSymbol = scopeSymbol(scope, system);
        if (scope == Scope.global) return scopeSymbol;

        ZoneId zone = targets.get(0).deployment().zoneId();
        String region = zone.region().value();
        boolean skipEnvironment = zone.environment().isProduction() && (system.isPublic() || !legacy);
        String environment = skipEnvironment ? "" : "." + zone.environment().value();
        if (system.isPublic()) {
            return region + environment + "." + scopeSymbol;
        }
        return region + (scopeSymbol.isEmpty() ? "" : "-" + scopeSymbol) + environment;
    }

    private static String scopeSymbol(Scope scope, SystemName system) {
        if (system.isPublic()) {
            switch (scope) {
                case zone: return "z";
                case weighted: return "w";
                case global: return "g";
                case application: return "r";
            }
        }
        switch (scope) {
            case zone: return "";
            case weighted: return "w";
            case global: return "global";
            case application: return "r";
        }
        throw new IllegalArgumentException("No scope symbol defined for " + scope + " in " + system);
    }

    private static String instancePart(Optional<InstanceName> instance, String separator) {
        if (instance.isEmpty()) return "";
        if (instance.get().isDefault()) return ""; // Skip "default"
        return instance.get().value() + separator;
    }

    private static String systemPart(SystemName system, String separator) {
        if (!system.isCd()) return "";
        if (system.isPublic()) return "";
        return system.value() + separator;
    }

    /** Returns the DNS suffix used for endpoints in given system */
    private static String dnsSuffix(SystemName system, boolean legacy) {
        switch (system) {
            case cd:
            case main:
                if (legacy) return YAHOO_DNS_SUFFIX;
                return OATH_DNS_SUFFIX;
            case Public:
                if (legacy) throw new IllegalArgumentException("No legacy DNS suffix declared for system " + system);
                return PUBLIC_DNS_SUFFIX;
            case PublicCd:
                if (legacy) throw new IllegalArgumentException("No legacy DNS suffix declared for system " + system);
                return PUBLIC_CD_DNS_SUFFIX;
            default: throw new IllegalArgumentException("No DNS suffix declared for system " + system);
        }
    }

    /** Returns the DNS suffix used for internal names (i.e. names not exposed to tenants) in given system */
    public static String internalDnsSuffix(SystemName system) {
        String suffix = dnsSuffix(system, false);
        if (system.isPublic()) {
            // Certificate provider requires special approval for three-level DNS names, e.g. foo.vespa-app.cloud.
            // To avoid this in public we always add an extra level.
            return ".internal" + suffix;
        }
        return suffix;
    }

    private static String upstreamIdOf(String name, ApplicationId application, ZoneId zone) {
        return Stream.of(namePart(name, ""),
                         instancePart(Optional.of(application.instance()), ""),
                         application.application().value(),
                         application.tenant().value(),
                         zone.region().value(),
                         zone.environment().value())
                     .filter(Predicate.not(String::isEmpty))
                     .map(Endpoint::sanitizeUpstream)
                     .collect(Collectors.joining("."));
    }

    /** Remove any invalid characters from a upstream part */
    private static String sanitizeUpstream(String part) {
        return truncate(part.toLowerCase()
                            .replace('_', '-')
                            .replaceAll("[^a-z0-9-]*", ""));
    }

    /** Truncate the given part at the front so its length does not exceed 63 characters */
    private static String truncate(String part) {
        return part.substring(Math.max(0, part.length() - 63));
    }

    /** Returns the given region without availability zone */
    private static RegionName effectiveRegion(RegionName region) {
        if (region.value().isEmpty()) return region;
        String value = region.value();
        char lastChar = value.charAt(value.length() - 1);
        if (lastChar >= 'a' && lastChar <= 'z') { // Remove availability zone
            value = value.substring(0, value.length() - 1);
        }
        return RegionName.from(value);
    }

    private static ZoneId effectiveZone(ZoneId zone) {
        return ZoneId.from(zone.environment(), effectiveRegion(zone.region()));
    }

    /** An endpoint's scope */
    public enum Scope {

        /**
         * Endpoint points to a multiple instances of an application, in the same region.
         *
         * Traffic is routed across instances according to weights specified in deployment.xml
         */
        application,

        /** Endpoint points to one or more zones. Traffic is routed to the zone closest to the client */
        global,

        /**
         * Endpoint points to one more zones in the same geographical region. Traffic is routed evenly across zones.
         *
         * This is for internal use only. Endpoints with this scope are not exposed directly to tenants.
         */
        weighted,

        /** Endpoint points to a single zone */
        zone;

        /** Returns whether this scope may span multiple deployments */
        public boolean multiDeployment() {
            return this == application || this == global;
        }

    }

    /** Represents an endpoint's HTTP port */
    public static class Port {

        private static final Port TLS_DEFAULT = new Port(443, true);

        private final int port;
        private final boolean tls;

        private Port(int port, boolean tls) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
            }
            this.port = port;
            this.tls = tls;
        }

        private boolean isDefault() {
            return port == 80 || port == 443;
        }

        /** Returns the default HTTPS port */
        public static Port tls() {
            return TLS_DEFAULT;
        }

        /** Returns default port for the given routing method */
        public static Port fromRoutingMethod(RoutingMethod method) {
            if (method.isDirect()) return Port.tls();
            return Port.tls(4443);
        }

        /** Create a HTTPS port */
        public static Port tls(int port) {
            return new Port(port, true);
        }

        /** Create a HTTP port */
        public static Port plain(int port) {
            return new Port(port, false);
        }

    }

    /** Build an endpoint for given instance */
    public static EndpointBuilder of(ApplicationId instance) {
        return new EndpointBuilder(TenantAndApplicationId.from(instance), Optional.of(instance.instance()));
    }

    /** Build an endpoint for given application */
    public static EndpointBuilder of(TenantAndApplicationId application) {
        return new EndpointBuilder(application, Optional.empty());
    }

    /** Create an endpoint for given system application */
    public static Endpoint of(SystemApplication systemApplication, ZoneId zone, URI url) {
        if (!systemApplication.hasEndpoint()) throw new IllegalArgumentException(systemApplication + " has no endpoint");
        RoutingMethod routingMethod = RoutingMethod.exclusive;
        Port port = url.getPort() == -1 ? Port.tls() : Port.tls(url.getPort()); // System application endpoints are always TLS
        return new Endpoint(TenantAndApplicationId.from(systemApplication.id()),
                            Optional.of(systemApplication.id().instance()),
                            null,
                            ClusterSpec.Id.from("admin"),
                            url,
                            List.of(new Target(new DeploymentId(systemApplication.id(), zone))),
                            Scope.zone, port, false, routingMethod);
    }

    /** A target of an endpoint */
    public static class Target {

        private final DeploymentId deployment;
        private final int weight;

        private Target(DeploymentId deployment, int weight) {
            this.deployment = Objects.requireNonNull(deployment);
            this.weight = weight;
            if (weight < 0 || weight > 100) {
                throw new IllegalArgumentException("Endpoint target weight must be in range [0, 100], got " + weight);
            }
        }

        private Target(DeploymentId deployment) {
            this(deployment, 1);
        }

        /** Returns the deployment of this */
        public DeploymentId deployment() {
            return deployment;
        }

        /** Returns the assigned weight of this */
        public int weight() {
            return weight;
        }

    }

    public static class EndpointBuilder {

        private final TenantAndApplicationId application;
        private final Optional<InstanceName> instance;

        private Scope scope;
        private List<Target> targets;
        private ClusterSpec.Id cluster;
        private EndpointId endpointId;
        private Port port;
        private RoutingMethod routingMethod = RoutingMethod.shared;
        private boolean legacy = false;

        private EndpointBuilder(TenantAndApplicationId application, Optional<InstanceName> instance) {
            this.application = Objects.requireNonNull(application);
            this.instance = Objects.requireNonNull(instance);
        }

        /** Sets the deployment target for this */
        public EndpointBuilder target(ClusterSpec.Id cluster, DeploymentId deployment) {
            checkScope();
            this.cluster = cluster;
            this.scope = Scope.zone;
            this.targets = List.of(new Target(deployment));
            return this;
        }

        /** Sets the global target with given ID and pointing to the default cluster */
        public EndpointBuilder target(EndpointId endpointId) {
           return target(endpointId, ClusterSpec.Id.from("default"), List.of());
        }

        /** Sets the global target with given ID, deployments and cluster (as defined in deployments.xml) */
        public EndpointBuilder target(EndpointId endpointId, ClusterSpec.Id cluster, List<DeploymentId> deployments) {
            checkScope();
            this.endpointId = endpointId;
            this.cluster = cluster;
            this.targets = deployments.stream().map(Target::new).collect(Collectors.toUnmodifiableList());
            this.scope = Scope.global;
            return this;
        }

        /** Sets the global wildcard target for this */
        public EndpointBuilder wildcard() {
            return target(EndpointId.of("*"), ClusterSpec.Id.from("*"), List.of());
        }

        /** Sets the zone wildcard target for this */
        public EndpointBuilder wildcard(DeploymentId deployment) {
            return target(ClusterSpec.Id.from("*"), deployment);
        }

        /** Sets the application target with given ID, cluster, deployments and their weights */
        public EndpointBuilder targetApplication(EndpointId endpointId, ClusterSpec.Id cluster, Map<DeploymentId, Integer> deployments) {
            checkScope();
            this.endpointId = endpointId;
            this.cluster = cluster;
            this.targets = deployments.entrySet().stream()
                                      .map(kv -> new Target(kv.getKey(), kv.getValue()))
                                      .collect(Collectors.toUnmodifiableList());
            this.scope = Scope.application;
            return this;
        }

        /** Sets the region target for this, deduced from given zone */
        public EndpointBuilder targetRegion(ClusterSpec.Id cluster, ZoneId zone) {
            checkScope();
            this.cluster = cluster;
            this.scope = Scope.weighted;
            this.targets = List.of(new Target(new DeploymentId(application.instance(instance.get()), effectiveZone(zone))));
            return this;
        }

        /** Sets the port of this */
        public EndpointBuilder on(Port port) {
            this.port = port;
            return this;
        }

        /** Marks this as a legacy endpoint */
        public EndpointBuilder legacy() {
            this.legacy = true;
            return this;
        }

        /** Sets the routing method for this */
        public EndpointBuilder routingMethod(RoutingMethod method) {
            this.routingMethod = method;
            return this;
        }

        /** Sets the system that owns this */
        public Endpoint in(SystemName system) {
            if (system.isPublic() && routingMethod != RoutingMethod.exclusive) {
                throw new IllegalArgumentException("Public system only supports routing method " + RoutingMethod.exclusive);
            }
            if (routingMethod.isDirect() && !port.isDefault()) {
                throw new IllegalArgumentException("Routing method " + routingMethod + " can only use default port");
            }
            return new Endpoint(endpointId, cluster, application, instance, targets, scope, system, port, legacy, routingMethod);
        }

        private void checkScope() {
            if (scope != null) {
                throw new IllegalArgumentException("Cannot change endpoint scope. Already set to " + scope);
            }
        }

    }

}
