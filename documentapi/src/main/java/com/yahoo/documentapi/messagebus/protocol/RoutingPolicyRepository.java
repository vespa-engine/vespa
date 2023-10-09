// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.messagebus.routing.RoutingPolicy;
import java.util.logging.Level;

import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Simon Thoresen Hult
 */
class RoutingPolicyRepository {

    private static final Logger log = Logger.getLogger(RoutingPolicyRepository.class.getName());
    private final Map<String, RoutingPolicyFactory> factories = new ConcurrentHashMap<String, RoutingPolicyFactory>();

    RoutingPolicyRepository() {
    }

    /**
     * Registers a routing policy factory for a given name.
     *
     * @param name    The name of the factory to register.
     * @param factory The factory to register.
     */
    void putFactory(String name, RoutingPolicyFactory factory) {
        factories.put(name, factory);
    }

    /**
     * Returns the routing policy factory for a given name.
     *
     * @param name The name of the factory to return.
     * @return The routing policy factory matching the criteria, or null.
     */
    private RoutingPolicyFactory getFactory(String name) {
        return factories.get(name);
    }

    /**
     * Creates and returns a routing policy using the named factory and the given parameter.
     *
     * @param name  The name of the factory to use.
     * @param param The parameter to pass to the factory.
     * @return The created policy.
     */
    RoutingPolicy createPolicy(String name, String param) {
        RoutingPolicyFactory factory = getFactory(name);
        if (factory == null) {
            log.log(Level.SEVERE, "No routing policy factory found for name '" + name + "'.");
            return null;
        }
        DocumentProtocolRoutingPolicy ret = factory.createPolicy(param);

        if (ret == null) {
            log.log(Level.SEVERE, "Routing policy factory " + factory.getClass().getName() + " failed to create a " +
                    "routing policy for parameter '" + name + "'.");
            return null;
        }

        return ret;
    }
}
