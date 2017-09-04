// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import java.util.Optional;
import java.util.Set;

/**
 * Interface for a basic name to IP address resolver.
 *
 * @author mpolden
 */
public interface NameResolver {

    /**
     * Get IP addresses for given host name
     *
     * @param hostname The hostname to resolve
     * @return A set of IPv4 or IPv6 addresses
     */
    Set<String> getAllByNameOrThrow(String hostname);

    /**
     * Get hostname from IP address
     *
     * @param ipAddress The IPv4 or IPv6 address for the host
     * @return Empty if the IP does not resolve or the hostname if it does
     */
    Optional<String> getHostname(String ipAddress);

}
