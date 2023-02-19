package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.google.common.net.InetAddresses;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;

/**
 * Encapsulates an IP address and its version along with some convenience methods.
 * Default sorting is by version (IPv6 first), then by address.
 *
 * @author gjoranv
 */
public class VersionedIpAddress implements Comparable<VersionedIpAddress> {

    private final InetAddress address;
    private final IPVersion version;

    private VersionedIpAddress(InetAddress address) {
        this.address = Objects.requireNonNull(address);
        version = getVersionOrThrow(address);
    }

    public static VersionedIpAddress from(InetAddress address) {
        return new VersionedIpAddress(address);
    }

    public static VersionedIpAddress from(String address) {
        return from(InetAddresses.forString(address));
    }

    public IPVersion version() {
        return version;
    }

    public String asString() {
        return InetAddresses.toAddrString(address);
    }

    public String asEndpoint(int port) {
        var format = (version == IPVersion.IPv6) ? "[%s]:%d" : "%s:%d";
        return String.format(format, asString(), port);
    }

    @Override
    public int compareTo(VersionedIpAddress o) {
        int version = version().compareTo(o.version());
        return (version != 0) ? version : asString().compareTo(o.asString());
    }

    @Override
    public String toString() {
        return "VersionedIpAddress{" +
                "address=" + address +
                ", version=" + version +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionedIpAddress that = (VersionedIpAddress) o;
        return address.equals(that.address) && version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, version);
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

}
