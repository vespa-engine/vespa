// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.http.DomainName;
import com.google.common.net.InetAddresses;

import java.util.Objects;

/**
 * Represents a server behind a load balancer.
 *
 * @author mpolden
 */
public class Real implements Comparable<Real> {

    public static final int defaultPort = 4443;

    private final DomainName hostname;
    private final String ipAddress;
    private final int port;

    public Real(DomainName hostname, String ipAddress) {
        this(hostname, ipAddress, defaultPort);
    }

    public Real(DomainName hostname, String ipAddress, int port) {
        this.hostname = hostname;
        this.ipAddress = requireIpAddress(ipAddress);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port number must be >= 1 and <= 65535");
        }
        this.port = port;
    }

    /** The hostname of this real */
    public DomainName hostname() {
        return hostname;
    }

    /** Target IP address for this real */
    public String ipAddress() {
        return ipAddress;
    }

    /** Target port for this real */
    public int port() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Real real = (Real) o;
        return port == real.port &&
               Objects.equals(hostname, real.hostname) &&
               Objects.equals(ipAddress, real.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, ipAddress, port);
    }

    @Override
    public String toString() {
        return "real server " + hostname + " (" + ipAddress + ":" + port + ")";
    }

    private static String requireIpAddress(String ipAddress) {
        Objects.requireNonNull(ipAddress, "ipAddress must be non-null");
        try {
            InetAddresses.forString(ipAddress);
            return ipAddress;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ipAddress must be a valid IP address", e);
        }
    }

    @Override
    public int compareTo(Real that) {
        return hostname.compareTo(that.hostname());
    }

}
