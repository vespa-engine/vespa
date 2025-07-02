// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <string>

namespace proton::flushengine {

/*
 * A recent flush operation that can be shown in the state explorer.
 */
class FlushHistoryEntry {
    using steady_clock = std::chrono::steady_clock;
    using time_point = steady_clock::time_point;
    using duration = steady_clock::duration;

    std::string _name;
    std::string _strategy;
    uint32_t    _strategy_id;
    bool        _priority_strategy;
    time_point  _create_time;
    time_point  _start_time;
    time_point  _finish_time;
    time_point  _prune_time;
    duration    _last_flush_duration;
    uint32_t    _id;

public:
    FlushHistoryEntry(std::string name_in, std::string strategy_in, uint32_t strategy_id_in,
                      bool priority_strategy_in, time_point create_time_in,
                      duration last_flush_duration_in, uint32_t id_in);
    FlushHistoryEntry(const FlushHistoryEntry &);
    FlushHistoryEntry(FlushHistoryEntry &&) noexcept;
    ~FlushHistoryEntry();
    FlushHistoryEntry& operator=(const FlushHistoryEntry &);
    FlushHistoryEntry& operator=(FlushHistoryEntry &&) noexcept;
    const std::string& name() const noexcept { return _name; }
    const std::string& strategy() const noexcept { return _strategy; }
    uint32_t strategy_id() const noexcept { return _strategy_id; }
    bool priority_strategy() const noexcept { return _priority_strategy; }
    time_point create_time() const noexcept { return _create_time; }
    time_point start_time() const noexcept { return _start_time; }
    time_point finish_time() const noexcept { return _finish_time; }
    time_point prune_time() const noexcept { return _prune_time; }
    duration flush_duration() const noexcept {
        return _finish_time != time_point() ? _finish_time - _start_time : duration();
    }
    duration last_flush_duration() const noexcept { return _last_flush_duration; }
    uint32_t id() const noexcept { return _id; }
    void start_flush(time_point start_time_in, uint32_t id_in) noexcept;
    void flush_done(time_point finish_time_in) noexcept;
    void prune_done(time_point prune_time_in) noexcept;
};

}
