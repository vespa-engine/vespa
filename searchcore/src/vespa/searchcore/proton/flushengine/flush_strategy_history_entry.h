// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <string>

namespace proton::flushengine {

/*
 * A recent flush strategy that can be shown in the state explorer.
 */
class FlushStrategyHistoryEntry {
    using steady_clock = std::chrono::steady_clock;
    using time_point = steady_clock::time_point;
    using duration = steady_clock::duration;

    std::string _name;
    uint32_t    _id;
    bool        _priority_strategy;
    time_point  _start_time;
    time_point  _finish_time;

public:
    FlushStrategyHistoryEntry(std::string name_in, uint32_t id_in,bool priority_strategy_in,
                              time_point start_time_in, time_point finish_time_in);
    FlushStrategyHistoryEntry(const FlushStrategyHistoryEntry &);
    FlushStrategyHistoryEntry(FlushStrategyHistoryEntry &&) noexcept;
    ~FlushStrategyHistoryEntry();
    FlushStrategyHistoryEntry& operator=(const FlushStrategyHistoryEntry &);
    FlushStrategyHistoryEntry& operator=(FlushStrategyHistoryEntry &&) noexcept;
    const std::string& name() const noexcept { return _name; }
    uint32_t id() const noexcept { return _id; }
    bool priority_strategy() const noexcept { return _priority_strategy; }
    time_point start_time() const noexcept { return _start_time; }
    time_point finish_time() const noexcept { return _finish_time; }
};

}
