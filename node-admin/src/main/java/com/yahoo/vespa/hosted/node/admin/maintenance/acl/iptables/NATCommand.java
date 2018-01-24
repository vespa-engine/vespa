// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Creates three ip(6)tables commands customized to map docker containers on a private net to their
 * respective public ip addresses that all are assign to the host
 *
 *  1. DNAT PRE-ROUTING: replaces an external/public destination ip to an internal/private ip when it arrives on an interface
 *  2. DNAT LOOPBACK OUTPUT: replaces an internal/public ip destination to an internal/private ip on the loopback device (for host to container communication)
 *  3. SNAT POST-ROUTING: replaces an internal/private source ip to an external/public ip before writing it on the wire
 *
 * @author smorgrav
 */
public class NATCommand implements Command {

    private final String snatCommand;
    private final String dnatCommand;
    private final String dnatLoopBackCommand;

    public NATCommand(InetAddress externalIp, InetAddress internalIp, String chainCommand) {
        String command = externalIp instanceof Inet6Address ? "ip6tables" : "iptables";
        this.snatCommand = String.format("%s -t nat %s POSTROUTING -s %s -j SNAT --to %s",
                command,
                chainCommand,
                internalIp.getHostAddress(),
                externalIp.getHostAddress());
        this.dnatLoopBackCommand = String.format("%s -t nat %s OUTPUT -o lo -d %s -j DNAT --to-destination %s",
                command,
                chainCommand,
                externalIp.getHostAddress(),
                internalIp.getHostAddress());
        this.dnatCommand = String.format("%s -t nat %s PREROUTING -d %s -j DNAT --to-destination %s",
                command,
                chainCommand,
                externalIp.getHostAddress(),
                internalIp.getHostAddress());
    }

    @Override
    public String asString() {
        return concat("&&");
    }

    @Override
    public String asString(String commandName) { return asString(); }

    private String concat(String delimiter) {
        return String.join(delimiter, snatCommand, dnatCommand, dnatLoopBackCommand);
    }

    public static String insert(InetAddress externalIp, InetAddress internalIp) {
        return new NATCommand(externalIp, internalIp, "-I").concat(" && ");
    }

    public static String drop(InetAddress externalIp, InetAddress internalIp) {
        return new NATCommand(externalIp, internalIp, "-D").concat("; ");
    }

    public static String check(InetAddress externalIp, InetAddress internalIp) {
        return new NATCommand(externalIp, internalIp, "-C").concat(" && ");
    }
}
