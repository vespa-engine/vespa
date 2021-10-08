// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.google.common.net.InetAddresses;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strong type IPv4 and IPv6 with common executables for ip related commands.
 *
 * @author smorgrav
 */
public enum IPVersion {

    IPv6(6, "ip6tables", "ip -6", "ipv6-icmp", 128, "icmp6-port-unreachable", "ip6tables-restore", "fe80::/10"),
    IPv4(4, "iptables", "ip", "icmp", 32, "icmp-port-unreachable", "iptables-restore", "169.254.0.0/16");

    private static final Pattern cidrNotationPattern = Pattern.compile("/\\d+$");

    IPVersion(int version, String iptablesCmd, String ipCmd,
              String icmpProtocol, int size, String icmpPortUnreachable,
              String iptablesRestore, String linkLocalCidr) {
        this.version = version;
        this.ipCmd = ipCmd;
        this.iptablesCmd = iptablesCmd;
        this.icmpProtocol = icmpProtocol;
        this.size = size;
        this.icmpPortUnreachable = icmpPortUnreachable;
        this.iptablesRestore = iptablesRestore;
        this.linkLocalCidr = linkLocalCidr;
    }

    private final int version;
    private final String iptablesCmd;
    private final String ipCmd;
    private final String icmpProtocol;
    private final int size;
    private final String icmpPortUnreachable;
    private final String iptablesRestore;
    private final String linkLocalCidr;

    /** The ID of the IP version, either IPv4 or IPv6. */
    public String id() { return "IPv" + version; }

    /** The IP version, either 4 or 6 */
    public int version() { return version; }

    public String versionString() { return String.valueOf(version); }
    public String iptablesCmd() { return iptablesCmd; }
    public String iptablesRestore() { return iptablesRestore;}
    public String ipCmd() { return ipCmd; }
    public String icmpProtocol() { return icmpProtocol; }
    public String singleHostCidr() { return "/" + size; }
    public String icmpPortUnreachable() { return icmpPortUnreachable; }

    /** The address size (in bits) of the IP version: 32 or 128. */
    public int addressSize() { return size; }

    /** Both IPv4 and IPv6 have exactly one link-local address space: 169.254.0.0/16 or fe80::/10. */
    public String linkLocalAddressCidr() { return linkLocalCidr; }

    public boolean match(InetAddress address) {
        return this == IPVersion.get(address);
    }

    public boolean match(String address) {
        return this == IPVersion.get(address);
    }

    public static IPVersion get(String address) {
        Matcher matcher = cidrNotationPattern.matcher(address);
        if (matcher.find()) {
            address = matcher.replaceFirst("");
        }
        return get(InetAddresses.forString(address));
    }

    public static IPVersion get(InetAddress address) {
        return address instanceof Inet4Address ? IPv4 : IPv6;
    }

}
