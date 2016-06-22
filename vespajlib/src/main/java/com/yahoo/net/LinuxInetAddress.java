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
public class LinuxInetAddress {

    /**
     * Returns an InetAddress representing the address of the localhost.
     * A non-loopback address is preferred if available.
     * An address that resolves to a hostname is preferred among non-loopback addresses.
     * IPv4 is preferred over IPv6 among resolving addresses.
     *
     * @return a localhost address
     */
    public static InetAddress getLocalHost() {
        InetAddress fallback = InetAddress.getLoopbackAddress();
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            fallback = localAddress;
            List<InetAddress> nonLoopback =
                getAllLocalFromNetwork().stream().filter(a -> ! a.isLoopbackAddress()).collect(Collectors.toList());
            if (nonLoopback.isEmpty()) {
                return fallback;
            }
            fallback = nonLoopback.get(0);
            List<InetAddress> resolving =
                    nonLoopback.stream().filter(a -> doesResolve(a)).collect(Collectors.toList());
            if (resolving.isEmpty()) {
                return fallback;
            }
            fallback = resolving.get(0);
            List<InetAddress> ipV4 =
                    resolving.stream().filter(a -> a instanceof Inet4Address).collect(Collectors.toList());
            if (ipV4.isEmpty()) {
                return fallback;
            }
            return ipV4.get(0);
        } catch (UnknownHostException e) {
            return fallback;
        }
    }

    /**
     * Returns all local addresses of this host.
     *
     * @return an array of the addresses of this
     * @throws UnknownHostException if we cannot access the network
     */
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

    private static boolean doesResolve(InetAddress addr) {
        String asAddr = addr.getHostAddress();
        String asName = addr.getCanonicalHostName();
        return ! asAddr.equals(asName);
    }

}
