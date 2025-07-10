// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_strategy_id_notifier.h"
#include "flush_strategy_id_listener.h"
#include <cassert>

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
    std::unique_lock guard(_lock);
    if (strategy_id > _strategy_id) {
        _strategy_id = strategy_id;
        _cond.notify_all();
        auto listeners = _listeners;
        guard.unlock();
        for (auto& listener : listeners) {
            listener->set_strategy_id(strategy_id);
            listener.reset();
        }
    }
}

void
FlushStrategyIdNotifier::close()
{
    std::unique_lock guard(_lock);
    _closed = true;
    _cond.notify_all();
    Listeners listeners;
    _listeners.swap(listeners);
    guard.unlock();
    for (auto& listener : listeners) {
        listener->notifier_closed();
        listener.reset();
    }
}

void
FlushStrategyIdNotifier::wait_gt_strategy_id(uint32_t strategy_id)
{
    std::unique_lock guard(_lock);
    while (_strategy_id <= strategy_id && !_closed) {
        _cond.wait(guard);
    }
}

bool
FlushStrategyIdNotifier::add_strategy_id_listener(std::shared_ptr<FlushStrategyIdListener> listener)
{
    std::unique_lock guard(_lock);
    if (_closed) {
        return false;
    }
    if (listener->strategy_id_listener_removed()) {
        return false;
    }
    auto it = std::find(_listeners.begin(), _listeners.end(), listener);
    assert(it == _listeners.end());
    _listeners.push_back(listener);
    return true;
}

void
FlushStrategyIdNotifier::remove_strategy_id_listener(std::shared_ptr<FlushStrategyIdListener> listener)
{
    std::unique_lock guard(_lock);
    listener->set_strategy_id_listener_removed();
    auto it = std::find(_listeners.begin(), _listeners.end(), listener);
    if (it != _listeners.end()) {
        _listeners.erase(it);
    }
}

}
