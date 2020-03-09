// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedBytes;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeType.confighost;
import static com.yahoo.config.provision.NodeType.controllerhost;
import static com.yahoo.config.provision.NodeType.proxyhost;

/**
 * This handles IP address configuration and allocation.
 *
 * @author mpolden
 */
public class IP {

    /** Comparator for sorting IP addresses by their natural order */
    public static final Comparator<String> NATURAL_ORDER = (ip1, ip2) -> {
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

    /** IP configuration of a node */
    public static class Config {

        public static final Config EMPTY = new Config(Set.of(), Set.of());

        private final Set<String> primary;
        private final Pool pool;

        /** DO NOT USE in non-test code. Public for serialization purposes. */
        public Config(Set<String> primary, Set<String> pool) {
            this.primary = ImmutableSet.copyOf(Objects.requireNonNull(primary, "primary must be non-null"));
            this.pool = Pool.of(Objects.requireNonNull(pool, "pool must be non-null"));
        }

        /** The primary addresses of this. These addresses are used when communicating with the node itself */
        public Set<String> primary() {
            return primary;
        }

        /** Returns the IP address pool available on a node */
        public Pool pool() {
            return pool;
        }

        /** Returns a copy of this with pool set to given value */
        public Config with(Pool pool) {
            return new Config(primary, pool.asSet());
        }

        /** Returns a copy of this with pool set to given value */
        public Config with(Set<String> primary) {
            return new Config(require(primary), pool.asSet());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Config config = (Config) o;
            return primary.equals(config.primary) &&
                   pool.equals(config.pool);
        }

        @Override
        public int hashCode() {
            return Objects.hash(primary, pool);
        }

        @Override
        public String toString() {
            return String.format("ip config primary=%s pool=%s", primary, pool.asSet());
        }

        /** Validates and returns the given addresses */
        public static Set<String> require(Set<String> addresses) {
            try {
                addresses.forEach(InetAddresses::forString);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Found one or more invalid addresses in " + addresses, e);
            }
            return addresses;
        }

        /**
         * Verify IP config of given nodes
         *
         * @throws IllegalArgumentException if there are IP conflicts with existing nodes
         */
        public static List<Node> verify(List<Node> nodes, LockedNodeList allNodes) {
            for (var node : nodes) {
                for (var other : allNodes) {
                    if (node.equals(other)) continue;
                    if (canAssignIpOf(other, node)) continue;

                    var addresses = new HashSet<>(node.ipConfig().primary());
                    var otherAddresses = new HashSet<>(other.ipConfig().primary());
                    if (node.type().isHost()) { // Addresses of a host can never overlap with any other nodes
                        addresses.addAll(node.ipConfig().pool().asSet());
                        otherAddresses.addAll(other.ipConfig().pool().asSet());
                    }
                    otherAddresses.retainAll(addresses);
                    if (!otherAddresses.isEmpty())
                        throw new IllegalArgumentException("Cannot assign " + addresses + " to " + node.hostname() +
                                                           ": " + otherAddresses + " already assigned to " +
                                                           other.hostname());
                }
            }
            return nodes;
        }

        /** Returns whether IP address of existing node can be assigned to node */
        private static boolean canAssignIpOf(Node existingNode, Node node) {
            if (node.parentHostname().isPresent() == existingNode.parentHostname().isPresent()) return false; // Not a parent-child node
            if (node.parentHostname().isEmpty()) return canAssignIpOf(node, existingNode);
            if (!node.parentHostname().get().equals(existingNode.hostname())) return false; // Wrong host
            switch (node.type()) {
                case proxy: return existingNode.type() == proxyhost;
                case config: return existingNode.type() == confighost;
                case controller: return existingNode.type() == controllerhost;
            }
            return false;
        }

        public static Node verify(Node node, LockedNodeList allNodes) {
            return verify(List.of(node), allNodes).get(0);
        }

    }

    /** A list of IP addresses and their protocol */
    public static class Addresses {

        private final Set<String> addresses;
        private final Protocol protocol;

        private Addresses(Set<String> addresses, Protocol protocol) {
            this.addresses = ImmutableSet.copyOf(Objects.requireNonNull(addresses, "addresses must be non-null"));
            this.protocol = Objects.requireNonNull(protocol, "type must be non-null");
        }

        public Set<String> asSet() {
            return addresses;
        }

        /** The protocol of addresses in this */
        public Protocol protocol() {
            return protocol;
        }

        /** Create addresses of the given set */
        private static Addresses of(Set<String> addresses) {
            long ipv6AddrCount = addresses.stream().filter(IP::isV6).count();
            if (ipv6AddrCount == addresses.size()) { // IPv6-only
                return new Addresses(addresses, Protocol.ipv6);
            }

            long ipv4AddrCount = addresses.stream().filter(IP::isV4).count();
            if (ipv4AddrCount == addresses.size()) { // IPv4-only
                return new Addresses(addresses, Protocol.ipv4);
            }

            // If we're dual-stacked, we must must have an equal number of addresses of each protocol.
            if (ipv4AddrCount == ipv6AddrCount) {
                return new Addresses(addresses, Protocol.dualStack);
            }

            throw new IllegalArgumentException(String.format("Dual-stacked IP address list must have an " +
                                                             "equal number of addresses of each version " +
                                                             "[IPv6 address count = %d, IPv4 address count = %d]",
                                                             ipv6AddrCount, ipv4AddrCount));
        }

        public enum Protocol {
            dualStack,
            ipv4,
            ipv6
        }

    }

    /**
     * A pool of IP addresses from which an allocation can be made.
     *
     * Addresses in this are available for use by Docker containers
     */
    public static class Pool {

        private final Addresses addresses;

        private Pool(Addresses addresses) {
            this.addresses = Objects.requireNonNull(addresses, "addresses must be non-null");
        }

        /**
         * Find a free allocation in this pool. Note that the allocation is not final until it is assigned to a node
         *
         * @param nodes A locked list of all nodes in the repository
         * @return An allocation from the pool, if any can be made
         */
        public Optional<Allocation> findAllocation(LockedNodeList nodes, NameResolver resolver) {
            var unusedAddresses = findUnused(nodes);
            var allocation = unusedAddresses.stream()
                                            .filter(IP::isV6)
                                            .findFirst()
                                            .map(addr -> Allocation.ofIpv6(addr, resolver));
            allocation.flatMap(Allocation::secondary).ifPresent(ipv4Address -> {
                if (!unusedAddresses.contains(ipv4Address)) {
                    throw new IllegalArgumentException("Allocation resolved " + ipv4Address + " from hostname " +
                                                       allocation.get().hostname +
                                                       ", but that address is not owned by this node");
                }
            });
            return allocation;
        }

        /**
         * Finds all unused addresses in this pool
         *
         * @param nodes Locked list of all nodes in the repository
         */
        public Set<String> findUnused(NodeList nodes) {
            var unusedAddresses = new LinkedHashSet<>(asSet());
            nodes.filter(node -> node.ipConfig().primary().stream().anyMatch(ip -> asSet().contains(ip)))
                 .forEach(node -> unusedAddresses.removeAll(node.ipConfig().primary()));
            return Collections.unmodifiableSet(unusedAddresses);
        }

        public Set<String> asSet() {
            return addresses.asSet();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pool that = (Pool) o;
            return Objects.equals(addresses, that.addresses);
        }

        @Override
        public int hashCode() {
            return Objects.hash(addresses);
        }

        /** Create a new pool containing given ipAddresses */
        public static Pool of(Set<String> ipAddresses) {
            var addresses = Addresses.of(ipAddresses);
            if (addresses.protocol() == Addresses.Protocol.ipv4) {
                return new Ipv4Pool(addresses);
            }
            return new Pool(addresses);
        }

        /** Validates and returns the given IP address pool */
        public static Set<String> require(Set<String> pool) {
            return of(pool).asSet();
        }

    }

    /** A pool of IPv4-only addresses from which an allocation can be made. */
    public static class Ipv4Pool extends Pool {

        private Ipv4Pool(Addresses addresses) {
            super(addresses);
            if (addresses.protocol() != Addresses.Protocol.ipv4) {
                throw new IllegalArgumentException("Protocol of addresses must be " + Addresses.Protocol.ipv4);
            }
        }

        @Override
        public Optional<Allocation> findAllocation(LockedNodeList nodes, NameResolver resolver) {
            return findUnused(nodes).stream()
                                    .findFirst()
                                    .map(addr -> Allocation.ofIpv4(addr, resolver));
        }

    }

    /** An IP address allocation from a pool */
    public static class Allocation {

        private final String hostname;
        private final String primary;
        private final Optional<String> secondary;

        private Allocation(String hostname, String primary, Optional<String> secondary) {
            Objects.requireNonNull(primary, "primary must be non-null");
            Objects.requireNonNull(secondary, "ipv4Address must be non-null");
            if (secondary.isPresent() && !isV4(secondary.get())) { // Secondary must be IPv4, if present
                throw new IllegalArgumentException("Invalid IPv4 address '" + secondary + "'");
            }
            this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
            this.primary = primary;
            this.secondary = secondary;
        }

        /**
         * Allocate an IPv6 address.
         *
         * A successful allocation is guaranteed to have an IPv6 address, but may also have an IPv4 address if the
         * hostname of the IPv6 address has an A record.
         *
         * @param ipAddress Unassigned IPv6 address
         * @param resolver DNS name resolver to use
         * @throws IllegalArgumentException if DNS is misconfigured
         * @return An allocation containing 1 IPv6 address and 1 IPv4 address (if hostname is dual-stack)
         */
        private static Allocation ofIpv6(String ipAddress, NameResolver resolver) {
            String hostname6 = resolver.getHostname(ipAddress).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipAddress));
            List<String> ipv4Addresses = resolver.getAllByNameOrThrow(hostname6).stream()
                                                 .filter(IP::isV4)
                                                 .collect(Collectors.toList());
            if (ipv4Addresses.size() > 1) {
                throw new IllegalArgumentException("Hostname " + hostname6 + " resolved to more than 1 IPv4 address: " + ipv4Addresses);
            }
            Optional<String> ipv4Address = ipv4Addresses.stream().findFirst();
            ipv4Address.ifPresent(addr -> {
                String hostname4 = resolver.getHostname(addr).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + addr));
                if (!hostname6.equals(hostname4)) {
                    throw new IllegalArgumentException(String.format("Hostnames resolved from each IP address do not " +
                                                                     "point to the same hostname [%s -> %s, %s -> %s]",
                                                                     ipAddress, hostname6, addr, hostname4));
                }
            });
            return new Allocation(hostname6, ipAddress, ipv4Address);
        }

        /**
         * Allocate an IPv4 address. A successful allocation is guaranteed to have an IPv4 address.
         *
         * @param ipAddress Unassigned IPv4 address
         * @param resolver DNS name resolver to use
         * @return An allocation containing 1 IPv4 address.
         */
        private static Allocation ofIpv4(String ipAddress, NameResolver resolver) {
            String hostname4 = resolver.getHostname(ipAddress).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipAddress));
            List<String> addresses = resolver.getAllByNameOrThrow(hostname4).stream()
                                             .filter(IP::isV4)
                                             .collect(Collectors.toList());
            if (addresses.size() != 1) {
                throw new IllegalArgumentException("Hostname " + hostname4 + " did not resolve to exactly 1 address. " +
                                                   "Resolved: " + addresses);
            }
            return new Allocation(hostname4, addresses.get(0), Optional.empty());
        }

        /** Hostname pointing to the IP addresses in this */
        public String hostname() {
            return hostname;
        }

        /** Primary address of this allocation */
        public String primary() {
            return primary;
        }

        /** Secondary address of this allocation */
        public Optional<String> secondary() {
            return secondary;
        }

        /** All IP addresses in this */
        public Set<String> addresses() {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            secondary.ifPresent(builder::add);
            builder.add(primary);
            return builder.build();
        }

        @Override
        public String toString() {
            return String.format("IP allocation [primary=%s, secondary=%s]", primary, secondary.orElse("<none>"));
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

}
