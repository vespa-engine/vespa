// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "flush_history_entry.h"
#include "flush_strategy_history_entry.h"
#include <vector>

namespace proton::flushengine {

/*
 * Recent history of flush event, exposed through state explorer.
 */
class FlushHistoryView {
    using time_point = std::chrono::steady_clock::time_point;

    uint32_t                               _strategy_id_base;
    uint32_t                               _max_concurrent_normal;
    std::vector<FlushHistoryEntry>         _finished;
    std::vector<FlushHistoryEntry>         _active;
    std::vector<FlushHistoryEntry>         _pending;
    std::vector<FlushStrategyHistoryEntry> _finished_strategies;
    std::vector<FlushStrategyHistoryEntry> _draining_strategies; // strategies or strategy with active flush
    FlushStrategyHistoryEntry              _active_strategy;
    std::vector<FlushStrategyHistoryEntry> _last_strategies;
public:
    FlushHistoryView(uint32_t strategy_id_base_in,
                     uint32_t max_concurrent_normal_in,
                     std::vector<FlushHistoryEntry> finished_in,
                     std::vector<FlushHistoryEntry> active_in,
                     std::vector<FlushHistoryEntry> pending_in,
                     std::vector<FlushStrategyHistoryEntry> finished_strategies_in,
                     std::vector<FlushStrategyHistoryEntry> draining_strategies_in,
                     FlushStrategyHistoryEntry active_strategy_in,
                     std::vector<FlushStrategyHistoryEntry> last_strategies_in);
    FlushHistoryView(const FlushHistoryView&);
    FlushHistoryView(FlushHistoryView&&) noexcept;
    ~FlushHistoryView();
    FlushHistoryView& operator=(const FlushHistoryView&);
    FlushHistoryView& operator=(FlushHistoryView&&) noexcept;

    uint32_t strategy_id_base() const noexcept { return _strategy_id_base; }
    uint32_t max_concurrent_normal() const noexcept { return _max_concurrent_normal; }
    const std::vector<FlushHistoryEntry>& finished() const noexcept { return _finished; }
    const std::vector<FlushHistoryEntry>& active() const noexcept { return _active; }
    const std::vector<FlushHistoryEntry>& pending() const noexcept { return _pending; }
    const std::vector<FlushStrategyHistoryEntry>& finished_strategies() const noexcept { return _finished_strategies; }
    const std::vector<FlushStrategyHistoryEntry>& draining_strategies() const noexcept { return _draining_strategies; }
    const FlushStrategyHistoryEntry& active_strategy() const noexcept { return _active_strategy; }
    const std::vector<FlushStrategyHistoryEntry>& last_strategies() const noexcept { return _last_strategies; }
};

}
