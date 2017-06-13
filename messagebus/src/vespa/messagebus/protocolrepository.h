// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/vespalib/util/sync.h>
#include "iprotocol.h"

namespace mbus {

/**
 * Implements a thread-safe repository for protocols and their routing policies. This manages an internal cache of
 * routing policies so that similarly referenced policy directives share the same instance of a policy.
 */
class ProtocolRepository {
private:
    typedef std::map<string, IProtocol::SP> ProtocolMap;
    typedef std::map<string, IRoutingPolicy::SP> RoutingPolicyCache;

    vespalib::Lock     _lock;
    ProtocolMap        _protocols;
    RoutingPolicyCache _routingPolicyCache;

public:
    ProtocolRepository(const ProtocolRepository &) = delete;
    ProtocolRepository & operator = (const ProtocolRepository &) = delete;
    ProtocolRepository();
    ~ProtocolRepository();
    /**
     * Registers a protocol with this repository. This will overwrite any protocol that was registered earlier
     * that has the same name. If this method detects a protocol replacement, it will clear its internal
     * routing policy cache.
     *
     * @param protocol The protocol to register.
     * @return The previous protocol registered under this name.
     */
    IProtocol::SP putProtocol(const IProtocol::SP & protocol);

    /**
     * Returns whether or not this repository contains a protocol with the given name. Given the concurrent
     * nature of things, one should not invoke this method followed by {@link #getProtocol(String)} and expect
     * the return value to be non-null. Instead just get the protocol and compare it to null.
     *
     * @param name The name to check for.
     * @return True if the named protocol is registered.
     */
    bool hasProtocol(const string &name) const;

    /**
     * Returns the protocol whose name matches the given argument. This method will return null if no such
     * protocol has been registered.
     *
     * @param name The name of the protocol to return.
     * @return The protocol registered, or null.
     */
    IProtocol::SP getProtocol(const string &name);

    /**
     * Creates and returns a routing policy that matches the given arguments. If a routing policy has been
     * created previously using the exact same parameters, this method will returned that cached instance
     * instead of creating another. Not that when you replace a protocol using {@link #putProtocol(Protocol)}
     * the policy cache is cleared.
     *
     * @param protocolName The name of the protocol whose routing policy to create.
     * @param policyName   The name of the routing policy to create.
     * @param policyParam  The parameter to pass to the routing policy constructor.
     * @return The created routing policy.
     */
    IRoutingPolicy::SP getRoutingPolicy(const string &protocolName,
                                        const string &policyName,
                                        const string &policyParam);

    /**
     * Clears the internal cache of routing policies.
     */
    void clearPolicyCache();
};

}

