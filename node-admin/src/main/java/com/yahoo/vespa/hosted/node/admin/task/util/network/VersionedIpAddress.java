package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.google.common.net.InetAddresses;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @author gjoranv
 */
public class VersionedIpAddress implements Comparable<VersionedIpAddress> {

    private final InetAddress address;
    private final IPVersion version;

    private VersionedIpAddress(InetAddress address) {
        this.address = address;
        version = getVersionOrThrow(address);
    }

    public IPVersion version() {
        return version;
    }

    public String asString() {
        return InetAddresses.toAddrString(address);
    }

    public static VersionedIpAddress from(InetAddress address) {
        return new VersionedIpAddress(address);
    }

    // TODO: remove?
    public static VersionedIpAddress from(String address) {
        return from(InetAddresses.forString(address));
    }

    private static IPVersion getVersionOrThrow(InetAddress address) {
        if (address instanceof Inet4Address) {
            return IPVersion.IPv4;
        } else if (address instanceof Inet6Address) {
            return IPVersion.IPv6;
        } else {
            throw new IllegalArgumentException("Unknown IP version for " + InetAddresses.toAddrString(address) + " of class " + address.getClass().getName());
        }
    }

    @Override
    public int compareTo(VersionedIpAddress o) {
        int version = version().compareTo(o.version());
        return (version != 0) ? version : asString().compareTo(o.asString());
    }

}
