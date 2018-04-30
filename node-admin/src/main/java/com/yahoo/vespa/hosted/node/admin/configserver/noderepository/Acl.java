// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.net.InetAddress;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class represents an ACL for a specific container instance.
 *
 * @author mpolden
 * @author smorgrav
 */
public class Acl {

    private final List<InetAddress> trustedNodes;
    private final List<Integer> trustedPorts;

    /**
     * @param trustedPorts Ports that hostname should trust
     * @param trustedNodes Other hostnames that this hostname should trust
     */
    public Acl(List<Integer> trustedPorts, List<InetAddress> trustedNodes) {
        this.trustedNodes = trustedNodes != null ? ImmutableList.copyOf(trustedNodes) : Collections.emptyList();
        this.trustedPorts = trustedPorts != null ? ImmutableList.copyOf(trustedPorts) : Collections.emptyList();
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
        String commaSeparatedPorts = trustedPorts.stream().map(i -> Integer.toString(i)).collect(Collectors.joining(","));
        if (!commaSeparatedPorts.isEmpty())
            rules.add("-A INPUT -p tcp -m multiport --dports " + commaSeparatedPorts + " -j ACCEPT");

        // Allow traffic from trusted nodes
        trustedNodes.stream()
                .filter(ipVersion::match)
                .map(ipAddress -> "-A INPUT -s " + InetAddresses.toAddrString(ipAddress) + ipVersion.singleHostCidr() + " -j ACCEPT")
                .forEach(rules::add);

        // We reject instead of dropping to give us an easier time to figure out potential network issues
        rules.add("-A INPUT -j REJECT --reject-with " + ipVersion.icmpPortUnreachable());

        return Collections.unmodifiableList(rules);
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
    public int hashCode() {
        return Objects.hash(trustedPorts, trustedNodes);
    }
}
