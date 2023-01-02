// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import java.util.Objects;

/**
 * Address info about a container that might run on a host.
 *
 * @author hakon
 */
public record Address(String hostname) {
    public Address {
        Objects.requireNonNull(hostname, "hostname cannot be null");
        if (hostname.isEmpty())
            throw new IllegalArgumentException("hostname cannot be empty");
    }
}
