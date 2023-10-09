// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <memory>

namespace mbus {

/**
 * When a {@link com.yahoo.messagebus.Reply} containing errors is returned to a {@link
 * com.yahoo.messagebus.MessageBus}, an object implementing this interface is consulted on whether or not to
 * resend the corresponding {@link com.yahoo.messagebus.Message}. The policy is passed to the message bus at
 * creation time using the {@link com.yahoo.messagebus.MessageBusParams#setRetryPolicy(RetryPolicy::UP)}
 * method.
 */
class IRetryPolicy {
public:

    using SP = std::shared_ptr<IRetryPolicy>;
    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IRetryPolicy() = default;

    /**
     * Returns whether or not a {@link com.yahoo.messagebus.Reply} containing an {@link
     * com.yahoo.messagebus.Error} with the given error code can be retried. This method is invoked once for
     * each error in a reply.
     *
     * @param errorCode The code to check.
     * @return True if the message can be resent.
     */
    virtual bool canRetry(uint32_t errorCode) const = 0;

    /**
     * Returns the number of seconds to delay resending a message.
     *
     * @param retry The retry attempt.
     * @return The delay in seconds.
     */
    virtual double getRetryDelay(uint32_t retry) const = 0;
};

} // namespace mbus

