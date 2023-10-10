// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iprotocol.h"
#include <map>
#include <atomic>
#include <mutex>

namespace mbus {

/**
 * Implements a thread-safe repository for protocols and their routing policies. This manages an internal cache of
 * routing policies so that similarly referenced policy directives share the same instance of a policy.
 * However for speed the protocols themselves must be kept alive on the outside when returned from
 * putProtocol. There is only room for a limited number of protocols.
 */
class ProtocolRepository {
private:
    using ProtocolMap = std::map<string, IProtocol::SP>;
    using RoutingPolicyCache = std::map<string, IRoutingPolicy::SP>;

    std::mutex     _lock; // Only guards the cache,
                              // not the protocols as they are set up during messagebus construction.
    static constexpr size_t MAX_PROTOCOLS = 16;
    std::pair<string, std::atomic<IProtocol *>> _protocols[MAX_PROTOCOLS];
    std::atomic<size_t>                         _numProtocols;
    ProtocolMap        _activeProtocols;
    RoutingPolicyCache _routingPolicyCache;

public:
    ProtocolRepository(const ProtocolRepository &) = delete;
    ProtocolRepository & operator = (const ProtocolRepository &) = delete;
    ProtocolRepository();
    ~ProtocolRepository();
    /**
     * Registers a protocol with this repository. This will overwrite any protocol that was registered earlier
     * that has the same name. If this method detects a protocol replacement, it will clear its internal
     * routing policy cache. You must keep the old protocol returned until there can be no usages of the references
     * acquired from getProtocol.
     *
     * Must not be called concurrently by multiple threads.
     *
     * @param protocol The protocol to register.
     * @return The previous protocol registered under this name.
     */
    IProtocol::SP putProtocol(const IProtocol::SP & protocol);

    /**
     * Returns the protocol whose name matches the given argument. This method will return null if no such
     * protocol has been registered.
     *
     * @param name The name of the protocol to return.
     * @return The protocol registered, or null.
     */
    IProtocol * getProtocol(const string &name);

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
