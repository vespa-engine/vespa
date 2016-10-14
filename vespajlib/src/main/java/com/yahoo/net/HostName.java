// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utilities for getting the hostname of the system running the JVM.
 *
 * @author lulf
 * @author bratseth
 * @author hakon
 */
public class HostName {

    private static final Logger logger = Logger.getLogger(HostName.class.getName());

    private static String cachedHostName = null;

    /**
     * Return a fully qualified hostname that resolves to an IP address on a network interface.
     * Normally this is the same as the 'hostname' command, but on dev machines on WiFi,
     * that IP isn't configured so we prefer a WiFi network interface IP address which is both reachable and
     * has a DNS entry.
     *
     * @return the preferred name of localhost
     * @throws RuntimeException if accessing the network or the 'hostname' command fails
     */
    public static synchronized String getHostName() {
        if (cachedHostName == null) {
            try {
                cachedHostName = getPreferredAddress().canonicalHostName;
            } catch (Exception e) {
                throw new RuntimeException("Failed to find a preferred hostname", e);
            }
        }
        return cachedHostName;
    }

    private static Address getPreferredAddress() throws Exception {
        List<Address> addresses = getReachableNetworkInterfaceAddresses();

        // Prefer address matching the system hostname
        String systemHostName = getSystemHostName();
        List<Address> systemAddresses = addresses.stream()
                .filter(address -> Objects.equals(address.canonicalHostName, systemHostName))
                .collect(Collectors.toList());
        if (systemAddresses.size() >= 1) {
            return systemAddresses.iterator().next();
        }

        // Otherwise, prefer non-local address.
        List<Address> nonLocalAddresses = addresses.stream()
                .filter(address -> !address.ipAddress.isAnyLocalAddress())
                .collect(Collectors.toList());
        if (nonLocalAddresses.size() >= 1) {
            return nonLocalAddresses.iterator().next();
        }

        // Otherwise, pick a local address.
        List<Address> localAddresses = addresses.stream()
                .filter(address -> address.ipAddress.isAnyLocalAddress())
                .collect(Collectors.toList());
        if (localAddresses.size() >= 1) {
            return localAddresses.iterator().next();
        }

        throw new RuntimeException("Failed to find any addresses on the network interfaces that resolves to a DNS name");
    }

    // public for testing purposes (all testing machines should have a hostname
    public static String getSystemHostName() throws Exception {
        Process process = Runtime.getRuntime().exec("hostname");
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String hostname = in.readLine();
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command 'hostname' failed with exit code " + process.exitValue());
        }

        return hostname;
    }

    private static class Address {

        public final InetAddress ipAddress;
        public final String canonicalHostName;

        public Address(InetAddress ipAddress, String canonicalHostName) {
            this.ipAddress = ipAddress;
            this.canonicalHostName = canonicalHostName;
        }

    }

    private static List<Address> getReachableNetworkInterfaceAddresses() throws SocketException {
        List<Address> addresses = new ArrayList<>();

        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress ipAddress : Collections.list(networkInterface.getInetAddresses())) {
                String hostname = ipAddress.getCanonicalHostName();
                if (Objects.equals(hostname, ipAddress.getHostAddress())) {
                    // getCanonicalHostName() failed to get the fully qualified domain name
                    continue;
                }

                try {
                    // ping says ~50ms on my Fedora Lenovo, but that seems a lot for pinging oneself  - hakon
                    int timeoutMs = 100;
                    if ( ! ipAddress.isReachable(timeoutMs)) {
                        // The network interface may be down, ignore address
                        logger.log(Level.INFO, ipAddress.toString() + 
                                               " is unreachable w/" + timeoutMs + "ms timeout, ignoring address");
                        continue;
                    }
                } catch (IOException e) {
                    // Why would this be different from !isReachable ?
                    logger.log(Level.INFO, "Failed testing reachability of " + ipAddress + ", ignoring address", e);
                    continue;
                }

                addresses.add(new Address(ipAddress, hostname));
            }
        }

        return addresses;
    }

}
