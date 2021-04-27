// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage::distributor {

/**
 * A tickable stripe is the minimal binding glue between the stripe's worker thread and
 * the actual implementation. Primarily allows for easier testing without having to
 * fake an entire actual DistributorStripe.
 */
class TickableStripe {
public:
    virtual ~TickableStripe() = default;

    // Perform a single operation tick of the stripe logic.
    // If function returns true, the caller should not perform any waiting before calling
    // tick() again. This generally means that the stripe is processing client operations
    // and wants to continue doing so as quickly as possible.
    // Only used for multi-threaded striped setups.
    // TODO return an enum indicating type of last processed event? E.g. external, maintenance, none, ...
    virtual bool tick() = 0;
};

}
