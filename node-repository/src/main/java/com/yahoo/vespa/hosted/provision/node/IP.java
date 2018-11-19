// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents IP addresses owned by a node.
 *
 * @author mpolden
 */
public class IP {

    /** A pool of available IP addresses */
    public static class AddressPool {

        private final Node owner;
        private final Set<String> addresses;

        public AddressPool(Node owner, Set<String> addresses) {
            this.owner = Objects.requireNonNull(owner, "owner must be non-null");
            this.addresses = ImmutableSet.copyOf(requireAddresses(addresses));
        }

        /**
         * Find a free allocation in this pool
         *
         * @param nodes All nodes in the repository
         * @return An allocation from the pool, if any can be made
         */
        public Optional<Allocation> findAllocation(NodeList nodes) {
            Set<String> unusedAddresses = findUnused(nodes);
            Optional<String> ipv6Address = unusedAddresses.stream()
                                                          .filter(addr -> InetAddresses.forString(addr) instanceof Inet6Address)
                                                          .findFirst();
            Optional<String> ipv4Address = unusedAddresses.stream()
                                                          .filter(addr -> InetAddresses.forString(addr) instanceof Inet4Address)
                                                          .findFirst();

            // An allocation must contain one IPv6 address, but IPv4 is optional. All hosts have IPv6 addresses that
            // can be used by containers, while the availability of IPv4 addresses depends on the cloud provider.
            return ipv6Address.map(address -> new Allocation(address, ipv4Address));
        }

        /** Find all unused addresses in this pool
         *
         * @param nodes All nodes in the repository
         */
        public Set<String> findUnused(NodeList nodes) {
            Set<String> unusedAddresses = new LinkedHashSet<>(addresses);
            nodes.childrenOf(owner).asList().forEach(node -> unusedAddresses.removeAll(node.ipAddresses()));
            return Collections.unmodifiableSet(unusedAddresses);
        }

        public Set<String> asSet() {
            return addresses;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AddressPool that = (AddressPool) o;
            return Objects.equals(addresses, that.addresses);
        }

        @Override
        public int hashCode() {
            return Objects.hash(addresses);
        }

        private static Set<String> requireAddresses(Set<String> addresses) {
            Objects.requireNonNull(addresses, "ipAddressPool must be non-null");

            long ipv6AddrCount = addresses.stream()
                                          .filter(addr -> InetAddresses.forString(addr) instanceof Inet6Address)
                                          .count();
            if (ipv6AddrCount == addresses.size()) {
                return addresses; // IPv6-only pool is valid
            }

            long ipv4AddrCount = addresses.stream()
                                          .filter(addr -> InetAddresses.forString(addr) instanceof Inet4Address)
                                          .count();
            if (ipv4AddrCount == ipv6AddrCount) {
                return addresses;
            }

            throw new IllegalArgumentException(String.format("Dual-stacked IP address pool must have an " +
                                                             "equal number of addresses of each version " +
                                                             "[IPv6 address count = %d, IPv4 address count = %d]",
                                                             ipv6AddrCount, ipv4AddrCount));
        }

    }

    /** An IP address allocation from a pool */
    public static class Allocation {

        private final String ipv6Address;
        private final Optional<String> ipv4Address;

        private Allocation(String ipv6Address, Optional<String> ipv4Address) {
            this.ipv6Address = Objects.requireNonNull(ipv6Address, "ipv6Address must be non-null");
            this.ipv4Address = Objects.requireNonNull(ipv4Address, "ipv4Address must be non-null");
        }

        /**
         * Resolve and return the hostname of this allocation
         *
         * @param resolver DNS name resolver to use when resolving the hostname
         * @throws IllegalArgumentException if DNS is misconfigured
         * @return The hostname
         */
        public String resolveHostname(NameResolver resolver) {
            String hostname6 = resolver.getHostname(ipv6Address).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipv6Address));
            if (ipv4Address.isPresent()) {
                String hostname4 = resolver.getHostname(ipv4Address.get()).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipv4Address));
                if (!hostname6.equals(hostname4)) {
                    throw new IllegalArgumentException(String.format("Hostnames resolved from each IP address do not " +
                                                                     "point to the same hostname [%s -> %s, %s -> %s]",
                                                                     ipv6Address, hostname6, ipv4Address.get(), hostname4));
                }
            }
            return hostname6;
        }

        /** All IP addresses in this */
        public Set<String> addresses() {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            builder.add(ipv6Address);
            ipv4Address.ifPresent(builder::add);
            return builder.build();
        }

        @Override
        public String toString() {
            return "ipv6Address='" + ipv6Address + '\'' +
                   ", ipv4Address='" + ipv4Address.orElse("none") + '\'';
        }

    }

}
