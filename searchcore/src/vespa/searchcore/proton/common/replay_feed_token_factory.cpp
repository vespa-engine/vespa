// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "replay_feed_token_factory.h"
#include "replay_feedtoken_state.h"
#include <cassert>

namespace proton::feedtoken {

class ReplayFeedTokenFactory::Deleter {
    ReplayFeedTokenFactory& _factory;
public:
    Deleter(ReplayFeedTokenFactory& factory)
        : _factory(factory)
    {
    }
    void operator()(ReplayState* p) const noexcept {
        _factory.on_delete(p);
        delete p;
    }
};

ReplayFeedTokenFactory::ReplayFeedTokenFactory(bool enable_tracking)
    : _lock(),
      _states(),
      _enable_tracking(enable_tracking)
{
}

ReplayFeedTokenFactory::~ReplayFeedTokenFactory()
{
    std::lock_guard guard(_lock);
    assert(_states.empty());
}

FeedToken
ReplayFeedTokenFactory::make_replay_feed_token(ThrottlerToken throttler_token, const FeedOperation& op)
{
    if (_enable_tracking) {
        auto token = std::make_unique<ReplayState>(std::move(throttler_token), op);
        std::lock_guard guard(_lock);
        bool inserted = _states.insert(token.get()).second;
        assert(inserted);
        return std::shared_ptr<ReplayState>(token.release(), Deleter(*this));
    } else {
        return std::make_shared<ReplayState>(std::move(throttler_token), op);
    }
}

void
ReplayFeedTokenFactory::on_delete(const ReplayState* state) noexcept
{
    std::lock_guard guard(_lock);
    bool erased = _states.erase(state) > 0u;
    assert(erased);
}

}
