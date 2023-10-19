// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver.RecordType;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
public record IP() {

    /** IP version.  Can be compared with ==, !=, and equals(). */
    public static class Version implements Comparable<Version> {
        public static final Version v4 = new Version(4);
        public static final Version v6 = new Version(6);

        private final int version;

        public static Version fromIpAddress(String ipAddress) {
            if (ipAddress.contains(":")) return v6;
            if (ipAddress.contains(".")) return v4;
            throw new IllegalArgumentException("Failed to deduce the IP version from the textual representation of the IP address: " + ipAddress);
        }

        public static Version fromIsIpv6(boolean isIpv6) { return isIpv6 ? v6 : v4; }

        private Version(int version) { this.version = version; }

        public boolean is4() { return version == 4; }
        public boolean is6() { return version == 6; }

        public RecordType toForwardRecordType() { return is4() ? RecordType.A : RecordType.AAAA; }

        @Override
        public int compareTo(Version that) { return Integer.compare(this.version, that.version); }

        @Override
        public String toString() { return "IPv" + version; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Version version1 = (Version) o;
            return version == version1.version;
        }

        @Override
        public int hashCode() { return Objects.hash(version); }
    }

    /**
     * IP configuration of a node
     *
     * @param primary The primary addresses of this. These addresses are used when communicating with the node itself
     * @param pool    The IP address pool available on a node
     */
    public record Config(List<String> primary, Pool pool) {

        public static final Config EMPTY = Config.ofEmptyPool();

        public static Config ofEmptyPool(String... primary) { return ofEmptyPool(List.of(primary)); }
        public static Config ofEmptyPool(List<String> primary) { return of(primary, List.of(), List.of()); }
        public static Config of(List<String> primary, List<String> ips, List<HostName> hostnames) { return new Config(primary, Pool.of(ips, hostnames)); }
        public static Config of(List<String> primary, List<String> ips) { return of(primary, ips, List.of()); }

        public Config(List<String> primary, Pool pool) {
            this.primary = List.copyOf(Objects.requireNonNull(primary, "primary must be non-null"));
            this.pool = Objects.requireNonNull(pool, "pool must be non-null");
        }

        /** Returns a copy of this with pool set to given value */
        public Config withPool(Pool pool) {
            return new Config(primary, pool);
        }

        /** Returns a copy of this with pool set to given value */
        public Config withPrimary(List<String> primary) {
            return new Config(primary, pool);
        }

        /**
         * Verify IP config of given nodes
         *
         * @throws IllegalArgumentException if there are IP conflicts with existing nodes
         */
        public static LockedNodeList verify(List<Node> nodes, LockedNodeList allNodes, Zone zone) {
            NodeList sortedNodes = allNodes.sortedBy(Comparator.comparing(Node::hostname));
            for (var node : nodes) {
                Space ipSpace = Space.of(zone, node.cloudAccount());
                for (var other : sortedNodes) {
                    if (node.equals(other)) continue;
                    if (canAssignIpOf(other, node)) continue;

                    var addresses = new HashSet<>(node.ipConfig().primary());
                    var otherAddresses = new HashSet<>(other.ipConfig().primary());
                    if (node.type().isHost()) { // Addresses of a host can never overlap with any other nodes
                        addresses.addAll(node.ipConfig().pool().ips());
                        otherAddresses.addAll(other.ipConfig().pool().ips());
                    }
                    otherAddresses.removeIf(otherIp -> !ipSpace.contains(otherIp, other.cloudAccount()));
                    otherAddresses.retainAll(addresses);
                    if (!otherAddresses.isEmpty())
                        throw new IllegalArgumentException("Cannot assign " + addresses + " to " + node.hostname() +
                                                           ": " + otherAddresses + " already assigned to " +
                                                           other.hostname());
                }
            }
            return allNodes.childList(nodes);
        }

