// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_history_entry.h"

namespace proton::flushengine {

FlushHistoryEntry::FlushHistoryEntry(std::string name_in, std::string strategy_in, uint32_t strategy_id_in,
                                     bool priority_strategy_in, time_point create_time_in,
                                     duration last_flush_duration_in, uint32_t id_in)
    : _name(std::move(name_in)),
      _strategy(std::move(strategy_in)),
      _strategy_id(strategy_id_in),
      _priority_strategy(priority_strategy_in),
      _create_time(create_time_in),
      _start_time(),
      _finish_time(),
      _prune_time(),
      _last_flush_duration(last_flush_duration_in),
      _id(id_in)
{

}

FlushHistoryEntry::FlushHistoryEntry(const FlushHistoryEntry &) = default;
FlushHistoryEntry::FlushHistoryEntry(FlushHistoryEntry &&) noexcept = default;

FlushHistoryEntry::~FlushHistoryEntry() = default;

FlushHistoryEntry& FlushHistoryEntry::operator=(const FlushHistoryEntry &) = default;
FlushHistoryEntry& FlushHistoryEntry::operator=(FlushHistoryEntry &&) noexcept = default;

void
FlushHistoryEntry::start_flush(time_point start_time_in, uint32_t id_in) noexcept
{
    _start_time = start_time_in;
    _id = id_in;
}

void
FlushHistoryEntry::flush_done(time_point finish_time_in) noexcept
{
    _finish_time = finish_time_in;
}

void
FlushHistoryEntry::prune_done(time_point prune_time_in) noexcept
{
    _prune_time = prune_time_in;
}

}
