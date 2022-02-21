// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iretrypolicy.h"
#include <vespa/messagebus/queue.h>
#include <vespa/messagebus/reply.h>
#include <mutex>
#include <queue>
#include <vector>

namespace mbus {

class RoutingNode;

/**
 * The resender handles scheduling and execution of sending instances of {@link
 * RoutingNode}. An instance of this class is owned by {@link
 * com.yahoo.messagebus.MessageBus}. Because this class does not have any
 * internal thread, it depends on message bus to keep polling it whenever it has
 * time.
 */
class Resender
{
private:
    using time_point = std::chrono::steady_clock::time_point;
    typedef std::pair<time_point , RoutingNode*> Entry;
    struct Cmp {
        bool operator()(const Entry &a, const Entry &b) {
            return (b.first < a.first);
        }
    };
    using PriorityQueue = std::priority_queue<Entry, std::vector<Entry>, Cmp>;

    std::mutex       _queue_mutex;
    PriorityQueue    _queue;
    IRetryPolicy::SP _retryPolicy;
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<Resender> UP;
    Resender(const Resender &) = delete;
    Resender & operator = (const Resender &) = delete;

    /**
     * Constructs a new resender.
     *
     * @param retryPolicy The retry policy to use.
     */
    explicit Resender(IRetryPolicy::SP retryPolicy);

    /**
     * Empties the retry queue.
     */
    ~Resender();

    /**
     * Returns whether or not the current {@link RetryPolicy} supports resending
     * a {@link Reply} that contains an error with the given error code.
     *
     * @param errorCode The code to check.
     * @return True if the message can be resent.
     */
    [[nodiscard]] bool canRetry(uint32_t errorCode) const;

    /**
     * Returns whether or not the given reply should be retried.
     *
     * @param reply The reply to check.
     * @return True if retry is required.
     */
    [[nodiscard]] bool shouldRetry(const Reply &reply) const;

    /**
     * Schedules the given node for resending, if enabled by message. This will
     * invoke {@link RoutingNode#prepareForRetry()} if the node was queued. This
     * method is NOT thread-safe, and should only be called by the messenger
     * thread.
     *
     * @param node The node to resend.
     * @return True if the node was queued.
     */
    [[nodiscard]] bool scheduleRetry(RoutingNode &node);

    /**
     * Invokes {@link RoutingNode#send()} on all routing nodes that are
     * applicable for sending at the current time.
     */
    void resendScheduled();
};

} // namespace mbus