        /** Returns whether IP address of existing node can be assigned to node */
        private static boolean canAssignIpOf(Node existingNode, Node node) {
            if (node.parentHostname().isPresent() == existingNode.parentHostname().isPresent()) return false; // Not a parent-child node
            if (node.parentHostname().isEmpty()) return canAssignIpOf(node, existingNode);
            if (!node.parentHostname().get().equals(existingNode.hostname())) return false; // Wrong host
            return switch (node.type()) {
                case proxy -> existingNode.type() == proxyhost;
                case config -> existingNode.type() == confighost;
                case controller -> existingNode.type() == controllerhost;
                default -> false;
            };
        }

        public static Node verify(Node node, LockedNodeList allNodes, Zone zone) {
            return verify(List.of(node), allNodes, zone).asList().get(0);
        }

    }

    /** A list of IP addresses and their protocol */
    record IpAddresses(List<String> addresses, Stack stack) {

        public IpAddresses(List<String> addresses, Stack stack) {
            this.addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses must be non-null"));
            this.stack = Objects.requireNonNull(stack, "type must be non-null");
        }

        /** Create addresses of the given set */
        private static IpAddresses of(List<String> addresses) {
            long ipv6AddrCount = addresses.stream().filter(IP::isV6).count();
            if (ipv6AddrCount == addresses.size()) { // IPv6-only
                return new IpAddresses(addresses, Stack.ipv6);
            }

            long ipv4AddrCount = addresses.stream().filter(IP::isV4).count();
            if (ipv4AddrCount == addresses.size()) { // IPv4-only
                return new IpAddresses(addresses, Stack.ipv4);
            }

            // If we're dual-stacked, we must have an equal number of addresses of each protocol.
            if (ipv4AddrCount == ipv6AddrCount) {
                return new IpAddresses(addresses, Stack.dual);
            }

            throw new IllegalArgumentException(String.format("Dual-stacked IP address list must have an " +
                                                             "equal number of addresses of each version " +
                                                             "[IPv6 address count = %d, IPv4 address count = %d]",
                                                             ipv6AddrCount, ipv4AddrCount));
        }

        public enum Stack {

            dual("dual-stack", Version.v4, Version.v6),
            ipv4("IPv4-only", Version.v4),
            ipv6("IPv6-only", Version.v6);

            private final String description;
            private final Set<Version> versions;

            Stack(String description, Version... versions) {
                this.description = description;
                this.versions = Set.of(versions);
            }

            public boolean supports(Version version) { return versions.contains(version); }
        }

    }

    /**
     * A pool of addresses from which an allocation can be made.
     *
     * Addresses in this are available for use by Linux containers.
     */
    public record Pool(IpAddresses ipAddresses, List<HostName> hostnames) {

        public static final Pool EMPTY = Pool.of(List.of(), List.of());

        /** Create a new pool containing given ips */
        public static Pool of(List<String> ips, List<HostName> hostnames) {
            return new Pool(IpAddresses.of(ips), hostnames);
        }

        public Pool(IpAddresses ipAddresses, List<HostName> hostnames) {
            this.ipAddresses = Objects.requireNonNull(ipAddresses, "ipAddresses must be non-null");
            this.hostnames = List.copyOf(Objects.requireNonNull(hostnames, "hostnames must be non-null"));
        }

        /** The number of hosts in this pool: each host has a name and/or one or two IP addresses. */
        public long size() {
            return hostnames().isEmpty() ?
                   Math.max(ipAddresses.addresses.stream().filter(IP::isV4).count(),
                            ipAddresses.addresses.stream().filter(IP::isV6).count()) :
                   hostnames().size();
        }

        public List<String> ips() { return ipAddresses.addresses; }

