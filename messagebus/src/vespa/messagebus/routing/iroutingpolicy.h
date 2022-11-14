// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace mbus {

class RoutingContext;

/**
 * A routing policy selects who should get a message and merges the replies that are returned from those
 * recipients. Note that recipient selection is done separately for each path element. This means that a routing policy
 * is not selecting recipients directly, but rather the set of strings that should be used for the next service name
 * path element. This process is done recursively until a set of actual service names are produced. The merging process
 * is similar, but in reverse order.
 */
class IRoutingPolicy {
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IRoutingPolicy> UP;
    typedef std::shared_ptr<IRoutingPolicy> SP;

    IRoutingPolicy(const IRoutingPolicy &) = delete;
    IRoutingPolicy & operator = (const IRoutingPolicy &) = delete;

    /**
     * Destructor. Frees any allocated resources.
     */
    virtual ~IRoutingPolicy() { }

    /**
     * This function must choose a set of services that is to receive the given message from a list of possible
     * recipients. This is done by adding child routing contexts to the argument object. These children can then be
     * iterated and manipulated even before selection pass is concluded.
     *
     * @param context The complete context for the invocation of this policy. Contains all available data.
     */
    virtual void select(RoutingContext &context) = 0;

    /**
     * This function is called when all replies have arrived for some message. The implementation is responsible for
     * merging multiple replies into a single sensible reply. The replies is contained in the child context objects of
     * the argument context, and then response must be set in that context.
     *
     * @param context The complete context for the invocation of this policy. Contains all available data.
     */
    virtual void merge(RoutingContext &context) = 0;

protected:
    IRoutingPolicy() = default;
};

} // namespace mbus

