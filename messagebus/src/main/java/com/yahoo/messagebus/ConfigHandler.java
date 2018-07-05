// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.routing.RoutingSpec;

/**
 * This class declares those methods required to be a handler for an instance of the {@link ConfigAgent} class.
 * Instead of declaring separate subscribers and handlers for all types of configurations, this pair is intended to hold
 * everything. Extend this handler whenever new configs are added to {@link ConfigAgent}.
 *
 * @author Simon Thoresen Hult
 */
public interface ConfigHandler {

    /**
     * Sets the routing specification for this client. This will be done synchronously during initialization, and then
     * subsequently whenever an updated configuration is available.
     *
     * @param spec The routing specification.
     */
    public void setupRouting(RoutingSpec spec);
}
