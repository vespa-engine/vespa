// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class represents an ACL for a specific container instance.
 *
 * @author mpolden
 * @author smorgrav
 */
public class Acl {

    public static final Acl EMPTY = new Acl(Set.of(), Set.of(), Set.of());

    private final Set<Node> trustedNodes;
    private final Set<Integer> trustedPorts;
    private final Set<String> trustedNetworks;

    /**
     * @param trustedPorts Ports to trust
     * @param trustedNodes Nodes to trust
     * @param trustedNetworks Networks (in CIDR notation) to trust
     */
    public Acl(Set<Integer> trustedPorts, Set<Node> trustedNodes, Set<String> trustedNetworks) {
        this.trustedNodes = copyOfNullable(trustedNodes);
        this.trustedPorts = copyOfNullable(trustedPorts);
        this.trustedNetworks = copyOfNullable(trustedNetworks);
    }

    public Acl(Set<Integer> trustedPorts, Set<Node> trustedNodes) {
        this(trustedPorts, trustedNodes, Set.of());
    }

    public List<String> toRules(IPVersion ipVersion, NodeType nodeType) {
        List<String> rules = new LinkedList<>();

        // We reject with rules instead of using policies
        rules.add("-P INPUT ACCEPT");
        rules.add("-P FORWARD ACCEPT");
        rules.add("-P OUTPUT ACCEPT");

        // Allow packets belonging to established connections
        rules.add( "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT");

        // Allow any loopback traffic
        rules.add("-A INPUT -i lo -j ACCEPT");

        // Allow ICMP packets. See http://shouldiblockicmp.com/
        rules.add("-A INPUT -p " + ipVersion.icmpProtocol() + " -j ACCEPT");

        // Allow trusted ports if any
        if (!trustedPorts.isEmpty()) {
            rules.add("-A INPUT -p tcp -m multiport --dports " + joinPorts(trustedPorts) + " -j ACCEPT");
        }

        // Trust ZooKeeper from other config servers/controllers only
        if (nodeType.isConfigServerLike()) {
            Set<Integer> zooKeeperPorts = Set.of(2181, 2182, 2183);
            List<String> clusterAddresses = getTrustedNodes(ipVersion).stream()
                                                                      .filter(node -> node.type() == nodeType)
                                                                      .map(Node::inetAddressString)
                                                                      .sorted()
                                                                      .toList();
            for (var ipAddress : clusterAddresses) {
                rules.add("-A INPUT -s " + ipAddress + ipVersion.singleHostCidr() + " -p tcp -m multiport --dports " +
                          joinPorts(zooKeeperPorts) + " -j ACCEPT");
            }
            // Reject any other connections to ZooKeeper
            rules.add("-A INPUT -p tcp -m multiport --dports " + joinPorts(zooKeeperPorts) +
                      " -j REJECT --reject-with " + ipVersion.icmpPortUnreachable());
        }

        // Allow traffic from trusted nodes
        getTrustedNodes(ipVersion).stream()
                                  .map(node -> "-A INPUT -s " + node.inetAddressString() + ipVersion.singleHostCidr() + " -j ACCEPT")
                                  .sorted()
                                  .forEach(rules::add);

        // Allow traffic from trusted networks
        addressesOf(ipVersion, trustedNetworks).stream()
                                               .map(network -> "-A INPUT -s " + network + " -j ACCEPT")
                                               .sorted()
                                               .forEach(rules::add);

        // We reject instead of dropping to give us an easier time to figure out potential network issues
        rules.add("-A INPUT -j REJECT --reject-with " + ipVersion.icmpPortUnreachable());

        return Collections.unmodifiableList(rules);
    }

    private static String joinPorts(Collection<Integer> ports) {
        return ports.stream().map(String::valueOf).sorted().collect(Collectors.joining(","));
    }

    public Set<Node> getTrustedNodes() {
        return trustedNodes;
    }

    public Set<Node> getTrustedNodes(IPVersion ipVersion) {
        return trustedNodes.stream()
                .filter(node -> ipVersion.match(node.inetAddress()))
                .collect(Collectors.toSet());
    }

    public Set<Integer> getTrustedPorts() {
        return trustedPorts;
    }

    public Set<Integer> getTrustedPorts(IPVersion ipVersion) {
        return trustedPorts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Acl acl = (Acl) o;
        return trustedNodes.equals(acl.trustedNodes) &&
               trustedPorts.equals(acl.trustedPorts) &&
               trustedNetworks.equals(acl.trustedNetworks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trustedNodes, trustedPorts, trustedNetworks);
    }

    @Override
    public String toString() {
        return "Acl{" +
               "trustedNodes=" + trustedNodes +
               ", trustedPorts=" + trustedPorts +
               ", trustedNetworks=" + trustedNetworks +
               '}';
    }

    private static Set<String> addressesOf(IPVersion version, Set<String> addresses) {
        return addresses.stream()
                        .filter(version::match)
                        .collect(Collectors.toUnmodifiableSet());
    }

    private static <T> Set<T> copyOfNullable(Set<T> set) {
        return Optional.ofNullable(set).map(Set::copyOf).orElseGet(Set::of);
    }

    public record Node(String hostname, NodeType type, InetAddress inetAddress) {

        public Node(String hostname, NodeType type, String ipAddress) {
            this(hostname, type, InetAddresses.forString(ipAddress));
        }

        public String inetAddressString() {
            return InetAddresses.toAddrString(inetAddress);
        }

        @Override
        public String toString() {
            return "Node{" +
                   "hostname='" + hostname + '\'' +
                   ", inetAddress=" + inetAddress +
                   ", nodeType=" + type +
                   '}';
        }
    }

    public static class Builder {

        private final Set<Node> trustedNodes = new HashSet<>();
        private final Set<Integer> trustedPorts = new HashSet<>();
        private final Set<String> trustedNetworks = new HashSet<>();

        public Builder() { }

        public Builder(Acl acl) {
            trustedNodes.addAll(acl.trustedNodes);
            trustedPorts.addAll(acl.trustedPorts);
            trustedNetworks.addAll(acl.trustedNetworks);
        }

        public Builder withTrustedNode(Node node) {
            trustedNodes.add(node);
            return this;
        }

        public Builder withTrustedNode(String hostname, String ipAddress, NodeType nodeType) {
            return withTrustedNode(new Node(hostname, nodeType, ipAddress));
        }

        public Builder withTrustedNode(String hostname, InetAddress inetAddress, NodeType nodeType) {
            return withTrustedNode(new Node(hostname, nodeType, inetAddress));
        }

        public Builder withTrustedPorts(Integer... ports) {
            trustedPorts.addAll(List.of(ports));
            return this;
        }

        public Builder withTrustedNetworks(Set<String> networks) {
            trustedNetworks.addAll(networks);
            return this;
        }

        public Acl build() {
            return new Acl(trustedPorts, trustedNodes, trustedNetworks);
        }
    }

}
