// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_history_explorer.h"
#include "flush_history.h"
#include "flush_history_view.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <chrono>

using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vespalib::slime::ObjectInserter;
using vespalib::Memory;
using vespalib::StateExplorer;
using std::chrono::microseconds;
using std::chrono::steady_clock;
using std::chrono::system_clock;

namespace proton::flushengine {

namespace {

int64_t
as_system_microseconds(const steady_clock::time_point &time_point)
{
    // TODO: Use clock_cast
    auto system_now = system_clock::now();
    auto steady_now = steady_clock::now();
    return duration_cast<microseconds>(time_point.time_since_epoch() -
                                       steady_now.time_since_epoch() +
                                       system_now.time_since_epoch()).count();
}

void
convert_to_slime(const FlushHistoryEntry& entry, Inserter& inserter)
{
    Cursor &object = inserter.insertObject();
    object.setString("name", entry.name());
    object.setString("strategy", entry.strategy());
    object.setLong("strategy_id", entry.strategy_id());
    object.setBool("priority_strategy", entry.priority_strategy());
    object.setLong("create_time", as_system_microseconds(entry.create_time()));
    object.setLong("start_time", as_system_microseconds(entry.start_time()));
    object.setLong("finish_time", as_system_microseconds(entry.finish_time()));
    object.setLong("flush_duration", duration_cast<microseconds>(entry.flush_duration()).count());
    object.setLong("last_flush_duration", duration_cast<microseconds>(entry.last_flush_duration()).count());
    object.setLong("id", entry.id());
}

void
convert_to_slime(const FlushStrategyHistoryEntry& entry, Inserter& inserter)
{
    Cursor &object = inserter.insertObject();
    object.setString("strategy", entry.name());
    object.setLong("strategy_id", entry.id());
    object.setBool("priority_strategy", entry.priority_strategy());
    object.setLong("start_time", as_system_microseconds(entry.start_time()));
    object.setLong("finish_time", as_system_microseconds(entry.finish_time()));
}

void
convert_to_slime(const std::vector<FlushHistoryEntry>& entries, Inserter& inserter)
{
    ArrayInserter array(inserter.insertArray());
    for (const auto &entry : entries) {
        convert_to_slime(entry, array);
    }
}

void
convert_to_slime(const std::vector<FlushStrategyHistoryEntry>& entries, Inserter& inserter)
{
    ArrayInserter array(inserter.insertArray());
    for (const auto &entry : entries) {
        convert_to_slime(entry, array);
    }
}

}

FlushHistoryExplorer::FlushHistoryExplorer(std::shared_ptr<FlushHistory> flush_history)
    : _flush_history(std::move(flush_history))
{
}

FlushHistoryExplorer::~FlushHistoryExplorer() = default;

void
FlushHistoryExplorer::get_state(const vespalib::slime::Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    if (full) {
        auto view = _flush_history->make_view();
        object.setString("strategy", view->strategy());
        object.setLong("strategy_id_base", view->strategy_id_base());
        object.setLong("strategy_id", view->strategy_id());
        object.setBool("priority_strategy", view->priority_strategy());
        object.setLong("strategy_start_time", as_system_microseconds(view->strategy_start_time()));
        object.setLong("max_concurrent_normal", view->max_concurrent_normal());
        {
            Memory finished_mem("finished");
            ObjectInserter finished_inserter(object, finished_mem);
            convert_to_slime(view->finished(), finished_inserter);
        }
        {
            Memory active_mem("active");
            ObjectInserter active_inserter(object, active_mem);
            convert_to_slime(view->active(), active_inserter);
        }
        {
            Memory pending_mem("pending");
            ObjectInserter pending_inserter(object, pending_mem);
            convert_to_slime(view->pending(), pending_inserter);
        }
        {
            Memory finished_strategies_mem("finished_strategies");
            ObjectInserter finished_strategies_inserter(object, finished_strategies_mem);
            convert_to_slime(view->finished_strategies(), finished_strategies_inserter);
        }
        {
            Memory last_strategies_mem("last_strategies");
            ObjectInserter last_strategies_inserter(object, last_strategies_mem);
            convert_to_slime(view->last_strategies(), last_strategies_inserter);
        }
    }
}

}
