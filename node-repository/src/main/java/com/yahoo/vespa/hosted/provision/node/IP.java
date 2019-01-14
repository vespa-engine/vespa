// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedBytes;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents IP addresses owned by a node.
 *
 * @author mpolden
 */
public class IP {

    /** Comparator for sorting IP addresses by their natural order */
    public static final Comparator<String> naturalOrder = (ip1, ip2) -> {
        byte[] address1 = InetAddresses.forString(ip1).getAddress();
        byte[] address2 = InetAddresses.forString(ip2).getAddress();

        // IPv4 always sorts before IPv6
        if (address1.length < address2.length) return -1;
        if (address1.length > address2.length) return 1;

        // Compare each octet
        for (int i = 0; i < address1.length; i++) {
            int b1 = UnsignedBytes.toInt(address1[i]);
            int b2 = UnsignedBytes.toInt(address2[i]);
            if (b1 == b2) {
                continue;
            }
            if (b1 < b2) {
                return -1;
            } else {
                return 1;
            }
        }
        return 0;
    };

    /** A pool of available IP addresses */
    public static class AddressPool {

        private final Node owner;
        private final Set<String> addresses;

        public AddressPool(Node owner, Set<String> addresses) {
            this.owner = Objects.requireNonNull(owner, "owner must be non-null");
            this.addresses = ImmutableSet.copyOf(Objects.requireNonNull(addresses, "addresses must be non-null"));
        }

        /**
         * Find a free allocation in this pool. Note that the allocation is not final until it is assigned to a node
         *
         * @param nodes All nodes in the repository
         * @return An allocation from the pool, if any can be made
         */
        public Optional<Allocation> findAllocation(NodeList nodes, NameResolver resolver) {
            Set<String> unusedAddresses = findUnused(nodes);
            Optional<Allocation> allocation = unusedAddresses.stream()
                                                             .filter(IP::isV6)
                                                             .findFirst()
                                                             .map(addr -> Allocation.resolveFrom(addr, resolver));
            allocation.flatMap(Allocation::ipv4Address).ifPresent(ipv4Address -> {
               if (!unusedAddresses.contains(ipv4Address)) {
                   throw new IllegalArgumentException("Allocation resolved " + ipv4Address + " from hostname " +
                                                      allocation.get().hostname +
                                                      ", but that address is not available in the address pool of " +
                                                      owner.hostname());
               }
            });
            return allocation;
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

    }

    /** An IP address allocation from a pool */
    public static class Allocation {

        private final String hostname;
        private final String ipv6Address;
        private final Optional<String> ipv4Address;

        private Allocation(String hostname, String ipv6Address, Optional<String> ipv4Address) {
            Objects.requireNonNull(ipv6Address, "ipv6Address must be non-null");
            if (!isV6(ipv6Address)) {
                throw new IllegalArgumentException("Invalid IPv6 address '" + ipv6Address + "'");
            }

            Objects.requireNonNull(ipv4Address, "ipv4Address must be non-null");
            if (ipv4Address.isPresent() && !isV4(ipv4Address.get())) {
                throw new IllegalArgumentException("Invalid IPv4 address '" + ipv4Address + "'");
            }
            this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
            this.ipv6Address = ipv6Address;
            this.ipv4Address = ipv4Address;
        }

        /**
         * Resolve the IP addresses and hostname of this allocation
         *
         * @param ipv6Address Unassigned IPv6 address
         * @param resolver DNS name resolver to use
         * @throws IllegalArgumentException if DNS is misconfigured
         * @return The allocation containing 1 IPv6 address and 1 IPv4 address (if hostname is dual-stack)
         */
        public static Allocation resolveFrom(String ipv6Address, NameResolver resolver) {
            String hostname6 = resolver.getHostname(ipv6Address).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipv6Address));
            List<String> ipv4Addresses = resolver.getAllByNameOrThrow(hostname6).stream()
                                                 .filter(IP::isV4)
                                                 .collect(Collectors.toList());
            if (ipv4Addresses.size() > 1) {
                throw new IllegalArgumentException("Hostname " + hostname6 + " resolved to more than 1 IPv4 address: " + ipv4Addresses);
            }
            Optional<String> ipv4Address = ipv4Addresses.stream().findFirst();
            ipv4Address.ifPresent(addr -> {
                String hostname4 = resolver.getHostname(addr).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipv4Address));
                if (!hostname6.equals(hostname4)) {
                    throw new IllegalArgumentException(String.format("Hostnames resolved from each IP address do not " +
                                                                     "point to the same hostname [%s -> %s, %s -> %s]",
                                                                     ipv6Address, hostname6, addr, hostname4));
                }
            });
            return new Allocation(hostname6, ipv6Address, ipv4Address);
        }

        /** Hostname pointing to the IP addresses in this */
        public String hostname() {
            return hostname;
        }

        /** IPv6 address in this allocation */
        public String ipv6Address() {
            return ipv6Address;
        }

        /** IPv4 address in this allocation */
        public Optional<String> ipv4Address() {
            return ipv4Address;
        }

        /** All IP addresses in this */
        public Set<String> addresses() {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            ipv4Address.ifPresent(builder::add);
            builder.add(ipv6Address);
            return builder.build();
        }

        @Override
        public String toString() {
            return "ipv6Address='" + ipv6Address + '\'' +
                   ", ipv4Address='" + ipv4Address.orElse("none") + '\'';
        }

    }

    /** Returns whether given string is an IPv4 address */
    public static boolean isV4(String ipAddress) {
        return InetAddresses.forString(ipAddress) instanceof Inet4Address;
    }

    /** Returns whether given string is an IPv6 address */
    public static boolean isV6(String ipAddress) {
        return InetAddresses.forString(ipAddress) instanceof Inet6Address;
    }

    /** Validates and returns the given set of IP addresses */
    public static Set<String> requireAddresses(Set<String> addresses) {
        String message = "A node must have at least one valid IP address";
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        try {
            addresses.forEach(InetAddresses::forString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message, e);
        }
        return addresses;
    }

    /** Validates and returns the given IP address pool */
    public static Set<String> requireAddressPool(Set<String> addresses) {
        long ipv6AddrCount = addresses.stream().filter(IP::isV6).count();
        if (ipv6AddrCount == addresses.size()) {
            return addresses; // IPv6-only pool is valid
        }

        long ipv4AddrCount = addresses.stream().filter(IP::isV4).count();
        if (ipv4AddrCount == ipv6AddrCount) {
            return addresses;
        }

        throw new IllegalArgumentException(String.format("Dual-stacked IP address list must have an " +
                                                         "equal number of addresses of each version " +
                                                         "[IPv6 address count = %d, IPv4 address count = %d]",
                                                         ipv6AddrCount, ipv4AddrCount));
    }

}
