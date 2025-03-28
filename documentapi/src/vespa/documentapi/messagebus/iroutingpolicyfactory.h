// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/documentapi/common.h>

namespace documentapi {

/**
 * This interface defines the necessary methods of a routing policy factory that can be plugged into a {@link
 * DocumentProtocol} using the {@link DocumentProtocol#putRoutingPolicyFactory(String, RoutingPolicyFactory)}
 * method.
 */
class IRoutingPolicyFactory {
public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<IRoutingPolicyFactory>;
    using SP = std::shared_ptr<IRoutingPolicyFactory>;

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IRoutingPolicyFactory() = default;

    /**
     * This method creates and returns a routing policy that corresponds to the implementing class, using the
     * given parameter string. There is only ever one instance of a routing policy for a given name and
     * parameter combination, and because of this the policies must be state-less beyond what can be derived
     * from the parameter string. Because there is only a single thread running route resolution within
     * message bus, it is not necessary to make policies thread-safe. For more information see {@link
     * RoutingPolicy}.
     *
     * Do NOT throw exceptions out of this method because that will cause the running thread to die, just
     * return null to signal failure instead.
     *
     * @param param The parameter to use when creating the policy.
     * @return The created routing policy.
     */
    virtual mbus::IRoutingPolicy::UP createPolicy(const string &param) const = 0;
};

}

