// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Interface for a basic name to IP address resolver. Default implementation delegates to
 * {@link java.net.InetAddress#getByName(String)}.
 *
 * @author mpolden
 */
public interface NameResolver {

    /** Resolve IP address from given host name */
    default String getByNameOrThrow(String hostname) {
        try {
            return InetAddress.getByName(hostname).getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
