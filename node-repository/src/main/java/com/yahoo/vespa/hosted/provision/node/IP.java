// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedBytes;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver.RecordType;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static final Comparator<InetAddress> NATURAL_ORDER = (ip1, ip2) -> {
        byte[] address1 = ip1.getAddress();
        byte[] address2 = ip2.getAddress();

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

        public static final Config EMPTY = Config.ofEmptyPool(Set.of());

        private final Set<String> primary;
        private final Pool pool;

        public static Config ofEmptyPool(Set<String> primary) {
            return new Config(primary, Set.of(), List.of());
        }

        public static Config of(Set<String> primary, Set<String> ipPool, List<Address> addressPool) {
            return new Config(primary, ipPool, addressPool);
        }

        /** LEGACY TEST CONSTRUCTOR - use of() variants and/or the with- methods. */
        public Config(Set<String> primary, Set<String> pool) {
            this(primary, pool, List.of());
        }

        /** DO NOT USE: Public for NodeSerializer. */
        public Config(Set<String> primary, Set<String> pool, List<Address> addresses) {
            this.primary = ImmutableSet.copyOf(Objects.requireNonNull(primary, "primary must be non-null"));
            this.pool = Pool.of(Objects.requireNonNull(pool, "pool must be non-null"),
                                Objects.requireNonNull(addresses, "addresses must be non-null"));
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
        public Config withPool(Pool pool) {
            return new Config(primary, pool.getIpSet(), pool.getAddressList());
        }

        /** Returns a copy of this with pool set to given value */
        public Config withPrimary(Set<String> primary) {
            return new Config(primary, pool.getIpSet(), pool.getAddressList());
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
            return String.format("ip config primary=%s pool=%s", primary, pool.getIpSet());
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
                        addresses.addAll(node.ipConfig().pool().getIpSet());
                        otherAddresses.addAll(other.ipConfig().pool().getIpSet());
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
    public static class IpAddresses {

        private final Set<String> ipAddresses;
        private final Protocol protocol;

        private IpAddresses(Set<String> ipAddresses, Protocol protocol) {
            this.ipAddresses = ImmutableSet.copyOf(Objects.requireNonNull(ipAddresses, "addresses must be non-null"));
            this.protocol = Objects.requireNonNull(protocol, "type must be non-null");
        }

        public Set<String> asSet() {
            return ipAddresses;
        }

        /** The protocol of addresses in this */
        public Protocol protocol() {
            return protocol;
        }

        /** Create addresses of the given set */
        private static IpAddresses of(Set<String> addresses) {
            long ipv6AddrCount = addresses.stream().filter(IP::isV6).count();
            if (ipv6AddrCount == addresses.size()) { // IPv6-only
                return new IpAddresses(addresses, Protocol.ipv6);
            }

            long ipv4AddrCount = addresses.stream().filter(IP::isV4).count();
            if (ipv4AddrCount == addresses.size()) { // IPv4-only
                return new IpAddresses(addresses, Protocol.ipv4);
            }

            // If we're dual-stacked, we must must have an equal number of addresses of each protocol.
            if (ipv4AddrCount == ipv6AddrCount) {
                return new IpAddresses(addresses, Protocol.dualStack);
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
     * A pool of addresses from which an allocation can be made.
     *
     * Addresses in this are available for use by Linux containers.
     */
    public static class Pool {

        private final IpAddresses ipAddresses;
        private final List<Address> addresses;

        /** Creates an empty pool. */
        public static Pool of() {
            return of(Set.of(), List.of());
        }

        /** Create a new pool containing given ipAddresses */
        public static Pool of(Set<String> ipAddresses, List<Address> addresses) {
            IpAddresses ips = IpAddresses.of(ipAddresses);
            return new Pool(ips, addresses);
        }

        private Pool(IpAddresses ipAddresses, List<Address> addresses) {
            this.ipAddresses = Objects.requireNonNull(ipAddresses, "ipAddresses must be non-null");
            this.addresses = Objects.requireNonNull(addresses, "addresses must be non-null");
        }

        /**
         * Find a free allocation in this pool. Note that the allocation is not final until it is assigned to a node
         *
         * @param nodes a locked list of all nodes in the repository
         * @return an allocation from the pool, if any can be made
         */
        public Optional<Allocation> findAllocation(LockedNodeList nodes, NameResolver resolver) {
            if (ipAddresses.asSet().isEmpty()) {
                // IP addresses have not yet been resolved and should be done later.
                return findUnusedAddressStream(nodes)
                        .map(Allocation::ofAddress)
                        .findFirst();
            }

            if (ipAddresses.protocol == IpAddresses.Protocol.ipv4) {
                return findUnusedIpAddresses(nodes).stream()
                        .findFirst()
                        .map(addr -> Allocation.ofIpv4(addr, resolver));
            }

            var unusedAddresses = findUnusedIpAddresses(nodes);
            var allocation = unusedAddresses.stream()
                                            .filter(IP::isV6)
                                            .findFirst()
                                            .map(addr -> Allocation.ofIpv6(addr, resolver));
            allocation.flatMap(Allocation::ipv4Address).ifPresent(ipv4Address -> {
                if (!unusedAddresses.contains(ipv4Address)) {
                    throw new IllegalArgumentException("Allocation resolved " + ipv4Address + " from hostname " +
                                                       allocation.get().hostname +
                                                       ", but that address is not owned by this node");
                }
            });
            return allocation;
        }

        /**
         * Finds all unused IP addresses in this pool
         *
         * @param nodes a list of all nodes in the repository
         */
        public Set<String> findUnusedIpAddresses(NodeList nodes) {
            var unusedAddresses = new LinkedHashSet<>(getIpSet());
            nodes.matching(node -> node.ipConfig().primary().stream().anyMatch(ip -> getIpSet().contains(ip)))
                 .forEach(node -> unusedAddresses.removeAll(node.ipConfig().primary()));
            return Collections.unmodifiableSet(unusedAddresses);
        }

        /**
         * Returns the number of unused IP addresses in the pool, assuming any and all unaccounted for hostnames
         * in the pool are resolved to exactly 1 IP address (or 2 with {@link IpAddresses.Protocol#dualStack}).
         */
        public int eventuallyUnusedAddressCount(NodeList nodes) {
            // The address pool is filled immediately upon provisioning in dynamically provisioned zones,
            // and within short time the IP address pool is filled.  For all other cases, the IP address
            // pool is already filled.
            //
            // The count in this method relies on the size of the IP address pool if that's non-empty,
            // otherwise fall back to the address/hostname pool.


            Set<String> currentIpAddresses = this.ipAddresses.asSet();
            if (!currentIpAddresses.isEmpty()) {
                return findUnusedIpAddresses(nodes).size();
            }

            return (int) findUnusedAddressStream(nodes).count();
        }

        private Stream<Address> findUnusedAddressStream(NodeList nodes) {
            Set<String> hostnames = nodes.stream().map(Node::hostname).collect(Collectors.toSet());
            return addresses.stream().filter(address -> !hostnames.contains(address.hostname()));
        }

        public IpAddresses.Protocol getProtocol() {
            return ipAddresses.protocol;
        }

        public Set<String> getIpSet() {
            return ipAddresses.asSet();
        }

        public List<Address> getAddressList() {
            return addresses;
        }

        public Pool withIpAddresses(Set<String> ipAddresses) {
            return Pool.of(ipAddresses, addresses);
        }

        public Pool withAddresses(List<Address> addresses) {
            return Pool.of(ipAddresses.ipAddresses, addresses);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pool that = (Pool) o;
            return Objects.equals(ipAddresses, that.ipAddresses);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ipAddresses);
        }

    }

    /** An address allocation from a pool */
    public static class Allocation {

        private final String hostname;
        private final Optional<String> ipv4Address;
        private final Optional<String> ipv6Address;

        private Allocation(String hostname, Optional<String> ipv4Address, Optional<String> ipv6Address) {
            this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
            this.ipv4Address = Objects.requireNonNull(ipv4Address, "ipv4Address must be non-null");
            this.ipv6Address = Objects.requireNonNull(ipv6Address, "ipv6Address must be non-null");
        }

        /**
         * Allocate an IPv6 address.
         *
         * A successful allocation is guaranteed to have an IPv6 address, but may also have an IPv4 address if the
         * hostname of the IPv6 address has an A record.
         *
         * @param ipv6Address Unassigned IPv6 address
         * @param resolver DNS name resolver to use
         * @throws IllegalArgumentException if DNS is misconfigured
         * @return An allocation containing 1 IPv6 address and 1 IPv4 address (if hostname is dual-stack)
         */
        private static Allocation ofIpv6(String ipv6Address, NameResolver resolver) {
            if (!isV6(ipv6Address)) {
                throw new IllegalArgumentException("Invalid IPv6 address '" + ipv6Address + "'");
            }

            String hostname6 = resolver.resolveHostname(ipv6Address).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipv6Address));
            List<String> ipv4Addresses = resolver.resolveAll(hostname6).stream()
                                                 .filter(IP::isV4)
                                                 .collect(Collectors.toList());
            if (ipv4Addresses.size() > 1) {
                throw new IllegalArgumentException("Hostname " + hostname6 + " resolved to more than 1 IPv4 address: " + ipv4Addresses);
            }
            Optional<String> ipv4Address = ipv4Addresses.stream().findFirst();
            ipv4Address.ifPresent(addr -> {
                String hostname4 = resolver.resolveHostname(addr).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + addr));
                if (!hostname6.equals(hostname4)) {
                    throw new IllegalArgumentException(String.format("Hostnames resolved from each IP address do not " +
                                                                     "point to the same hostname [%s -> %s, %s -> %s]",
                                                                     ipv6Address, hostname6, addr, hostname4));
                }
            });
            return new Allocation(hostname6, ipv4Address, Optional.of(ipv6Address));
        }

        /**
         * Allocate an IPv4 address. A successful allocation is guaranteed to have an IPv4 address.
         *
         * @param ipAddress Unassigned IPv4 address
         * @param resolver DNS name resolver to use
         * @return An allocation containing 1 IPv4 address.
         */
        private static Allocation ofIpv4(String ipAddress, NameResolver resolver) {
            String hostname4 = resolver.resolveHostname(ipAddress).orElseThrow(() -> new IllegalArgumentException("Could not resolve IP address: " + ipAddress));
            List<String> addresses = resolver.resolveAll(hostname4).stream()
                                             .filter(IP::isV4)
                                             .collect(Collectors.toList());
            if (addresses.size() != 1) {
                throw new IllegalArgumentException("Hostname " + hostname4 + " did not resolve to exactly 1 address. " +
                                                   "Resolved: " + addresses);
            }
            return new Allocation(hostname4, Optional.of(addresses.get(0)), Optional.empty());
        }

        private static Allocation ofAddress(Address address) {
            return new Allocation(address.hostname(), Optional.empty(), Optional.empty());
        }

        /** Hostname pointing to the IP addresses in this */
        public String hostname() {
            return hostname;
        }

        /** IPv4 address of this allocation */
        public Optional<String> ipv4Address() {
            return ipv4Address;
        }

        /** IPv6 address of this allocation */
        public Optional<String> ipv6Address() {
            return ipv6Address;
        }

        /** All IP addresses in this */
        public Set<String> addresses() {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            ipv4Address.ifPresent(builder::add);
            ipv6Address.ifPresent(builder::add);
            return builder.build();
        }

        @Override
        public String toString() {
            return String.format("Address allocation [hostname=%s, IPv4=%s, IPv6=%s]",
                    hostname, ipv4Address.orElse("<none>"), ipv6Address.orElse("<none>"));
        }

    }

    /** Parse given IP address string */
    public static InetAddress parse(String ipAddress) {
        try {
            return InetAddresses.forString(ipAddress);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid IP address '" + ipAddress + "'", e);
        }
    }

    /** Verify DNS configuration of given hostname and IP address */
    public static void verifyDns(String hostname, String ipAddress, NameResolver resolver) {
        RecordType recordType = isV6(ipAddress) ? RecordType.AAAA : RecordType.A;
        Set<String> addresses = resolver.resolve(hostname, recordType);
        if (!addresses.equals(Set.of(ipAddress)))
            throw new IllegalArgumentException("Expected " + hostname + " to resolve to " + ipAddress +
                                               ", but got " + addresses);

        Optional<String> reverseHostname = resolver.resolveHostname(ipAddress);
        if (reverseHostname.isEmpty())
            throw new IllegalArgumentException(ipAddress + " did not resolve to a hostname");

        if (!reverseHostname.get().equals(hostname))
            throw new IllegalArgumentException(ipAddress + " resolved to " + reverseHostname.get() +
                                               ", which does not match expected hostname " + hostname);
    }

    /** Convert IP address to string. This uses :: for zero compression in IPv6 addresses.  */
    public static String asString(InetAddress inetAddress) {
        return InetAddresses.toAddrString(inetAddress);
    }

    /** Returns whether given string is an IPv4 address */
    public static boolean isV4(String ipAddress) {
        return ipAddress.contains(".");
    }

    /** Returns whether given string is an IPv6 address */
    public static boolean isV6(String ipAddress) {
        return ipAddress.contains(":");
    }

}
