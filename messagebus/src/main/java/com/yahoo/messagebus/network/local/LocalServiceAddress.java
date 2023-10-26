// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.local;

import com.yahoo.messagebus.network.ServiceAddress;

/**
 * @author Simon Thoresen Hult
 */
public class LocalServiceAddress implements ServiceAddress {

    private final LocalNetwork network;
    private final String sessionName;

    public LocalServiceAddress(final String serviceName, final LocalNetwork network) {
        this.network = network;
        this.sessionName = serviceName.substring(serviceName.lastIndexOf('/') + 1);
    }

    public LocalNetwork getNetwork() {
        return network;
    }

    public String getSessionName() {
        return sessionName;
    }
}
