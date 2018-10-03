// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.net.InetAddress;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class represents an ACL for a specific container instance.
 *
 * @author mpolden
 * @author smorgrav
 */
public class Acl {

    private final Set<Node> trustedNodes;
    private final Set<Integer> trustedPorts;

    /**
     * @param trustedPorts Ports that hostname should trust
     * @param trustedNodes Other nodes that this hostname should trust
     */
    public Acl(Set<Integer> trustedPorts, Set<Node> trustedNodes) {
        this.trustedNodes = trustedNodes != null ? Collections.unmodifiableSet(trustedNodes) : Collections.emptySet();
        this.trustedPorts = trustedPorts != null ? Collections.unmodifiableSet(trustedPorts) : Collections.emptySet();
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
        String commaSeparatedPorts = trustedPorts.stream().map(i -> Integer.toString(i)).sorted().collect(Collectors.joining(","));
        if (!commaSeparatedPorts.isEmpty())
            rules.add("-A INPUT -p tcp -m multiport --dports " + commaSeparatedPorts + " -j ACCEPT");

        // Allow traffic from trusted nodes
        getTrustedNodes(ipVersion).stream()
                .map(node -> "-A INPUT -s " + node.inetAddressString() + ipVersion.singleHostCidr() + " -j ACCEPT")
                .sorted()
                .forEach(rules::add);

        // We reject instead of dropping to give us an easier time to figure out potential network issues
        rules.add("-A INPUT -j REJECT --reject-with " + ipVersion.icmpPortUnreachable());

        return Collections.unmodifiableList(rules);
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
        Acl that = (Acl) o;
        return Objects.equals(trustedPorts, that.trustedPorts) &&
                Objects.equals(trustedNodes, that.trustedNodes);
    }

    @Override
    public String toString() {
        return "Acl{" +
                "trustedNodes=" + trustedNodes +
                ", trustedPorts=" + trustedPorts +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(trustedPorts, trustedNodes);
    }

    public static class Node {
        private final String hostname;
        private final InetAddress inetAddress;

        public Node(String hostname, String ipAddress) {
            this(hostname, InetAddresses.forString(ipAddress));
        }

        public Node(String hostname, InetAddress inetAddress) {
            this.hostname = hostname;
            this.inetAddress = inetAddress;
        }

        public String hostname() {
            return hostname;
        }

        public InetAddress inetAddress() {
            return inetAddress;
        }

        public String inetAddressString() {
            return InetAddresses.toAddrString(inetAddress);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return Objects.equals(hostname, node.hostname) &&
                    Objects.equals(inetAddress, node.inetAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, inetAddress);
        }

        @Override
        public String toString() {
            return "Node{" +
                    "hostname='" + hostname + '\'' +
                    ", inetAddress=" + inetAddress +
                    '}';
        }
    }

    public static class Builder {
        private final Set<Node> trustedNodes = new HashSet<>();
        private final Set<Integer> trustedPorts = new HashSet<>();

        public Builder() { }

        public Builder(Acl acl) {
            trustedNodes.addAll(acl.trustedNodes);
            trustedPorts.addAll(acl.trustedPorts);
        }

        public Builder withTrustedNode(String hostname, String ipAddress) {
            return withTrustedNode(new Node(hostname, ipAddress));
        }

        public Builder withTrustedNode(String hostname, InetAddress inetAddress) {
            return withTrustedNode(new Node(hostname, inetAddress));
        }

        public Builder withTrustedNode(Node node) {
            trustedNodes.add(node);
            return this;
        }

        public Builder withTrustedPorts(Integer... ports) {
            trustedPorts.addAll(Arrays.asList(ports));
            return this;
        }

        public Acl build() {
            return new Acl(trustedPorts, trustedNodes);
        }
    }
}
