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

public:
    /*
     * Flush counts for a flush strategy. The remaining active flushes when the flush strategy was created
     * is tracked in _inherited, with _inherited_finished being incremented when those flushes complete.
     */
    struct FlushCounts {
        uint32_t    _started;            // # flushes started by this flush strategy
        uint32_t    _finished;           // # flushes started by this flush strategy that have finished
        uint32_t    _inherited;          // # flushes started by an earlier flush strategy
        uint32_t    _inherited_finished; // # flushes started by an earlier flush strategy that have finished

        constexpr explicit FlushCounts(uint32_t inherited) noexcept
            : FlushCounts(0, 0, inherited, 0)
        {
        }
        constexpr FlushCounts(uint32_t started, uint32_t finished, uint32_t inherited, uint32_t inherited_finished) noexcept
            : _started(started),
              _finished(finished),
              _inherited(inherited),
              _inherited_finished(inherited_finished)
        {
        }
        bool has_active_flushes() const noexcept { return _started > _finished || _inherited > _inherited_finished; }
        bool operator==(const FlushCounts& rhs) const noexcept = default;
    };
private:
    std::string _name;
    uint32_t    _id;
    bool        _priority_strategy;
    time_point  _start_time;
    time_point  _switch_time;
    time_point  _finish_time;
    time_point  _last_flush_finish_time;
    FlushCounts _flush_counts;

public:
    FlushStrategyHistoryEntry(std::string name_in, uint32_t id_in,bool priority_strategy_in,
                              time_point start_time_in, uint32_t inherited_flushes_in);
    FlushStrategyHistoryEntry(const FlushStrategyHistoryEntry &);
    FlushStrategyHistoryEntry(FlushStrategyHistoryEntry &&) noexcept;
    ~FlushStrategyHistoryEntry();
    FlushStrategyHistoryEntry& operator=(const FlushStrategyHistoryEntry &);
    FlushStrategyHistoryEntry& operator=(FlushStrategyHistoryEntry &&) noexcept;
    const std::string& name() const noexcept { return _name; }
    uint32_t id() const noexcept { return _id; }
    bool priority_strategy() const noexcept { return _priority_strategy; }
    time_point start_time() const noexcept { return _start_time; }
    time_point switch_time() const noexcept { return _switch_time; }
    time_point finish_time() const noexcept { return _finish_time; }
    time_point last_flush_finish_time() const noexcept { return _last_flush_finish_time; }
    const FlushCounts& flush_counts() const noexcept { return _flush_counts; }
    uint32_t started_flushes() const noexcept { return _flush_counts._started; }
    uint32_t finished_flushes() const noexcept { return _flush_counts._finished; }
    uint32_t inherited_started_flushes() const noexcept { return _flush_counts._inherited; }
    uint32_t inherited_finished_flushes() const noexcept { return _flush_counts._inherited_finished; }
    void set_switch_time(time_point switch_time_in) noexcept { _switch_time = switch_time_in; }
    void set_finish_time(time_point finish_time_in) noexcept { _finish_time = finish_time_in; }
    void start_flush() noexcept;
    void finish_flush(uint32_t strategy_id, time_point now) noexcept;
    bool has_active_flushes() const noexcept { return _flush_counts.has_active_flushes(); }
};

}
