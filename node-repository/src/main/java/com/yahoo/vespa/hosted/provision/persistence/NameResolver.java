// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;

/**
 * Interface for a basic name to IP address resolver. Default implementation delegates to
 * {@link java.net.InetAddress#getAllByName(String)}.
 *
 * @author mpolden
 */
public interface NameResolver {

    /** Resolve IP addresses for given host name */
    default Set<String> getAllByNameOrThrow(String hostname) {
        try {
            ImmutableSet.Builder<String> ipAddresses = ImmutableSet.builder();
            Arrays.stream(InetAddress.getAllByName(hostname))
                    .map(InetAddress::getHostAddress)
                    .forEach(ipAddresses::add);
            return ipAddresses.build();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
