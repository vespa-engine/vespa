// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author jonmv
 */
public interface EndpointsChecker {

    record Endpoint(ApplicationId applicationId,
                    ClusterSpec.Id clusterName,
                    HttpURL url,
                    Optional<InetAddress> ipAddress,
                    Optional<DomainName> canonicalName,
                    boolean isPublic,
                    CloudAccount account) { }

    /** Status sorted by increasing readiness. */
    enum Status { endpointsUnavailable, containersUnhealthy, available }

    record Availability(Status status, String message) {
        public static final Availability ready = new Availability(Status.available, "Endpoints are ready.");
    }

    interface NameResolver { List<String> resolve(NameType nameType, DomainName name); }

    interface HealthChecker { Availability healthy(Endpoint endpoint); }

    interface HealthCheckerProvider {
        default HealthChecker getHealthChecker() { return __ -> Availability.ready; }
    }

    static EndpointsChecker of(HealthChecker healthChecker) {
        return zoneEndpoints -> endpointsAvailable(zoneEndpoints, EndpointsChecker::resolveAll, healthChecker);
    }

    static EndpointsChecker mock(NameResolver resolver, HealthChecker healthChecker) {
        return zoneEndpoints -> endpointsAvailable(zoneEndpoints, resolver, healthChecker);
    }

    Availability endpointsAvailable(List<Endpoint> zoneEndpoints);

    private static Availability endpointsAvailable(List<Endpoint> zoneEndpoints,
                                                   NameResolver nameResolver,
                                                   HealthChecker healthChecker) {
        if (zoneEndpoints.isEmpty())
            return new Availability(Status.endpointsUnavailable, "Endpoints not yet ready.");

        for (Endpoint endpoint : zoneEndpoints) {
            Set<String> resolvedIpAddresses = resolveIpAddresses(endpoint.url().domain(), nameResolver);
            if (resolvedIpAddresses.isEmpty())
                return new Availability(Status.endpointsUnavailable, "DNS lookup yielded no IP address for '" + endpoint.url().domain() + "'.");

            if (endpoint.ipAddress().isPresent()) {
                if (resolvedIpAddresses.contains(endpoint.ipAddress().get().getHostAddress())) {
                    continue; // Resolved addresses contain the expected endpoint IP address
                }
                return new Availability(Status.endpointsUnavailable,
                                        "IP address(es) of '" + endpoint.url().domain() + "' (" +
                                        resolvedIpAddresses + ") do not include load balancer IP " +
                                        "' (" + endpoint.ipAddress().get().getHostAddress() + ")");
            }

            if (endpoint.canonicalName().isEmpty()) // We have no expected IP address, and no canonical name, so there's nothing more to check.
                continue;

            List<String> cnameAnswers = nameResolver.resolve(NameType.CNAME, endpoint.url().domain());
            if (!cnameAnswers.contains(endpoint.canonicalName().get().value())) {
                return new Availability(Status.endpointsUnavailable,
                                        "CNAME '" + endpoint.url().domain() + "' points at " +
                                        cnameAnswers +
                                        " but should point at load balancer " +
                                        endpoint.canonicalName().map(name -> "'" + name + "'").orElse("nothing"));
            }

            Set<String> loadBalancerAddresses = resolveIpAddresses(endpoint.canonicalName().get(), nameResolver);
            if ( ! loadBalancerAddresses.equals(resolvedIpAddresses)) {
                return new Availability(Status.endpointsUnavailable,
                                        "IP address(es) of CNAME '" + endpoint.url().domain() + "' (" +
                                        resolvedIpAddresses + ") and load balancer '" +
                                        endpoint.canonicalName().get() + "' (" + loadBalancerAddresses + ") are not equal");
            }
        }

        Availability availability = Availability.ready;
        for (Endpoint endpoint : zoneEndpoints) {
            Availability candidate = healthChecker.healthy(endpoint);
            if (candidate.status.compareTo(availability.status) < 0)
                availability = candidate;
        }
        return availability;
    }

    private static Set<String> resolveIpAddresses(DomainName name, NameResolver nameResolver) {
        Set<String> answers = new HashSet<>();
        answers.addAll(nameResolver.resolve(NameType.A, name));
        answers.addAll(nameResolver.resolve(NameType.AAAA, name));
        return answers;
    }

    enum NameType {
        A, AAAA, CNAME
    }

    /** Returns all answers for given type and name. An empty list is returned if name does not exist (NXDOMAIN) */
    private static List<String> resolveAll(NameType type, DomainName name) {
        try {
            DirContext ctx = new InitialDirContext();
            try {
                String entryType = type.name();
                Attributes attributes = ctx.getAttributes("dns:/" + name, new String[]{entryType});
                Attribute attribute = attributes.get(entryType);
                if (attribute == null) {
                    return List.of();
                }
                List<String> results = new ArrayList<>();
                attribute.getAll().asIterator().forEachRemaining(value -> {
                    String answer = Objects.toString(value);
                    answer = answer.endsWith(".") ? answer.substring(0, answer.length() - 1) : answer; // Trim trailing dot
                    results.add(answer);
                });
                return Collections.unmodifiableList(results);
            } finally {
                ctx.close();
            }
        } catch (NameNotFoundException ignored) {
            return List.of();
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

}
