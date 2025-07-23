// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "flush_history_entry.h"
#include "flush_strategy_history_entry.h"
#include <deque>
#include <map>
#include <memory>
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
    const uint32_t                           _strategy_id_base;
    const uint32_t                           _max_concurrent_normal;
    uint32_t                                 _pending_id;

    /*
     * History of flushes.
     *
     * For a priority flush strategy, a flush history entry starts at _pending, moves to _active when it is scheduled,
     * and later _finished when the flush has completed. The oldest entries in _finished can be removed due to pruning.
     *
     * For a normal flush strategy, a flush history entry starts at _active since selection of new flush targets is
     * deferred to when a new flush can be scheduled.
     */
    std::deque<FlushHistoryEntry>            _finished;
    std::map<uint32_t, FlushHistoryEntry>    _active;
    std::map<std::string, FlushHistoryEntry> _pending;

    /*
     * History of flush strategies.
     *
     * A flush strategy history entry starts at _active_strategy. When a new flush strategy is activated, the
     * flush strategy history entry for the deactivated flush strategy is copied to both _draining_strategies and
     * _last_strategies (overwriting any previous entry in _last_strategies with same name). The entries in
     * _draining_strategies that don't have active flushes are moved to _finished_strategies.
     * The oldest entries in _finished_strategies can be removed due to pruning.
     *
     * Currently, the flush history does not reflect the queued flush strategies.
     */
    std::deque<FlushStrategyHistoryEntry>            _finished_strategies;
    std::map<uint32_t, FlushStrategyHistoryEntry>    _draining_strategies; // inactive flush strategies with active flushes
    FlushStrategyHistoryEntry                        _active_strategy;
    std::map<std::string, FlushStrategyHistoryEntry> _last_strategies; // last inactive flush strategy for each name

    std::string build_name(const std::string& handler_name, const std::string& target_name);

    void prune_finished();
    void prune_finished_strategies();
    void prune_draining_strategies(time_point now);
    void strategy_flush_done(uint32_t strategy_id, time_point now);
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
    void prune_done(uint32_t id);
    void add_pending_flush(const std::string& handler_name, const std::string& target_name,
                           duration last_flush_duration);
    void drop_pending_flush(const std::string& handler_name, const std::string& target_name);
    void clear_pending_flushes();
    void set_strategy(std::string stategy, uint32_t strategy_id, bool priority_strategy);
    std::shared_ptr<const FlushHistoryView> make_view() const;
};

}
