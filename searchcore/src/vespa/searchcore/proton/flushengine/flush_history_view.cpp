// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_history_view.h"
#include <vespa/vespalib/util/priority_queue.h>
#include <algorithm>

namespace proton::flushengine {

FlushHistoryView::FlushHistoryView(uint32_t strategy_id_base_in,
                                   uint32_t max_concurrent_normal_in,
                                   std::vector<FlushHistoryEntry> finished_in,
                                   std::vector<FlushHistoryEntry> active_in,
                                   std::vector<FlushHistoryEntry> pending_in,
                                   std::vector<FlushStrategyHistoryEntry> finished_strategies_in,
                                   std::vector<FlushStrategyHistoryEntry> draining_strategies_in,
                                   FlushStrategyHistoryEntry active_strategy_in,
                                   std::vector<FlushStrategyHistoryEntry> last_strategies_in)
    : _strategy_id_base(strategy_id_base_in),
      _max_concurrent_normal(max_concurrent_normal_in),
      _finished(std::move(finished_in)),
      _active(std::move(active_in)),
      _pending(std::move(pending_in)),
      _finished_strategies(std::move(finished_strategies_in)),
      _draining_strategies(std::move(draining_strategies_in)),
      _active_strategy(std::move(active_strategy_in)),
      _last_strategies(std::move(last_strategies_in))
{
}

FlushHistoryView::FlushHistoryView(const FlushHistoryView&) = default;
FlushHistoryView::FlushHistoryView(FlushHistoryView&&) noexcept = default;

FlushHistoryView::~FlushHistoryView() = default;

FlushHistoryView& FlushHistoryView::operator=(const FlushHistoryView&) = default;
FlushHistoryView& FlushHistoryView::operator=(FlushHistoryView&&) noexcept = default;

FlushHistoryView::time_point
FlushHistoryView::estimated_flush_complete_time(time_point now) const
{
    vespalib::PriorityQueue<time_point> complete;
    for (auto& active : _active) {
        // Add estimated flush complete time for active flush thread
        complete.push(std::max(now, active.start_time() + active.last_flush_duration()));
    }
    while (complete.size() < _max_concurrent_normal) {
        // Idle flush threads can start new flushes now
        complete.push(now);
    }
    // Estimate flush complete times as pending flushes are handled
    for (auto& pending : _pending) {
        complete.front() += pending.last_flush_duration();
        complete.adjust();
    }
    while (complete.size() > 1) {
        complete.pop_front();
    }
    return complete.front();
}

}
