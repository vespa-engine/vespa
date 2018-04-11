// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.net.InetAddress;
import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public String toRestoreCommand(IPVersion ipVersion, Optional<InetAddress> redirectAddress) {
        return String.join("\n"
                , "*filter"
                , toListRules(ipVersion, redirectAddress)
                , "COMMIT\n");
    }

    public String toListRules(IPVersion ipVersion, Optional<InetAddress> redirectAddress) {

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
        String commaSeparatedPorts = trustedPorts.stream().map(i -> Integer.toString(i)).collect(Collectors.joining(","));
        String ports = "-A INPUT -p tcp -m multiport --dports " + commaSeparatedPorts + " -j ACCEPT";

        // Allow traffic from trusted nodes
        String nodes = trustedNodes.stream()
                .filter(ipVersion::match)
                .map(ipAddress -> "-A INPUT -s " + InetAddresses.toAddrString(ipAddress) + ipVersion.singleHostCidr() + " -j ACCEPT")
                .collect(Collectors.joining("\n"));

        // We reject instead of dropping to give us an easier time to figure out potential network issues
        String rejectEverythingElse = "-A INPUT -j REJECT";

        // Temporary result before the conditional redirect
        String result = String.join("\n", basics, ports, nodes, rejectEverythingElse);

        // Redirect calls to itself to loopback interface (this to avoid socket collision on bridged networks)
        if (redirectAddress.isPresent()) {
            result = String.join("\n", result,
                    "-A OUTPUT -d " + InetAddresses.toAddrString(redirectAddress.get()) + " -j REDIRECT");
        }

        return result;
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
