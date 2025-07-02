// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_strategy_id_notifier.h"

namespace proton::flushengine {

FlushStrategyIdNotifier::FlushStrategyIdNotifier(uint32_t strategy_id)
    : _lock(),
      _cond(),
      _strategy_id(strategy_id),
      _closed(false)
{
}

FlushStrategyIdNotifier::~FlushStrategyIdNotifier() = default;

void
FlushStrategyIdNotifier::set_strategy_id(uint32_t strategy_id)
{
    std::lock_guard guard(_lock);
    if (strategy_id > _strategy_id) {
        _strategy_id = strategy_id;
        _cond.notify_all();
    }
}

void
FlushStrategyIdNotifier::close()
{
    std::lock_guard guard(_lock);
    _closed = true;
    _cond.notify_all();
}

void
FlushStrategyIdNotifier::wait_ge_strategy_id(uint32_t strategy_id)
{
    std::unique_lock guard(_lock);
    while (_strategy_id < strategy_id && !_closed) {
        _cond.wait(guard);
    }
}

}
