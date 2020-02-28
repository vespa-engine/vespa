// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.net.URI;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents an application's endpoint. The endpoint scope can either be global or a specific zone. This is visible to
 * the tenant and is used by the tenant when accessing deployments.
 *
 * @author mpolden
 */
public class Endpoint {

    private static final String YAHOO_DNS_SUFFIX = ".vespa.yahooapis.com";
    private static final String OATH_DNS_SUFFIX = ".vespa.oath.cloud";
    private static final String PUBLIC_DNS_SUFFIX = ".public.vespa.oath.cloud";
    private static final String PUBLIC_CD_DNS_SUFFIX = ".public-cd.vespa.oath.cloud";

    private final String name;
    private final URI url;
    private final Scope scope;
    private final boolean legacy;
    private final RoutingMethod routingMethod;
    private final boolean tls;
    private final boolean wildcard;

    private Endpoint(String name, ApplicationId application, ZoneId zone, SystemName system, Port port, boolean legacy,
                     RoutingMethod routingMethod, boolean wildcard) {
        Objects.requireNonNull(name, "name must be non-null");
        Objects.requireNonNull(application, "application must be non-null");
        Objects.requireNonNull(system, "system must be non-null");
        Objects.requireNonNull(port, "port must be non-null");
        Objects.requireNonNull(routingMethod, "routingMethod must be non-null");
        this.name = name;
        this.url = createUrl(name, application, zone, system, port, legacy, routingMethod);
        this.scope = zone == null ? Scope.global : Scope.zone;
        this.legacy = legacy;
        this.routingMethod = routingMethod;
        this.tls = port.tls;
        this.wildcard = wildcard;
    }

