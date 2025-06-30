// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_strategy_history_entry.h"

namespace proton::flushengine {

FlushStrategyHistoryEntry::FlushStrategyHistoryEntry(std::string name_in, uint32_t id_in,
                                     bool priority_strategy_in, time_point start_time_in, time_point finish_time_in)
    : _name(std::move(name_in)),
      _id(id_in),
      _priority_strategy(priority_strategy_in),
      _start_time(start_time_in),
      _finish_time(finish_time_in)
{
}

FlushStrategyHistoryEntry::FlushStrategyHistoryEntry(const FlushStrategyHistoryEntry &) = default;
FlushStrategyHistoryEntry::FlushStrategyHistoryEntry(FlushStrategyHistoryEntry &&) noexcept = default;

FlushStrategyHistoryEntry::~FlushStrategyHistoryEntry() = default;

FlushStrategyHistoryEntry& FlushStrategyHistoryEntry::operator=(const FlushStrategyHistoryEntry &) = default;
FlushStrategyHistoryEntry& FlushStrategyHistoryEntry::operator=(FlushStrategyHistoryEntry &&) noexcept = default;

}
