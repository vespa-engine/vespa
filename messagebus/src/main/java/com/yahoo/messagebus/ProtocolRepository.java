// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import java.util.logging.Level;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.text.Utf8String;

import java.util.logging.Logger;

/**
 * Implements a thread-safe repository for protocols and their routing policies. This manages an internal cache of
 * routing policies so that similarly referenced policy directives share the same instance of a policy.
 *
 * @author Simon Thoresen Hult
 */
public class ProtocolRepository {

    private static final Logger log = Logger.getLogger(ProtocolRepository.class.getName());
    private final CopyOnWriteHashMap<String, Protocol> protocols = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String, RoutingPolicy> routingPolicyCache = new CopyOnWriteHashMap<>();

    /**
     * Registers a protocol with this repository. This will overwrite any protocol that was registered earlier that has
     * the same name. If this method detects a protocol replacement, it will clear its internal routing policy cache.
     *
     * @param protocol The protocol to register.
     */
    public void putProtocol(Protocol protocol) {
        if (protocols.put(protocol.getName(), protocol) != null) {
            clearPolicyCache();
        }
    }

    /**
     * Returns whether or not this repository contains a protocol with the given name. Given the concurrent nature of
     * things, one should not invoke this method followed by {@link #getProtocol(String)} and expect the return value to
     * be non-null. Instead just get the protocol and compare it to null.
     *
     * @param name The name to check for.
     * @return True if the named protocol is registered.
     */
    public boolean hasProtocol(String name) {
        return protocols.containsKey(name);
    }

    /**
     * Returns the protocol whose name matches the given argument. This method will return null if no such protocol has
     * been registered.
     *
     * @param name The name of the protocol to return.
     * @return The protocol registered, or null.
     */
    public Protocol getProtocol(String name) {
        return protocols.get(name);
    }

    /**
     * Creates and returns a routing policy that matches the given arguments. If a routing policy has been created
     * previously using the exact same parameters, this method will returned that cached instance instead of creating
     * another. Not that when you replace a protocol using {@link #putProtocol(Protocol)} the policy cache is cleared.
     *
     * @param protocolName The name of the protocol whose routing policy to create.
     * @param policyName   The name of the routing policy to create.
     * @param policyParam  The parameter to pass to the routing policy constructor.
     * @return The created routing policy.
     */
    public RoutingPolicy getRoutingPolicy(String protocolName, String policyName, String policyParam) {
        String cacheKey = protocolName + "." + policyName + "." + policyParam;
        RoutingPolicy ret = routingPolicyCache.get(cacheKey);
        if (ret != null) {
            return ret;
        }
        synchronized (this) {
            ret = routingPolicyCache.get(cacheKey);
            if (ret != null) {
                return ret;
            }
            Protocol protocol = getProtocol(protocolName);
            if (protocol == null) {
                log.log(Level.SEVERE, "Protocol '" + protocolName + "' not supported.");
                return null;
            }
            try {
                ret = protocol.createPolicy(policyName, policyParam);
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Protocol '" + protocolName + "' threw an exception: " + e.getMessage(), e);
                return null;
            }
            if (ret == null) {
                log.log(Level.SEVERE, "Protocol '" + protocolName + "' failed to create routing policy '" + policyName +
                                        "' with parameter '" + policyParam + "'.");
                return null;
            }
            routingPolicyCache.put(cacheKey, ret);
        }
        return ret;
    }

    public final RoutingPolicy getRoutingPolicy(Utf8String protocolName, String policyName, String policyParam) {
        return getRoutingPolicy(protocolName.toString(), policyName, policyParam);
    }

    /**
     * Clears the internal cache of routing policies.
     */
    public synchronized void clearPolicyCache() {
        for (RoutingPolicy policy : routingPolicyCache.values()) {
            policy.destroy();
        }
        routingPolicyCache.clear();
    }
}
