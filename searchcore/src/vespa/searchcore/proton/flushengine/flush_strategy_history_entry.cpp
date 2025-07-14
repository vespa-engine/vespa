// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_strategy_history_entry.h"

namespace proton::flushengine {

FlushStrategyHistoryEntry::FlushStrategyHistoryEntry(std::string name_in, uint32_t id_in,
                                     bool priority_strategy_in, time_point start_time_in,
                                     uint32_t inherited_flushes_in)
    : _name(std::move(name_in)),
      _id(id_in),
      _priority_strategy(priority_strategy_in),
      _start_time(start_time_in),
      _switch_time(),
      _finish_time(),
      _last_flush_finish_time(),
      _flush_counts(inherited_flushes_in)
{
}

FlushStrategyHistoryEntry::FlushStrategyHistoryEntry(const FlushStrategyHistoryEntry &) = default;
FlushStrategyHistoryEntry::FlushStrategyHistoryEntry(FlushStrategyHistoryEntry &&) noexcept = default;

FlushStrategyHistoryEntry::~FlushStrategyHistoryEntry() = default;

FlushStrategyHistoryEntry& FlushStrategyHistoryEntry::operator=(const FlushStrategyHistoryEntry &) = default;
FlushStrategyHistoryEntry& FlushStrategyHistoryEntry::operator=(FlushStrategyHistoryEntry &&) noexcept = default;

void
FlushStrategyHistoryEntry::start_flush() noexcept
{
    ++_flush_counts._started;
}

void
FlushStrategyHistoryEntry::finish_flush(uint32_t strategy_id, time_point now) noexcept
{
    if (strategy_id < _id) {
        ++_flush_counts._inherited_finished;
        _last_flush_finish_time = now;
    } else if (strategy_id == _id) {
        ++_flush_counts._finished;
        _last_flush_finish_time = now;
    }
}

}
