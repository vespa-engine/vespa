// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ZoneEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a load balancer instance. This contains the fields that are owned by a {@link LoadBalancerService} and is
 * immutable.
 *
 * @author mpolden
 */
public class LoadBalancerInstance {

    private final Optional<DomainName> hostname;
    private final Optional<String> ipAddress;
    private final Optional<DnsZone> dnsZone;
    private final Set<Integer> ports;
    private final Set<String> networks;
    private final Set<Real> reals;
    private final ZoneEndpoint settings;
    private final List<PrivateServiceId> serviceIds;
    private final CloudAccount cloudAccount;

    public LoadBalancerInstance(Optional<DomainName> hostname, Optional<String> ipAddress,
                                Optional<DnsZone> dnsZone, Set<Integer> ports, Set<String> networks, Set<Real> reals,
                                ZoneEndpoint settings, List<PrivateServiceId> serviceIds, CloudAccount cloudAccount) {
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.ipAddress = Objects.requireNonNull(ipAddress, "ip must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.ports = ImmutableSortedSet.copyOf(requirePorts(ports));
        this.networks = ImmutableSortedSet.copyOf(Objects.requireNonNull(networks, "networks must be non-null"));
        this.reals = ImmutableSortedSet.copyOf(Objects.requireNonNull(reals, "targets must be non-null"));
        this.settings = Objects.requireNonNull(settings, "settings must be non-null");
        this.serviceIds = List.copyOf(Objects.requireNonNull(serviceIds, "private service id must be non-null"));
        this.cloudAccount = Objects.requireNonNull(cloudAccount, "cloudAccount must be non-null");

        if (hostname.isEmpty() == ipAddress.isEmpty()) {
            throw new IllegalArgumentException("Exactly 1 of hostname=%s and ipAddress=%s must be set".formatted(
                    hostname.map(DomainName::value).orElse("<empty>"), ipAddress.orElse("<empty>")));
        }
    }

    /** Fully-qualified domain name of this load balancer. This hostname can be used for query and feed */
    public Optional<DomainName> hostname() {
        return hostname;
    }

    /** IP address of this (public) load balancer */
    public Optional<String> ipAddress() {
        return ipAddress;
    }

    /** ID of the DNS zone associated with this */
    public Optional<DnsZone> dnsZone() {
        return dnsZone;
    }

    /** Listening port(s) of this load balancer */
    public Set<Integer> ports() {
        return ports;
    }

    /** Networks (CIDR blocks) of this load balancer */
    public Set<String> networks() {
        return networks;
    }

    /** Real servers behind this load balancer */
    public Set<Real> reals() {
        return reals;
    }

    /** Static user-configured settings of this load balancer */
    public ZoneEndpoint settings() {
        return settings;
    }

    /** ID of any private endpoint service configured for this load balancer. */
    // TODO jonmv: remove
    public Optional<PrivateServiceId> serviceId() {
        return serviceIds.isEmpty() ? Optional.empty() : Optional.of(serviceIds.get(serviceIds.size() - 1));
    }

    public List<PrivateServiceId> serviceIds() {
        return serviceIds;
    }

    /** Cloud account of this load balancer */
    public CloudAccount cloudAccount() {
        return cloudAccount;
    }

    private static Set<Integer> requirePorts(Set<Integer> ports) {
        Objects.requireNonNull(ports, "ports must be non-null");
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("ports must be non-empty");
        }
        if (!ports.stream().allMatch(port -> port >= 1 && port <= 65535)) {
            throw new IllegalArgumentException("all ports must be >= 1 and <= 65535");
        }
        return ports;
    }

    /** Updates this with new data, from a reconfiguration. */
    public LoadBalancerInstance with(Set<Real> reals, ZoneEndpoint settings, Optional<PrivateServiceId> serviceId) {
        List<PrivateServiceId> ids = new ArrayList<>(serviceIds);
        serviceId.filter(id -> ! ids.contains(id)).ifPresent(ids::add);
        return new LoadBalancerInstance(hostname, ipAddress, dnsZone, ports, networks,
                                        reals, settings, ids,
                                        cloudAccount);
    }

    /** Prepends the given service IDs, possibly changing the order of those we have in this. */
    public LoadBalancerInstance withServiceIds(List<PrivateServiceId> serviceIds) {
        List<PrivateServiceId> ids = new ArrayList<>(serviceIds);
        for (PrivateServiceId id : this.serviceIds) if ( ! ids.contains(id)) ids.add(id);
        return new LoadBalancerInstance(hostname, ipAddress, dnsZone, ports, networks, reals, settings, ids, cloudAccount);
    }

}