        /**
         * Find a free allocation in this pool. Note that the allocation is not final until it is assigned to a node
         *
         * <p>TODO(2023-09-20): Once all dynamically provisioned hosts have been reprovisioned, the order of IP addresses
         * is significant and should be 1:1 with the order of hostnames, if both are filled.  This needs to be verified.
         * This can be used to strengthen validation, and simplify the selection of the Allocation to return.</p>
         *
         * @param context allocation context
         * @param nodes   a locked list of all nodes in the repository
         * @return an allocation from the pool, if any can be made
         */
        public Optional<Allocation> findAllocation(Allocation.Context context, LockedNodeList nodes) {
            if (ipAddresses.addresses.isEmpty()) {
                // IP addresses have not yet been resolved and should be done later.
                return findUnusedHostnames(nodes).map(Allocation::ofHostname)
                                                 .findFirst();
            }

            List<String> unusedIps = findUnusedIpAddresses(nodes);

            if (context.allocateFromUnusedHostname())
                return findUnusedHostnames(nodes).findFirst().map(hostname -> Allocation.fromHostname(context, hostname, ipAddresses.stack, unusedIps));

            if (ipAddresses.stack == IpAddresses.Stack.ipv4) {
                return unusedIps.stream()
                                .findFirst()
                                .map(addr -> Allocation.ofIpv4(addr, context.resolver()));
            }

            var allocation = unusedIps.stream()
                                      .filter(IP::isV6)
                                      .findFirst()
                                      .map(addr -> Allocation.ofIpv6(addr, context.resolver()));
            allocation.flatMap(Allocation::ipv4Address).ifPresent(ipv4Address -> {
                if (!unusedIps.contains(ipv4Address)) {
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
        public List<String> findUnusedIpAddresses(NodeList nodes) {
            List<String> unusedAddresses = new ArrayList<>(ips());
            nodes.matching(node -> node.ipConfig().primary().stream().anyMatch(ip -> ips().contains(ip)))
                 .forEach(node -> unusedAddresses.removeAll(node.ipConfig().primary()));
            return unusedAddresses;
        }

        private Stream<HostName> findUnusedHostnames(NodeList nodes) {
            Set<String> usedHostnames = nodes.stream().map(Node::hostname).collect(Collectors.toSet());
            return hostnames.stream().filter(hostname -> !usedHostnames.contains(hostname.value()));
        }

        public Pool withIpAddresses(List<String> ipAddresses) {
            return Pool.of(ipAddresses, hostnames);
        }

        public Pool withHostnames(List<HostName> hostnames) {
            return Pool.of(ipAddresses.addresses, hostnames);
        }

    }

    /** An address allocation from a pool */
    public record Allocation(String hostname, Optional<String> ipv4Address, Optional<String> ipv6Address) {

        public Allocation {
            Objects.requireNonNull(hostname, "hostname must be non-null");
            Objects.requireNonNull(ipv4Address, "ipv4Address must be non-null");
            Objects.requireNonNull(ipv6Address, "ipv6Address must be non-null");
        }

        public static class Context {
            private final CloudName cloudName;
            private final boolean exclave;
            private final NameResolver resolver;

            private Context(CloudName cloudName, boolean exclave, NameResolver resolver) {
                this.cloudName = cloudName;
                this.exclave = exclave;
                this.resolver = resolver;
            }

            public static Context from(CloudName cloudName, boolean exclave, NameResolver resolver) {
                return new Context(cloudName, exclave, resolver);
            }

            public NameResolver resolver() { return resolver; }

            public boolean allocateFromUnusedHostname() { return exclave; }

            public boolean hasIpNotInDns(Version version) {
                if (exclave && cloudName == CloudName.GCP && version.is4()) {
                    // Exclave nodes in GCP have IPv4, because load balancers backends are required to be IPv4,
                    // but it's private (10.x).  The hostname only resolves to the public IPv6 address.
                    return true;
                }

                return false;
            }
        }

        /**
         * Allocate an IPv6 address.
         *
         * <p>A successful allocation is guaranteed to have an IPv6 address, but may also have an IPv4 address if the
         * hostname of the IPv6 address has an A record.</p>
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
                                                 .toList();
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
                                             .toList();
            if (addresses.size() != 1) {
                throw new IllegalArgumentException("Hostname " + hostname4 + " did not resolve to exactly 1 address. " +
                                                   "Resolved: " + addresses);
            }
            return new Allocation(hostname4, Optional.of(addresses.get(0)), Optional.empty());
        }

        private static Allocation fromHostname(Context context, HostName hostname, IpAddresses.Stack stack, List<String> unusedIps) {
            Optional<String> ipv4Address = resolveAndVerify(context, hostname, stack, IP.Version.v4, unusedIps);
            Optional<String> ipv6Address = resolveAndVerify(context, hostname, stack, IP.Version.v6, unusedIps);
            return new Allocation(hostname.value(), ipv4Address, ipv6Address);
        }

        private static Optional<String> resolveAndVerify(Context context, HostName hostname, IpAddresses.Stack stack, Version version, List<String> unusedIps) {
            if (context.hasIpNotInDns(version)) {
                List<String> candidates = unusedIps.stream()
                                                   .filter(a -> IP.Version.fromIpAddress(a).equals(version))
                                                   .toList();
                if (candidates.size() != 1) {
                    throw new IllegalStateException("Unable to find a unique child IP address of " + hostname + ": Found " + candidates);
                }
                return candidates.stream().findFirst();
            }

            Optional<String> address = resolveOptional(hostname.value(), context.resolver(), version.toForwardRecordType());
            if (stack.supports(version) && address.isEmpty())
                throw new IllegalArgumentException(stack.description + " hostname " + hostname.value() + " did not resolve to an " + version + " address");
            if (!stack.supports(version) && address.isPresent())
                throw new IllegalArgumentException(stack.description + " hostname " + hostname.value() + " has an " + version + " address: " + address.get());

            return address;
        }

        private static Optional<String> resolveOptional(String hostname, NameResolver resolver, RecordType recordType) {
            Set<String> values = resolver.resolve(hostname, recordType);
            return switch (values.size()) {
                case 0 -> Optional.empty();
                case 1 -> Optional.of(values.iterator().next());
                default -> throw new IllegalArgumentException("Hostname " + hostname + " resolved to more than one " + recordType.description() + ": " + values);
            };
        }

        private static Allocation ofHostname(HostName hostName) {
            return new Allocation(hostName.value(), Optional.empty(), Optional.empty());
        }

        /** All IP addresses in this */
        public List<String> addresses() {
            return Stream.concat(ipv4Address.stream(), ipv6Address.stream()).toList();
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

    /** Convert IP address to string. This uses :: for zero compression in IPv6 addresses.  */
    public static String asString(InetAddress inetAddress) {
        return InetAddresses.toAddrString(inetAddress);
    }

    /** Returns whether given string is an IPv4 address */
    public static boolean isV4(String ipAddress) {
        return !isV6(ipAddress) && ipAddress.contains(".");
    }

    /** Returns whether given string is an IPv6 address */
    public static boolean isV6(String ipAddress) {
        return ipAddress.contains(":");
    }

    /** Returns whether given string is a public IP address */
    private static boolean isPublic(String ip) {
        InetAddress address = parse(ip);
        return ! address.isLoopbackAddress() && ! address.isLinkLocalAddress() && ! address.isSiteLocalAddress();
    }

    @FunctionalInterface
    public interface Space {
        static Space of(Zone zone) { return of(zone, zone.cloud().account()); }

        /** Returns the IP space of a cloud account in a zone. */
        static Space of(Zone zone, CloudAccount cloudAccount) { return (ip, account) -> sharedIp(ip, account, cloudAccount, zone); }

        private static boolean sharedIp(String ip, CloudAccount sourceCloudAccount, CloudAccount targetCloudAccount, Zone zone) {
            // IPs within the same account and zone are always shared.
            if (sourceCloudAccount.equals(targetCloudAccount))
                return true;

            // Only public IPs inside (outside) an exclave account are shared outside (inside).
            if (sourceCloudAccount.isExclave(zone) || targetCloudAccount.isExclave(zone))
                return isPublic(ip);

            // IPs in noclave and inclave are always shared.
            return true;
        }

        /** Returns true if the IP in the given account is in this IP space. */
        boolean contains(String ip, CloudAccount cloudAccount);
    }

}
