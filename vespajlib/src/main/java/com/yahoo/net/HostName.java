// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.util.Optional;

/**
 * Utilities for getting the hostname of the system running the JVM.
 * Detection of the hostname is now done before starting any Vespa
 * programs and provided in the environment variable VESPA_HOSTNAME;
 * if that variable isn't set a default of "localhost" is always returned.
 *
 * @author arnej
 */
public class HostName {

    private static String preferredHostName = null;

    /**
     * Return a public and fully qualified hostname for localhost that
     * resolves to an IP address on a network interface.
     *
     * @return the preferred name of localhost
     */
    public static synchronized String getLocalhost() {
        if (preferredHostName == null) {
            preferredHostName = getPreferredHostName();
        }
        return preferredHostName;
    }

    static private String getPreferredHostName() {
        Optional<String> vespaHostEnv = Optional.ofNullable(System.getenv("VESPA_HOSTNAME"));
        if (vespaHostEnv.isPresent() && ! vespaHostEnv.get().trim().isEmpty()) {
            return vespaHostEnv.get().trim();
        }
        return "localhost";
    }

    public static void setHostNameForTestingOnly(String hostName) {
        preferredHostName = hostName;
    }
}