    /**
     * Returns the name of this endpoint (the first component of the DNS name). Depending on the endpoint type, this
     * can be one of the following:
     * - A wildcard (any scope)
     * - A cluster name (only zone scope)
     * - An endpoint ID (only global scope)
     */
    public String name() {
        return name;
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

    /** Returns the scope of this */
    public Scope scope() {
        return scope;
    }

    /** Returns whether this is considered a legacy DNS name that is due for removal */
    public boolean legacy() {
        return legacy;
    }

    /** Returns the routing used for this */
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

    /** Returns whether this is a wildcard endpoint (used only in certificates) */
    public boolean wildcard() {
        return wildcard;
    }

    /** Returns the upstream ID of given deployment. This *must* match what the routing layer generates */
    public String upstreamIdOf(DeploymentId deployment) {
        if (scope != Scope.global) throw new IllegalArgumentException("Scope " + scope + " does not have upstream name");
        if (!routingMethod.isShared()) throw new IllegalArgumentException("Routing method " + routingMethod + " does not have upstream name");
        return upstreamIdOf(name, deployment.applicationId(), deployment.zoneId());
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
        return String.format("endpoint %s [scope=%s, legacy=%s, routingMethod=%s]", url, scope, legacy, routingMethod);
    }

    /** Returns the DNS suffix used for endpoints in given system */
    public static String dnsSuffix(SystemName system) {
        return dnsSuffix(system, false);
    }

    private static URI createUrl(String name, ApplicationId application, ZoneId zone, SystemName system,
                                 Port port, boolean legacy, RoutingMethod routingMethod) {
        String scheme = port.tls ? "https" : "http";
        String separator = separator(system, routingMethod, port.tls);
        String portPart = port.isDefault() ? "" : ":" + port.port;
        return URI.create(scheme + "://" +
                          sanitize(namePart(name, separator)) +
                          systemPart(system, separator) +
                          sanitize(instancePart(application, separator)) +
                          sanitize(application.application().value()) +
                          separator +
                          sanitize(application.tenant().value()) +
                          "." +
                          scopePart(zone, legacy) +
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

    private static String scopePart(ZoneId zone, boolean legacy) {
        if (zone == null) return "global";
        if (!legacy && zone.environment().isProduction()) return zone.region().value(); // Skip prod environment for non-legacy endpoints
        return zone.region().value() + "." + zone.environment().value();
    }

    private static String instancePart(ApplicationId application, String separator) {
        if (application.instance().isDefault()) return ""; // Skip "default"
        return application.instance().value() + separator;
    }

    private static String systemPart(SystemName system, String separator) {
        if (!system.isCd()) return "";
        return system.value() + separator;
    }

    private static String dnsSuffix(SystemName system, boolean legacy) {
        switch (system) {
            case cd:
            case main:
                if (legacy) return YAHOO_DNS_SUFFIX;
                return OATH_DNS_SUFFIX;
            case Public:
                return PUBLIC_DNS_SUFFIX;
            case PublicCd:
                return PUBLIC_CD_DNS_SUFFIX;
            default: throw new IllegalArgumentException("No DNS suffix declared for system " + system);
        }
    }

    private static String upstreamIdOf(String name, ApplicationId application, ZoneId zone) {
        return Stream.of(namePart(name, ""),
                         instancePart(application, ""),
                         application.tenant().value(),
                         application.application().value(),
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

    /** An endpoint's scope */
    public enum Scope {

        /** Endpoint points to all zones */
        global,

        /** Endpoint points to a single zone */
        zone,

    }

    /** Represents an endpoint's HTTP port */
    public static class Port {

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
            return new Port(443, true);
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

    /** Build an endpoint for given application */
    public static EndpointBuilder of(ApplicationId application) {
        return new EndpointBuilder(application);
    }

    public static class EndpointBuilder {

        private final ApplicationId application;

        private ZoneId zone;
        private ClusterSpec.Id cluster;
        private EndpointId endpointId;
        private Port port;
        private RoutingMethod routingMethod = RoutingMethod.shared;
        private boolean legacy = false;
        private boolean wildcard = false;

        private EndpointBuilder(ApplicationId application) {
            this.application = application;
        }

        /** Sets the cluster target for this */
        public EndpointBuilder target(ClusterSpec.Id cluster, ZoneId zone) {
            if (endpointId != null || wildcard) {
                throw new IllegalArgumentException("Cannot set multiple target types");
            }
            this.cluster = cluster;
            this.zone = zone;
            return this;
        }

        /** Sets the endpoint target ID for this (as defined in deployments.xml) */
        public EndpointBuilder named(EndpointId endpointId) {
            if (cluster != null || wildcard) {
                throw new IllegalArgumentException("Cannot set multiple target types");
            }
            this.endpointId = endpointId;
            return this;
        }

        /** Sets the global wildcard target for this */
        public EndpointBuilder wildcard() {
            if (endpointId != null || cluster != null) {
                throw new IllegalArgumentException("Cannot set multiple target types");
            }
            this.wildcard = true;
            return this;
        }

        /** Sets the zone wildcard target for this */
        public EndpointBuilder wildcard(ZoneId zone) {
            if(endpointId != null || cluster != null) {
                throw new IllegalArgumentException("Cannot set multiple target types");
            }
            this.zone = zone;
            this.wildcard = true;
            return this;
        }

        /** Sets the port of this  */
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
            String name;
            if (wildcard) {
                name = "*";
            } else if (endpointId != null) {
                name = endpointId.id();
            } else if (cluster != null) {
                name = cluster.value();
            } else {
                throw new IllegalArgumentException("Must set either cluster, rotation or wildcard target");
            }
            if (system.isPublic() && routingMethod != RoutingMethod.exclusive) {
                throw new IllegalArgumentException("Public system only supports routing method " + RoutingMethod.exclusive);
            }
            if (routingMethod.isDirect() && !port.isDefault()) {
                throw new IllegalArgumentException("Routing method " + routingMethod + " can only use default port");
            }
            return new Endpoint(name, application, zone, system, port, legacy, routingMethod, wildcard);
        }

    }

}
