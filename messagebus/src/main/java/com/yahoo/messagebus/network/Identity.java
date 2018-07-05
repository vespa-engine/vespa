// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

import com.yahoo.net.HostName;

/**
 * This class encapsulates the identity of the application that uses this instance of message bus. This identity
 * contains a servicePrefix identifier, which is the configuration id of the current servicePrefix, and the canonical
 * host name of the host running this.
 *
 * @author Simon Thoresen Hult
 */
public class Identity {

    private final String hostname;
    private final String servicePrefix;

    /**
     * The default constructor requires a configuration identifier that it will use to subscribe to this message bus'
     * identity. This identity is necessary so that the network layer is able to identify self for registration with
     * Slobrok.
     *
     * @param configId The config identifier for the application.
     */
    public Identity(String configId) {
        hostname = HostName.getLocalhost(); // ... but fallback to hostname if we get an IPv6 address
        servicePrefix = configId;
    }

    /**
     * Implements the copy constructor.
     *
     * @param identity The object to copy.
     */
    public Identity(Identity identity) {
        hostname = identity.hostname;
        servicePrefix = identity.servicePrefix;
    }

    /**
     * Returns the hostname for this. This is the network name of the host on which this identity exists. It is
     * retrieved on creation by running shell command "hostname".
     *
     * @return The canonical host name.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Returns the service prefix for this. This is what is prefixed to every session that is created on this identity's
     * message bus before registered in the naming service.
     *
     * @return The service prefix.
     */
    public String getServicePrefix() {
        return servicePrefix;
    }

}
