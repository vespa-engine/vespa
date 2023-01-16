// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.google.common.net.InetAddresses;
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

    public static final Acl EMPTY = new Acl(Set.of(), Set.of(), Set.of(), Set.of());

    private final Set<Node> trustedNodes;
    private final Set<Integer> trustedPorts;
    private final Set<Integer> trustedUdpPorts;
    private final Set<String> trustedNetworks;

    /**
     * @param trustedPorts TCP Ports to trust
     * @param trustedUdpPorts UDP ports to trust
     * @param trustedNodes Nodes to trust
     * @param trustedNetworks Networks (in CIDR notation) to trust
     */
    public Acl(Set<Integer> trustedPorts, Set<Integer> trustedUdpPorts, Set<Node> trustedNodes, Set<String> trustedNetworks) {
        this.trustedNodes = copyOfNullable(trustedNodes);
        this.trustedPorts = copyOfNullable(trustedPorts);
        this.trustedUdpPorts = copyOfNullable(trustedUdpPorts);
        this.trustedNetworks = copyOfNullable(trustedNetworks);
    }

    public Acl(Set<Integer> trustedPorts, Set<Node> trustedNodes) {
        this(trustedPorts, Set.of(), trustedNodes, Set.of());
    }

    public List<String> toRules(IPVersion ipVersion) {
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

        // Allow trusted UDP ports if any
        if (!trustedUdpPorts.isEmpty()) {
            rules.add("-A INPUT -p udp -m multiport --dports " + joinPorts(trustedUdpPorts) + " -j ACCEPT");
        }

        // Allow traffic from trusted nodes, limited to specific ports, if any
        getTrustedNodes(ipVersion).stream()
                                  .map(node -> {
                                      StringBuilder rule = new StringBuilder();
                                      rule.append("-A INPUT -s ")
                                          .append(node.inetAddressString())
                                          .append(ipVersion.singleHostCidr());
                                      if (!node.ports.isEmpty()) {
                                          rule.append(" -p tcp -m multiport --dports ")
                                              .append(joinPorts(node.ports()));
                                      }
                                      rule.append(" -j ACCEPT");
                                      return rule.toString();
                                  })
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
        return ports.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
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

    public Set<Integer> getTrustedUdpPorts() {
        return trustedUdpPorts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Acl acl = (Acl) o;
        return trustedNodes.equals(acl.trustedNodes) &&
               trustedPorts.equals(acl.trustedPorts) &&
               trustedUdpPorts.equals(acl.trustedUdpPorts) &&
               trustedNetworks.equals(acl.trustedNetworks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trustedNodes, trustedPorts, trustedUdpPorts, trustedNetworks);
    }

    @Override
    public String toString() {
        return "Acl{" +
               "trustedNodes=" + trustedNodes +
               ", trustedPorts=" + trustedPorts +
               ", trustedUdpPorts=" + trustedUdpPorts +
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

    public record Node(String hostname, InetAddress inetAddress, Set<Integer> ports) {

        public Node(String hostname, String ipAddress, Set<Integer> ports) {
            this(hostname, InetAddresses.forString(ipAddress), ports);
        }

        public String inetAddressString() {
            return InetAddresses.toAddrString(inetAddress);
        }

        @Override
        public String toString() {
            return "Node{" +
                   "hostname='" + hostname + '\'' +
                   ", inetAddress=" + inetAddress +
                   ", ports=" + ports +
                   '}';
        }
    }

    public static class Builder {

        private final Set<Node> trustedNodes = new HashSet<>();
        private final Set<Integer> trustedPorts = new HashSet<>();
        private final Set<Integer> trustedUdpPorts = new HashSet<>();
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

        public Builder withTrustedNode(String hostname, String ipAddress) {
            return withTrustedNode(hostname, ipAddress, Set.of());
        }

        public Builder withTrustedNode(String hostname, String ipAddress, Set<Integer> ports) {
            return withTrustedNode(new Node(hostname, ipAddress, ports));
        }

        public Builder withTrustedNode(String hostname, InetAddress inetAddress, Set<Integer> ports) {
            return withTrustedNode(new Node(hostname, inetAddress, ports));
        }

        public Builder withTrustedPorts(Integer... ports) {
            trustedPorts.addAll(List.of(ports));
            return this;
        }

        public Builder withTrustedUdpPorts(Integer... ports) {
            trustedUdpPorts.addAll(List.of(ports));
            return this;
        }

        public Builder withTrustedNetworks(Set<String> networks) {
            trustedNetworks.addAll(networks);
            return this;
        }

        public Acl build() {
            return new Acl(trustedPorts, trustedUdpPorts, trustedNodes, trustedNetworks);
        }
    }

}
