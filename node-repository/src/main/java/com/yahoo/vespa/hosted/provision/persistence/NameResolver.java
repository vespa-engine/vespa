// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.log.LogLevel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * Interface for a basic name to IP address resolver. Default implementation delegates to
 * {@link java.net.InetAddress#getByName(String)}.
 *
 * @author mpolden
 */
public interface NameResolver {

    Logger log = Logger.getLogger(NameResolver.class.getName());

    /** Resolve IP address from given host name */
    default String getByNameOrThrow(String hostname) {
        try {
            return InetAddress.getByName(hostname).getHostAddress();
        } catch (UnknownHostException e) {
            log.log(LogLevel.ERROR, String.format("Failed to resolve hostname %s: %s", hostname, e.getMessage()));
            throw new RuntimeException(e);
        }
    }

}
