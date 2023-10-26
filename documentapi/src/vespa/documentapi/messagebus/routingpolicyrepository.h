// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iroutingpolicyfactory.h"
#include <map>
#include <mutex>

namespace documentapi {

class RoutingPolicyRepository {
private:
    using FactoryMap = std::map<string, IRoutingPolicyFactory::SP>;

    mutable std::mutex  _lock;
    FactoryMap          _factories;

public:
    RoutingPolicyRepository(const RoutingPolicyRepository &) = delete;
    RoutingPolicyRepository & operator = (const RoutingPolicyRepository &) = delete;
    /**
     * Constructs a new routing policy repository.
     */
    RoutingPolicyRepository();

    /**
     * Registers a routing policy factory for a given name.
     *
     * @param name    The name of the factory to register.
     * @param factory The factory to register.
     */
    void putFactory(const string &name, IRoutingPolicyFactory::SP factory);

    /**
     * Returns the routing policy factory for a given name.
     *
     * @param name The name of the factory to return.
     * @return The routing policy factory matching the criteria, or null.
     */
    IRoutingPolicyFactory::SP getFactory(const string &name) const;

    /**
     * Creates and returns a routing policy using the named factory and the given parameter.
     *
     * @param name  The name of the factory to use.
     * @param param The parameter to pass to the factory.
     * @return The craeted policy.
     */
    mbus::IRoutingPolicy::UP createPolicy(const string &name, const string &param) const;
};

}

