package com.yahoo.config.provision;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * @author jonmv
 */
public interface EndpointsChecker {

    record Endpoint(ClusterSpec.Id clusterName,
                    HttpURL url,
                    Optional<InetAddress> ipAddress,
                    Optional<DomainName> canonicalName,
                    boolean isPublic) { }

    /** Status sorted by increasing readiness. */
    enum Status { endpointsUnavailable, containersUnhealthy, available }

    record Availability(Status status, String message) { }

    interface HostNameResolver { Optional<InetAddress> resolve(DomainName hostName); }

    interface CNameResolver { Optional<DomainName> resolve(DomainName hostName); }

    interface ContainerHealthChecker { boolean healthy(Endpoint endpoint); }

    static EndpointsChecker of(ContainerHealthChecker containerHealthChecker) {
        return zoneEndpoints -> endpointsAvailable(zoneEndpoints, EndpointsChecker::resolveHostName, EndpointsChecker::resolveCname, containerHealthChecker);
    }

    static EndpointsChecker mock(HostNameResolver hostNameResolver, CNameResolver cNameResolver, ContainerHealthChecker containerHealthChecker) {
        return zoneEndpoints -> endpointsAvailable(zoneEndpoints, hostNameResolver, cNameResolver, containerHealthChecker);
    }

    Availability endpointsAvailable(List<Endpoint> zoneEndpoints);

    private static Availability endpointsAvailable(List<Endpoint> zoneEndpoints,
                                                   HostNameResolver hostNameResolver,
                                                   CNameResolver cNameResolver,
                                                   ContainerHealthChecker containerHealthChecker) {
        if (zoneEndpoints.isEmpty())
            return new Availability(Status.endpointsUnavailable, "Endpoints not yet ready.");

        for (Endpoint endpoint : zoneEndpoints) {
            Optional<InetAddress> resolvedIpAddress = hostNameResolver.resolve(endpoint.url().domain());
            if (resolvedIpAddress.isEmpty())
                return new Availability(Status.endpointsUnavailable, "DNS lookup yielded no IP address for '" + endpoint.url().domain() + "'.");

            if (resolvedIpAddress.equals(endpoint.ipAddress())) // We expect a certain IP address, and that's what we got, so we're good.
                continue;

            if (endpoint.ipAddress().isPresent()) // We expect a certain IP address, but that's not what we got.
                return new Availability(Status.endpointsUnavailable,
                                        "IP address of '" + endpoint.url().domain() + "' (" +
                                        resolvedIpAddress.get().getHostAddress() + ") and load balancer " +
                                        "' (" + endpoint.ipAddress().get().getHostAddress() + ") are not equal");

            if (endpoint.canonicalName().isEmpty()) // We have no expected IP address, and no canonical name, so there's nothing more to check.
                continue;

            Optional<DomainName> cNameValue = cNameResolver.resolve(endpoint.url().domain());
            if (cNameValue.filter(endpoint.canonicalName().get()::equals).isEmpty()) {
                return new Availability(Status.endpointsUnavailable,
                                        "CNAME '" + endpoint.url().domain() + "' points at " +
                                        cNameValue.map(name -> "'" + name + "'").orElse("nothing") +
                                        " but should point at load balancer " +
                                        endpoint.canonicalName().map(name -> "'" + name + "'").orElse("nothing"));
            }

            Optional<InetAddress> loadBalancerAddress = hostNameResolver.resolve(endpoint.canonicalName().get());
            if ( ! loadBalancerAddress.equals(resolvedIpAddress)) {
                return new Availability(Status.endpointsUnavailable,
                                        "IP address of CNAME '" + endpoint.url().domain() + "' (" +
                                        resolvedIpAddress.get().getHostAddress() + ") and load balancer '" +
                                        endpoint.canonicalName().get() + "' (" +
                                        loadBalancerAddress.map(InetAddress::getHostAddress).orElse("empty") + ") are not equal");
            }
        }

        for (Endpoint endpoint : zoneEndpoints)
            if ( ! containerHealthChecker.healthy(endpoint))
                return new Availability(Status.containersUnhealthy, "Failed to get enough healthy responses from " + endpoint.url());

        return new Availability(Status.available, "Endpoints are ready");
    }

    /** Returns the IP address of the given host name, if any. */
    private static Optional<InetAddress> resolveHostName(DomainName hostname) {
        try {
            return Optional.of(InetAddress.getByName(hostname.value()));
        }
        catch (UnknownHostException ignored) {
            return Optional.empty();
        }
    }

    /** Returns the host name of the given CNAME, if any. */
    private static Optional<DomainName> resolveCname(DomainName endpoint) {
        try {
            InitialDirContext ctx = new InitialDirContext();
            try {
                Attributes attrs = ctx.getAttributes("dns:/" + endpoint.value(), new String[]{ "CNAME" });
                for (Attribute attribute : Collections.list(attrs.getAll())) {
                    Enumeration<?> vals = attribute.getAll();
                    if (vals.hasMoreElements()) {
                        String hostname = vals.nextElement().toString();
                        return Optional.of(hostname.substring(0, hostname.length() - 1)).map(DomainName::of);
                    }
                }
            }
            finally {
                ctx.close();
            }
        }
        catch (NamingException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

}
