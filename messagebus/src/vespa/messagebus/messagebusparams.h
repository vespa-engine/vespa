// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iprotocol.h"
#include <vespa/messagebus/routing/iretrypolicy.h>

namespace mbus {

class MessageBus;

/**
 * To facilitate several configuration parameters to the {@link MessageBus} constructor, all parameters are held by this
 * class. This class has reasonable default values for each parameter.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class MessageBusParams {
private:
    std::vector<IProtocol::SP> _protocols;
    IRetryPolicy::SP           _retryPolicy;
    uint32_t                   _maxPendingCount;
    uint32_t                   _maxPendingSize;

public:
    /**
     * Constructs a new instance of this parameter object with default values for all members.
     */
    MessageBusParams();
    ~MessageBusParams();

    /**
     * Returns the retry policy for the resender.
     *
     * @return The policy.
     */
    IRetryPolicy::SP getRetryPolicy() const { return _retryPolicy; }

    /**
     * Sets the retry policy for the resender.
     *
     * @param retryPolicy The policy to set.
     * @return This, to allow chaining.
     */
    MessageBusParams &setRetryPolicy(IRetryPolicy::SP retryPolicy) { _retryPolicy = retryPolicy; return *this; }

    /**
     * Registers a protocol under the name given by {@link com.yahoo.messagebus.Protocol#getName()}.
     *
     * @param protocol The protocol to register.
     * @return This, to allow chaining.
     */
    MessageBusParams &addProtocol(IProtocol::SP protocol);

    /**
     * Returns the number of protocols that are contained in this.
     *
     * @return The number of protocols.
     */
    uint32_t getNumProtocols() const;

    /**
     * Returns the protocol at the given index.
     *
     * @param i The index of the protocol to return.
     * @return The protocol object.
     */
    IProtocol::SP getProtocol(uint32_t i) const;

    /**
     * Returns the maximum number of pending messages.
     *
     * @return The count limit.
     */
    uint32_t getMaxPendingCount() const { return _maxPendingCount; }

    /**
     * Sets the maximum number of allowed pending messages.
     *
     * @param maxCount The count limit to set.
     * @return This, to allow chaining.
     */
    MessageBusParams &setMaxPendingCount(uint32_t maxCount) { _maxPendingCount = maxCount; return *this; }

    /**
     * Returns the maximum number of bytes allowed for pending messages.
     *
     * @return The size limit.
     */
    uint32_t getMaxPendingSize() const { return _maxPendingSize; }

    /**
     * Sets the maximum number of bytes allowed for pending messages.
     *
     * @param maxSize The size limit to set.
     * @return This, to allow chaining.
     */
    MessageBusParams &setMaxPendingSize(int maxSize) { _maxPendingSize = maxSize; return *this; }
};

} // namespace mbus

