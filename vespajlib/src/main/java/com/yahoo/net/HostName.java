// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import ai.vespa.http.DomainName;
import ai.vespa.validation.PatternedStringWrapper;

import java.util.Optional;
import java.util.regex.Pattern;

import static ai.vespa.validation.Validation.requireLength;

/**
 * Hostnames match {@link #domainNamePattern}, and are restricted to 64 characters in length.
 *
 * This class also has utilities for getting the hostname of the system running the JVM.
 * Detection of the hostname is now done before starting any Vespa
 * programs and provided in the environment variable VESPA_HOSTNAME;
 * if that variable isn't set a default of "localhost" is always returned.
 *
 * @author arnej
 * @author jonmv
 */
public class HostName extends DomainName {

    private static HostName preferredHostName = null;

    private HostName(String value) {
        super(requireLength(value, "hostname length", 1, 64));
    }

    public static HostName of(String value) {
        return new HostName(value);
    }

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
        return preferredHostName.value();
    }

    static private HostName getPreferredHostName() {
        Optional<String> vespaHostEnv = Optional.ofNullable(System.getenv("VESPA_HOSTNAME"));
        if (vespaHostEnv.isPresent() && ! vespaHostEnv.get().trim().isEmpty()) {
            return of(vespaHostEnv.get().trim());
        }
        return of("localhost");
    }

    public static void setHostNameForTestingOnly(String hostName) {
        preferredHostName = HostName.of(hostName);
    }

}
