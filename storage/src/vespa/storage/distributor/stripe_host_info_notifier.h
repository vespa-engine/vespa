// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace storage::distributor {

/**
 * Used by stripes to signal that the distributor node should immediately respond to
 * any pending GetNodeState long-poll RPCs from the cluster controller. This is generally
 * done when a stripe has completed initializing or if all merging has completed for
 * a bucket space.
 *
 * Implementations of this interface may batch and/or throttle actual host info sends,
 * but shall attempt to send new host info within a reasonable amount of time (on the
 * order of seconds).
 */
class StripeHostInfoNotifier {
public:
    virtual ~StripeHostInfoNotifier() = default;
    virtual void notify_stripe_wants_to_send_host_info(uint16_t stripe_index) = 0;
};

}
