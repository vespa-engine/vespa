package com.yahoo.config.provision;

import ai.vespa.http.DomainName;

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
public class EndpointsChecker {

    public record Endpoint(ClusterSpec.Id clusterName,
                    DomainName dnsName,
                    Optional<InetAddress> ipAddress,
                    Optional<DomainName> canonicalName,
                    boolean isPublic) { }

    public record UnavailabilityCause(String message) { }

    public interface HostNameResolver { Optional<InetAddress> resolve(DomainName hostName); }

    public interface CNameResolver { Optional<DomainName> resolve(DomainName hostName); }

    private EndpointsChecker() { }

    public static Optional<UnavailabilityCause> endpointsAvailable(List<Endpoint> zoneEndpoints) {
        return endpointsAvailable(zoneEndpoints, EndpointsChecker::resolveHostName, EndpointsChecker::resolveCname);
    }

    public static Optional<UnavailabilityCause> endpointsAvailable(List<Endpoint> zoneEndpoints,
                                                                   HostNameResolver hostNameResolver,
                                                                   CNameResolver cNameResolver) {
        if (zoneEndpoints.isEmpty())
            return Optional.of(new UnavailabilityCause("Endpoints not yet ready."));

        for (Endpoint endpoint : zoneEndpoints) {
            Optional<InetAddress> resolvedIpAddress = hostNameResolver.resolve(endpoint.dnsName());
            if (resolvedIpAddress.isEmpty())
                return Optional.of(new UnavailabilityCause("DNS lookup yielded no IP address for '" + endpoint.dnsName() + "'."));

            if (resolvedIpAddress.equals(endpoint.ipAddress())) // We expect a certain IP address, and that's what we got, so we're good.
                continue;

            if (endpoint.ipAddress().isPresent()) // We expect a certain IP address, but that's not what we got.
                return Optional.of(new UnavailabilityCause("IP address of '" + endpoint.dnsName() + "' (" +
                                                           resolvedIpAddress.get().getHostAddress() + ") and load balancer " +
                                                           "' (" + endpoint.ipAddress().get().getHostAddress() + ") are not equal"));

            if (endpoint.canonicalName().isEmpty()) // We have no expected IP address, and no canonical name, so there's nothing more to check.
                continue;

            Optional<DomainName> cNameValue = cNameResolver.resolve(endpoint.dnsName());
            if (cNameValue.filter(endpoint.canonicalName().get()::equals).isEmpty()) {
                return Optional.of(new UnavailabilityCause("CNAME '" + endpoint.dnsName() + "' points at " +
                                                           cNameValue.map(name -> "'" + name + "'").orElse("nothing") +
                                                           " but should point at load balancer " +
                                                           endpoint.canonicalName().map(name -> "'" + name + "'").orElse("nothing")));
            }

            Optional<InetAddress> loadBalancerAddress = hostNameResolver.resolve(endpoint.canonicalName().get());
            if ( ! loadBalancerAddress.equals(resolvedIpAddress)) {
                return Optional.of(new UnavailabilityCause("IP address of CNAME '" + endpoint.dnsName() + "' (" +
                                                           resolvedIpAddress.get().getHostAddress() + ") and load balancer '" +
                                                           endpoint.canonicalName().get() + "' (" +
                                                           loadBalancerAddress.map(InetAddress::getHostAddress).orElse("empty") + ") are not equal"));
            }
        }
        return Optional.empty();
    }

    /** Returns the IP address of the given host name, if any. */
    static Optional<InetAddress> resolveHostName(DomainName hostname) {
        try {
            return Optional.of(InetAddress.getByName(hostname.value()));
        }
        catch (UnknownHostException ignored) {
            return Optional.empty();
        }
    }

    /** Returns the host name of the given CNAME, if any. */
    static Optional<DomainName> resolveCname(DomainName endpoint) {
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
