// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Creates two commands that:
 *
 *  1. replaces an external/public destination ip to an internal/private ip before routing it (pre-routing)
 *  2. replaces an internal/private source ip to an external/public ip before writing it on the wire (post-routing)
 *
 * @author smorgrav
 */
public class NATCommand implements Command {

    private final String snatCommand;
    private final String dnatCommand;

    public NATCommand(InetAddress externalIp, InetAddress internalIp, String iface) {
        String command = externalIp instanceof Inet6Address ? "ip6tables" : "iptables";
        this.snatCommand = String.format("%s -t nat -A POSTROUTING -o %s -s %s -j SNAT --to %s",
                command,
                iface,
                internalIp.getHostAddress(),
                externalIp.getHostAddress());

        this.dnatCommand = String.format("%s -t nat -A PREROUTING -i %s -d %s -j DNAT --to-destination %s",
                command,
                iface,
                externalIp.getHostAddress(),
                internalIp.getHostAddress());
    }

    @Override
    public String asString() {
        return snatCommand + "; " + dnatCommand;
    }

    @Override
    public String asString(String commandName) { return asString(); }

    public static String create(InetAddress externalIp, InetAddress internalIp, String iface) {
        return new NATCommand(externalIp, internalIp, iface).asString();
    }
}
