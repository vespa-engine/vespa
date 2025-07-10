// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_strategy_id_listener.h"
#include "flush_strategy_id_notifier.h"
#include <cassert>

namespace proton::flushengine {

FlushStrategyIdListener::FlushStrategyIdListener(std::shared_ptr<FlushStrategyIdNotifier> notifier)
    : _notifier(std::move(notifier)),
      _strategy_id_listener_removed(false)
{
}

FlushStrategyIdListener::~FlushStrategyIdListener() = default;

bool
FlushStrategyIdListener::add_to_notifier(std::shared_ptr<FlushStrategyIdListener> self)
{
    assert(this == self.get());
    auto notifier = _notifier.lock();
    return notifier ? notifier->add_strategy_id_listener(std::move(self)) : false;
}

void
FlushStrategyIdListener::remove_from_notifier(std::shared_ptr<FlushStrategyIdListener> self)
{
    assert(this == self.get());
    auto notifier = _notifier.lock();
    if (notifier) {
        notifier->remove_strategy_id_listener(std::move(self));
    }
}

}
