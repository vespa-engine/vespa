// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.documentapi.metrics.DocumentProtocolMetricSet;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.log.LogLevel;

import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Simon Thoresen Hult
 */
class RoutingPolicyRepository {

    private static final Logger log = Logger.getLogger(RoutingPolicyRepository.class.getName());
    private final Map<String, RoutingPolicyFactory> factories = new ConcurrentHashMap<String, RoutingPolicyFactory>();
    private final DocumentProtocolMetricSet metrics;

    RoutingPolicyRepository(DocumentProtocolMetricSet metrics) {
        this.metrics = metrics;
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
    RoutingPolicyFactory getFactory(String name) {
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
            log.log(LogLevel.ERROR, "No routing policy factory found for name '" + name + "'.");
            return null;
        }
        final DocumentProtocolRoutingPolicy ret = factory.createPolicy(param);

        if (ret == null) {
            log.log(LogLevel.ERROR, "Routing policy factory " + factory.getClass().getName() + " failed to create a " +
                    "routing policy for parameter '" + name + "'.");
            return null;
        }

        if (ret.getMetrics() != null) {
            metrics.routingPolicyMetrics.addMetric(ret.getMetrics());
        }

        return ret;
    }
}
