// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_history.h"
#include "flush_history_view.h"
#include <algorithm>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.flush_history");

namespace proton::flushengine {

namespace {

template <typename Key, typename Value>
std::vector<Value>
make_value_vector(const std::map<Key, Value>& entries)
{
    std::vector<Value> result;
    result.reserve(entries.size());
    for (auto& entry : entries) {
        result.emplace_back(entry.second);
    }
    return result;
}

}

FlushHistory::FlushHistory(std::string strategy, uint32_t strategy_id, uint32_t max_concurrent_normal)
    : _mutex(),
      _keep_duration(std::chrono::minutes(5u)),
      _keep_entries(100),
      _keep_entries_max(10000),
      _strategy_id_base(strategy_id),
      _max_concurrent_normal(max_concurrent_normal),
      _pending_id(0),
      _finished(),
      _active(),
      _pending(),
      _finished_strategies(),
      _draining_strategies(),
      _active_strategy(strategy, strategy_id, false, steady_clock::now(), 0),
      _last_strategies()
{
}

FlushHistory::~FlushHistory() = default;

std::string
FlushHistory::build_name(const std::string& handler_name, const std::string& target_name)
{
    return handler_name + "." + target_name;
}

void
FlushHistory::prune_finished()
{
    auto now = steady_clock::now();
    auto it = _finished.begin();
    uint32_t scanned = 0;
    if (_finished.size() > _keep_entries_max) {
        auto skip = _finished.size() - _keep_entries_max;
        scanned += skip;
        it += skip;
    }
    for (; it != _finished.end() && scanned + _keep_entries < _finished.size(); ++it, ++scanned) {
        if (now - it->finish_time() <= _keep_duration) {
            break;
        }
    }
    _finished.erase(_finished.begin(), it);
}

void
FlushHistory::prune_finished_strategies()
{
    auto it = _finished_strategies.begin();
    uint32_t keep_strategy_entries = 10;
    uint32_t scanned = 0;
    for (; it != _finished_strategies.end() && scanned + keep_strategy_entries < _finished_strategies.size(); ++it, ++scanned) {
    }
    _finished_strategies.erase(_finished_strategies.begin(), it);
}

void
FlushHistory::prune_draining_strategies(time_point now)
{
    while (!_draining_strategies.empty()) {
        auto& strategy = _draining_strategies.begin()->second;
        if (strategy.has_active_flushes()) {
            break;
        }
        strategy.set_finish_time(now);
        if (strategy.priority_strategy()) {
            LOG(info, "Priority flush strategy %s id %u drained, %u flushes, %u inherited flushes",
                strategy.name().c_str(), strategy.id(),
                strategy.finished_flushes(), strategy.inherited_finished_flushes());
        }
        _finished_strategies.push_back(std::move(strategy));
        _draining_strategies.erase(_draining_strategies.begin());
    }
}

void
FlushHistory::strategy_flush_done(uint32_t strategy_id, time_point now)
{
    auto it = _draining_strategies.find(strategy_id);
    if (it != _draining_strategies.end()) {
        for (; it != _draining_strategies.end(); ++it) {
            it->second.finish_flush(strategy_id, now);
        }
        for (auto& last_strategy : _last_strategies) {
            last_strategy.second.finish_flush(strategy_id, now);
        }
    }
    _active_strategy.finish_flush(strategy_id, now);
    prune_draining_strategies(now);
    prune_finished_strategies();
}

void
FlushHistory::start_flush(const std::string& handler_name, const std::string& target_name, duration last_flush_duration, uint32_t id)
{
    // Note: this member function is called when queueing flush engine task, initFlush has already completed.
    auto name = build_name(handler_name, target_name);
    std::lock_guard guard(_mutex);
    auto active_it = _active.lower_bound(id);
    assert(active_it == _active.end() || active_it->first != id);
    auto pending_it = _pending.lower_bound(name);
    if (pending_it != _pending.end() && pending_it->first == name) {
        active_it = _active.emplace_hint(active_it, id, pending_it->second);
        _pending.erase(pending_it);
    } else {
        auto now = steady_clock::now();
        active_it = _active.emplace_hint(active_it, id, FlushHistoryEntry(name, _active_strategy, now,
                                                                          last_flush_duration, ++_pending_id));
    }
    _active_strategy.start_flush();
    auto now = steady_clock::now();
    active_it->second.start_flush(now, id);
}

void
FlushHistory::flush_done(uint32_t id)
{
    // Note: flush is still considered actuve after flush done, until prune is done.
    std::lock_guard guard(_mutex);
    auto it = _active.lower_bound(id);
    assert(it != _active.end() && it->first == id);
    auto now = steady_clock::now();
    it->second.flush_done(now);
}

void
FlushHistory::prune_done(uint32_t id)
{
    std::lock_guard guard(_mutex);
    auto it = _active.lower_bound(id);
    assert(it != _active.end() && it->first == id);
    auto strategy_id = it->second.strategy_id();
    _finished.emplace_back(it->second);
    auto now = steady_clock::now();
    _finished.back().prune_done(now);
    _active.erase(it);
    prune_finished();
    strategy_flush_done(strategy_id, now);
}

void
FlushHistory::add_pending_flush(const std::string& handler_name, const std::string& target_name,
                                duration last_flush_duration)
{
    // Called when priority flush strategy is used.
    auto name = build_name(handler_name, target_name);
    std::lock_guard guard(_mutex);
    auto pending_it = _pending.lower_bound(name);
    auto now = steady_clock::now();
    if (pending_it != _pending.end() && pending_it->first == name) {
        pending_it->second = FlushHistoryEntry(name, _active_strategy, now, last_flush_duration, ++_pending_id);
    } else {
        _pending.emplace_hint(pending_it, name, FlushHistoryEntry(name, _active_strategy, now,
                                                                  last_flush_duration, ++_pending_id));
    }
}

void
FlushHistory::drop_pending_flush(const std::string& handler_name, const std::string& target_name)
{
    // Called when initFlush() for a flush target failed to return a valid task and priority flush strategy is used.
    auto name = build_name(handler_name, target_name);
    std::lock_guard guard(_mutex);
    auto pending_it = _pending.lower_bound(name);
    if (pending_it != _pending.end() && pending_it->first == name) {
        _pending.erase(pending_it);
    }
}

void
FlushHistory::clear_pending_flushes()
{
    std::lock_guard guard(_mutex);
    _pending.clear();
}

void
FlushHistory::set_strategy(std::string strategy, uint32_t strategy_id, bool priority_strategy)
{
    std::lock_guard guard(_mutex);
    auto now = steady_clock::now();
    _active_strategy.set_switch_time(now);
    auto it = _last_strategies.lower_bound(_active_strategy.name());
    if (it != _last_strategies.end() && it->first == _active_strategy.name()) {
        it->second = _active_strategy;
    } else {
        _last_strategies.emplace_hint(it, _active_strategy.name(), _active_strategy);
    }
    _draining_strategies.emplace_hint(_draining_strategies.end(), _active_strategy.id(), _active_strategy);
    _active_strategy = FlushStrategyHistoryEntry(strategy, strategy_id, priority_strategy, now, _active.size());
    prune_draining_strategies(now);
    prune_finished_strategies();
}

std::shared_ptr<const FlushHistoryView>
FlushHistory::make_view() const
{
    std::unique_lock guard(_mutex);
    auto strategy_id_base_copy = _strategy_id_base;
    std::vector<FlushHistoryEntry> finished_copy(_finished.begin(), _finished.end());
    auto active_copy = make_value_vector(_active);
    auto pending_copy = make_value_vector(_pending);
    std::vector<FlushStrategyHistoryEntry> finished_strategies_copy(_finished_strategies.begin(),
                                                                    _finished_strategies.end());
    auto draining_strategies_copy = make_value_vector(_draining_strategies);
    auto active_strategy_copy = _active_strategy;
    auto last_strategies_copy = make_value_vector(_last_strategies);
    guard.unlock();
    std::sort(pending_copy.begin(), pending_copy.end(), [](const auto& lhs, const auto& rhs) { return lhs.id() < rhs.id(); });
    return std::make_shared<FlushHistoryView>(strategy_id_base_copy,
                                              _max_concurrent_normal,
                                              std::move(finished_copy),
                                              std::move(active_copy),
                                              std::move(pending_copy),
                                              std::move(finished_strategies_copy),
                                              std::move(draining_strategies_copy),
                                              std::move(active_strategy_copy),
                                              std::move(last_strategies_copy));
}

}
