// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/shared_operation_throttler.h>
#include "feedtoken.h"
#include <mutex>
#include <unordered_set>

namespace proton { class FeedOperation; }

namespace proton::feedtoken {

class ReplayState;

/*
 * A factory for replay feed tokens with optional tracking.
 */
class ReplayFeedTokenFactory {
    using ThrottlerToken = vespalib::SharedOperationThrottler::Token;
    class Deleter;

    std::mutex                             _lock;
    std::unordered_set<const ReplayState*> _states;
    bool                                   _enable_tracking;
    void on_delete(const ReplayState* state) noexcept;
public:
    ReplayFeedTokenFactory(bool enable_tracking);
    ~ReplayFeedTokenFactory();
    FeedToken make_replay_feed_token(ThrottlerToken throttler_token, const FeedOperation& op);
};

}
