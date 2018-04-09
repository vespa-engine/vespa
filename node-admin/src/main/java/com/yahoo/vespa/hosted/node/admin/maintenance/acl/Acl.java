// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.AclSpec;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Action;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Chain;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Command;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.FilterCommand;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.PolicyCommand;

import java.net.Inet6Address;
import java.util.List;
import java.util.Objects;

/**
 * This class represents an ACL for a specific container instance
 *
 * @author mpolden
 */
public class Acl {

    private final int containerPid;
    private final List<AclSpec> aclSpecs;

    public Acl(int containerPid, List<AclSpec> aclSpecs) {
        this.containerPid = containerPid;
        this.aclSpecs = ImmutableList.copyOf(aclSpecs);
    }

    public List<Command> toCommands() {
        final ImmutableList.Builder<Command> commands = ImmutableList.builder();
        commands.add(
                // Default policies. Packets that do not match any rules will be processed according to policy.
                new PolicyCommand(Chain.INPUT, Action.DROP),
                new PolicyCommand(Chain.FORWARD, Action.DROP),
                new PolicyCommand(Chain.OUTPUT, Action.ACCEPT),

                // Allow packets belonging to established connections
                new FilterCommand(Chain.INPUT, Action.ACCEPT)
                        .withOption("-m", "state")
                        .withOption("--state", "RELATED,ESTABLISHED"),

                // Allow any loopback traffic
                new FilterCommand(Chain.INPUT, Action.ACCEPT)
                         .withOption("-i", "lo"),

                // Allow IPv6 ICMP packets. This is required for IPv6 routing (e.g. path MTU) to work correctly.
                new FilterCommand(Chain.INPUT, Action.ACCEPT)
                        .withOption("-p", "ipv6-icmp"));

        // Allow traffic from trusted containers
        aclSpecs.stream()
                .map(AclSpec::ipAddress)
                .filter(Acl::isIpv6)
                .map(ipAddress -> new FilterCommand(Chain.INPUT, Action.ACCEPT)
                        .withOption("-s", String.format("%s/128", ipAddress)))
                .forEach(commands::add);

        // Reject all other packets. This means that packets that would otherwise be processed according to policy, are
        // matched by the following rule.
        //
        // Ideally, we want to set the INPUT policy to REJECT and get rid of this rule, but unfortunately REJECT is not
        // a valid policy action.
        commands.add(new FilterCommand(Chain.INPUT, Action.REJECT));

        return commands.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Acl that = (Acl) o;
        return containerPid == that.containerPid &&
                Objects.equals(aclSpecs, that.aclSpecs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerPid, aclSpecs);
    }

    private static boolean isIpv6(String ipAddress) {
        return InetAddresses.forString(ipAddress) instanceof Inet6Address;
    }
}
