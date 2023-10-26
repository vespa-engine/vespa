// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ithrottlepolicy.h"

namespace mbus {

/**
 * This is an implementatin of the {@link ThrottlePolicy} that offers static limits to the amount of pending
 * data a {@link SourceSession} is allowed to have. You may choose to set a limit to the total number of
 * pending messages (by way of {@link #setMaxPendingCount(int)}), the total size of pending messages (by way
 * of {@link #setMaxPendingSize(long)}), or some combination thereof.
 *
 * <b>NOTE:</b> By context, "pending" is refering to the number of sent messages that have not been replied to
 * yet.
 */
class StaticThrottlePolicy: public IThrottlePolicy {
private:
    uint32_t _maxPendingCount;
    uint64_t _maxPendingSize;
    uint64_t _pendingSize;

public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<StaticThrottlePolicy>;
    using SP = std::shared_ptr<StaticThrottlePolicy>;

    /**
     * Constructs a new instance of this policy and sets the appropriate default values of member data.
     */
    StaticThrottlePolicy();

    /**
     * Returns the maximum number of pending messages allowed.
     *
     * @return The max limit.
     */
    uint32_t getMaxPendingCount() const;

    /**
     * Sets the maximum number of pending messages allowed.
     *
     * @param maxCount The max count.
     * @return This, to allow chaining.
     */
    StaticThrottlePolicy &setMaxPendingCount(uint32_t maxCount);

    /**
     * Returns the maximum total size of pending messages allowed.
     *
     * @return The max limit.
     */
    uint64_t getMaxPendingSize() const;

    /**
     * Sets the maximum total size of pending messages allowed. This size is relative to the value returned by
     * {@link com.yahoo.messagebus.Message#getApproxSize()}.
     *
     * @param maxSize The max size.
     * @return This, to allow chaining.
     */
    StaticThrottlePolicy &setMaxPendingSize(uint64_t maxSize);

    /**
     * Returns the total size of pending messages.
     *
     * @return The size.
     */
    uint64_t getPendingSize() const;

    bool canSend(const Message &msg, uint32_t pendingCount) override;
    void processMessage(Message &msg) override;
    void processReply(Reply &reply) override;
};

} // namespace mbus

