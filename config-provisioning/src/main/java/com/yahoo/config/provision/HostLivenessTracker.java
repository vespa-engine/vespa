// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.Instant;
import java.util.Optional;

/**
 * Instances of this are used to keep track of (notify and query)
 * which hosts are currently connected to the config system.
 * 
 * @author bratseth
 */
public interface HostLivenessTracker {

    /** Called each time a config request is received from a client */
    void receivedRequestFrom(String hostname);

    /** 
     * Returns the epoch timestamp of the last request received from the given hostname,
     * or empty if there is no memory of this host making a request
     */
    Optional<Instant> lastRequestFrom(String hostname);

}
