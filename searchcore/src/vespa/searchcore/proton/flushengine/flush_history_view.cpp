// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_history_view.h"

namespace proton::flushengine {

FlushHistoryView::FlushHistoryView(std::string strategy_in,
                                   uint32_t strategy_id_base_in,
                                   uint32_t strategy_id_in,
                                   bool priority_strategy_in,
                                   time_point strategy_start_time_in,
                                   uint32_t max_concurrent_normal_in,
                                   std::vector<FlushHistoryEntry> finished_in,
                                   std::vector<FlushHistoryEntry> active_in,
                                   std::vector<FlushHistoryEntry> pending_in,
                                   std::vector<FlushStrategyHistoryEntry> finished_strategies_in,
                                   std::vector<FlushStrategyHistoryEntry> last_strategies_in)
    : _strategy(std::move(strategy_in)),
      _strategy_id_base(strategy_id_base_in),
      _strategy_id(strategy_id_in),
      _priority_strategy(priority_strategy_in),
      _strategy_start_time(strategy_start_time_in),
      _max_concurrent_normal(max_concurrent_normal_in),
      _finished(std::move(finished_in)),
      _active(std::move(active_in)),
      _pending(std::move(pending_in)),
      _finished_strategies(std::move(finished_strategies_in)),
      _last_strategies(std::move(last_strategies_in))
{
}

FlushHistoryView::FlushHistoryView(const FlushHistoryView&) = default;
FlushHistoryView::FlushHistoryView(FlushHistoryView&&) noexcept = default;

FlushHistoryView::~FlushHistoryView() = default;

FlushHistoryView& FlushHistoryView::operator=(const FlushHistoryView&) = default;
FlushHistoryView& FlushHistoryView::operator=(FlushHistoryView&&) noexcept = default;

}
