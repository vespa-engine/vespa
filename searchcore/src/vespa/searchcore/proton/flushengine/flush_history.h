// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "flush_history_entry.h"
#include "flush_strategy_history_entry.h"
#include <deque>
#include <map>
#include <mutex>

namespace proton::flushengine {

class FlushHistoryView;

/*
 * Recent history of flush event, exposed through state explorer
 */
class FlushHistory {
    using steady_clock = std::chrono::steady_clock;
    using time_point = steady_clock::time_point;
    using duration = steady_clock::duration;

    mutable std::mutex                       _mutex;
    duration                                 _keep_duration;
    size_t                                   _keep_entries;
    size_t                                   _keep_entries_max; // limit at which _keep_duration is ignored
    std::string                              _strategy;
    const uint32_t                           _strategy_id_base;
    uint32_t                                 _strategy_id;
    bool                                     _priority_strategy;
    time_point                               _strategy_start_time;
    const uint32_t                           _max_concurrent_normal;
    uint32_t                                 _pending_id;
    std::deque<FlushHistoryEntry>            _finished;
    std::map<uint32_t, FlushHistoryEntry>    _active;
    std::map<std::string, FlushHistoryEntry> _pending;
    std::deque<FlushStrategyHistoryEntry>    _finished_strategies;
    std::map<std::string, FlushStrategyHistoryEntry> _last_strategies;

    std::string build_name(const std::string& handler_name, const std::string& target_name);

    void prune_finished();
    void prune_finished_strategies();
public:
    FlushHistory(std::string strategy, uint32_t stategy_id, uint32_t max_concurrent_normal);
    FlushHistory(const FlushHistory&) = delete;
    FlushHistory(FlushHistory&&) noexcept = delete;
    ~FlushHistory();
    FlushHistory& operator=(const FlushHistory&) = delete;
    FlushHistory& operator=(FlushHistory&&) = delete;
    void start_flush(const std::string& handler_name, const std::string& target_name,
                     duration last_flush_duration, uint32_t id);
    void flush_done(uint32_t id);
    void add_pending_flush(const std::string& handler_name, const std::string& target_name,
                           duration last_flush_duration);
    void drop_pending_flush(const std::string& handler_name, const std::string& target_name);
    void clear_pending_flushes();
    void set_strategy(std::string stategy, uint32_t strategy_id, bool priority_strategy);
    std::shared_ptr<const FlushHistoryView> make_view() const;
};

}
