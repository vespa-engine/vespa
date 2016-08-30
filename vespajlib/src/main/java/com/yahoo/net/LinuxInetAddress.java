// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utilities for returning localhost addresses on Linux.
 * See
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037
 * on why this is necessary.
 *
 * @author bratseth
 */
// TODO: Remove on vespa 7
public class LinuxInetAddress {

    /**
     * Returns an InetAddress representing the address of the localhost.
     * A non-loopback address is preferred if available.
     * An address that resolves to a hostname is preferred among non-loopback addresses.
     * IPv4 is preferred over IPv6 among resolving addresses.
     *
     * @return a localhost address
     * @deprecated use {@link HostName} instead
     */
    // Note: Checking resolvability of ipV6 addresses takes a long time on some systems (over 5 seconds 
    // for some addresses on my mac). This method is written to minimize the number of resolution checks done
    // and to defer ip6 checks until necessary.
    @Deprecated
    public static InetAddress getLocalHost() {
        InetAddress fallback = InetAddress.getLoopbackAddress();
        try {
            fallback = InetAddress.getLocalHost();
            List<InetAddress> nonLoopback =
                getAllLocalFromNetwork().stream().filter(a -> ! a.isLoopbackAddress()).collect(Collectors.toList());
            if (nonLoopback.isEmpty()) return fallback;
            
            // Invariant: We got all addresses without exception

            List<InetAddress> ipV4 = nonLoopback.stream().filter(a -> a instanceof Inet4Address).collect(Collectors.toList());
            for (InetAddress address : ipV4)
                if (doesResolve(address))
                    return address;

            // Invariant: There are no resolving ip4 addresses
            
            List<InetAddress> ipV6 = nonLoopback.stream().filter(a -> a instanceof Inet4Address).collect(Collectors.toList());
            for (InetAddress address : ipV6)
                if (doesResolve(address))
                    return address;

            // Invariant: There are no resolving ip6 addresses either

            if (! ipV4.isEmpty()) return ipV4.get(0);
            return ipV6.get(0);
        } catch (UnknownHostException e) {
            return fallback;
        }
    }

    /**
     * Returns all local addresses of this host.
     *
     * @return an array of the addresses of this
     * @throws UnknownHostException if we cannot access the network
     * @deprecated do not use
     */
    @Deprecated
    public static InetAddress[] getAllLocal() throws UnknownHostException {
        InetAddress[] localInetAddresses = InetAddress.getAllByName("127.0.0.1");
        if ( ! localInetAddresses[0].isLoopbackAddress()) return localInetAddresses;
        return getAllLocalFromNetwork().toArray(new InetAddress[0]);
    }

    /**
     * Returns all local addresses of this host.
     *
     * @return a list of the addresses of this
     * @throws UnknownHostException if we cannot access the network
     */
    private static List<InetAddress> getAllLocalFromNetwork() throws UnknownHostException {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces()))
                addresses.addAll(Collections.list(networkInterface.getInetAddresses()));
            return addresses;
        }
        catch (SocketException ex) {
            throw new UnknownHostException("127.0.0.1");
        }
    }

    private static boolean doesResolve(InetAddress address) {
        // The latter returns a name if resolvable to one and the host address otherwise
        return ! address.getHostAddress().equals(address.getCanonicalHostName());
    }

}
