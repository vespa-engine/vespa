// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.net.InetAddress;
import java.util.ArrayList;

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

    public Acl(List<Integer> trustedPorts, List<InetAddress> trustedNodes) {
        this.trustedNodes = trustedNodes != null ? ImmutableList.copyOf(trustedNodes) : new ArrayList<>();
        this.trustedPorts = trustedPorts != null ? ImmutableList.copyOf(trustedPorts) : new ArrayList<>();
    }

    public List<InetAddress> trustedNodes() {
        return trustedNodes;
    }

    public List<Integer> trustedPorts() {
        return trustedPorts;
    }

    public String toRestoreCommand(InetAddress containerAddress) {
        return String.join("\n"
                , "*filter"
                , toListRules(containerAddress)
                , "COMMIT\n");
    }

    public String toListRules(InetAddress containerAddress) {
        IPVersion ipVersion = IPVersion.get(containerAddress);

        String basics = String.join("\n"
                // We reject with rules instead of using policies
                , "-P INPUT ACCEPT"
                , "-P FORWARD ACCEPT"
                , "-P OUTPUT ACCEPT"
                // Allow packets belonging to established connections
                , "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT"
                // Allow any loopback traffic
                , "-A INPUT -i lo -j ACCEPT"
                // Allow ICMP packets. See http://shouldiblockicmp.com/
                , "-A INPUT -p " + ipVersion.icmpProtocol() + " -j ACCEPT");

        // Allow trusted ports
        String ports = trustedPorts.stream()
                .map(port -> "-A INPUT -p tcp --dport " + port + " -j ACCEPT")
                .collect(Collectors.joining("\n"));

        // Allow traffic from trusted nodes
        String nodes = trustedNodes.stream()
                .filter(ipVersion::match)
                .map(ipAddress -> "-A INPUT -s " + InetAddresses.toAddrString(ipAddress) + ipVersion.singleHostCidr() + " -j ACCEPT")
                .collect(Collectors.joining("\n"));

        String rejectEverythingElse = "-A INPUT -j REJECT";

        // Redirect calls to itself to loopback interface (this to avoid socket collision on bridged networks)
        String redirectSelf = "-A OUTPUT -d " + InetAddresses.toAddrString(containerAddress) + " -j REDIRECT";

        return String.join("\n", basics, ports, nodes, rejectEverythingElse, redirectSelf);
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
