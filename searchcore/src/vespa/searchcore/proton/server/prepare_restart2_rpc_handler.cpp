// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart2_rpc_handler.h"
#include <vespa/searchcore/proton/flushengine/flush_history.h>
#include <vespa/searchcore/proton/flushengine/flush_history_view.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/jsonstream.h>
#include <algorithm>
#include <chrono>

using proton::flushengine::FlushHistory;
using proton::flushengine::FlushHistoryEntry;
using proton::flushengine::FlushHistoryView;
using proton::flushengine::FlushStrategyHistoryEntry;
using std::chrono::microseconds;
using std::chrono::steady_clock;
using std::chrono::system_clock;
using vespalib::JsonStream;
using vespalib::asciistream;
using Object = vespalib::JsonStream::Object;
using Array = vespalib::JsonStream::Array;
using End = vespalib::JsonStream::End;

namespace proton {

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



const FlushStrategyHistoryEntry*
last_flush_all_or_prepare_restart_strategy(const FlushHistoryView& view) {
    auto& last = view.last_strategies();
    auto prepare_restart_it = std::find_if(last.begin(), last.end(), [](auto& e) { return e.name() == "prepare_restart"; });
    auto flush_all_it = std::find_if(last.begin(), last.end(), [](auto& e) { return e.name() == "flush_all"; });
    if (prepare_restart_it == last.end()) {
        if (flush_all_it == last.end()) {
            return nullptr;
        }
        return flush_all_it.operator->();
    }
    return prepare_restart_it->id() > flush_all_it->id() ? prepare_restart_it.operator->() : flush_all_it.operator->();
}

void
add_finished_flush(JsonStream& stream, const FlushHistoryView& view, uint32_t strategy_id)
{
    auto& active = view.finished();
    const FlushHistoryEntry* best = nullptr;
    uint32_t count = 0;
    for (auto& entry : active) {
        if (entry.strategy_id() <= strategy_id) {
            if (entry.strategy_id() == strategy_id) {
                ++count;
            }
            if (best == nullptr || best->finish_time() < entry.finish_time()) {
                best = &entry;
            }
        }
    }
    if (best != nullptr) {
        stream << "finished" << Object();
        stream << "flush_count" << count;
        stream << "last_finish" << Object();
        stream << "finish_time" << as_system_microseconds(best->finish_time());
        stream << "strategy" << best->strategy();
        stream << "strategy_id" << best->strategy_id();
        stream << End();
        stream << End();
    }
}

void
add_active_flush(JsonStream& stream, const FlushHistoryView& view, uint32_t strategy_id)
{
    auto& active = view.active();
    const FlushHistoryEntry* best = nullptr;
    uint32_t count = 0;
    for (auto& entry : active) {
        if (entry.strategy_id() <= strategy_id) {
            ++count;
            if (best == nullptr || best->start_time() < entry.start_time()) {
                best = &entry;
            }
        }
    }
    if (best != nullptr) {
        stream << "active" << Object();
        stream << "flush_count" << count;
        stream << "last_start" << Object();
        stream << "start_time" << as_system_microseconds(best->start_time());
        stream << "strategy" << best->strategy();
        stream << "strategy_id" << best->strategy_id();
        stream << End();
        stream << End();
    }
}

void
add_pending_flush(JsonStream& stream, const FlushHistoryView& view, uint32_t strategy_id)
{
    auto& pending = view.pending();
    uint32_t count = 0;
    for (auto& entry : pending) {
        if (entry.strategy_id() <= strategy_id) {
            ++count;
        }
    }
    stream << "pending_flushes" << count;
}

void
add_previous_flush_strategy(JsonStream& stream, const FlushHistoryView& view, const FlushStrategyHistoryEntry& entry)
{
    stream << "previous" << Object();
    stream << "strategy" << entry.name();
    stream << "start_time" << as_system_microseconds(entry.start_time());
    stream << "finish_time" << as_system_microseconds(entry.finish_time());
    // Note: above finish time is when strategy scheduled last flush job, not when that job completed.
    stream << "id" << entry.id();
    add_active_flush(stream, view, entry.id());
    add_finished_flush(stream, view, entry.id());
    stream << End();
}

void
add_current_flush_strategy(JsonStream& stream, const FlushHistoryView& view)
{
    stream << "current" << Object();
    stream << "strategy" << view.strategy();
    stream << "start_time" << as_system_microseconds(view.strategy_start_time());
    stream << "id" << view.strategy_id();
    add_active_flush(stream, view, view.strategy_id());
    add_finished_flush(stream, view, view.strategy_id());
    add_pending_flush(stream, view, view.strategy_id());
    stream << End();
}

void
add_history(JsonStream& stream, const FlushHistory& flush_history)
{
    auto view = flush_history.make_view();
    auto previous = last_flush_all_or_prepare_restart_strategy(*view);
    if (previous != nullptr) {
        add_previous_flush_strategy(stream, *view, *previous);
    }
    add_current_flush_strategy(stream, *view);
}

}


PrepareRestart2RpcHandler::PrepareRestart2RpcHandler(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                                                     vespalib::ref_counted<FRT_RPCRequest> req,
                                                     std::shared_ptr<flushengine::FlushStrategyIdNotifier> notifier,
                                                     FNET_Scheduler *scheduler,
                                                     uint32_t wait_strategy_id,
                                                     std::chrono::steady_clock::duration timeout,
                                                     std::shared_ptr<flushengine::FlushHistory> flush_history)
    : SetFlushStrategyRpcHandler(std::move(owner), std::move(req), std::move(notifier), scheduler, wait_strategy_id,
                                 timeout),
      _flush_history(std::move(flush_history))
{
}

PrepareRestart2RpcHandler::~PrepareRestart2RpcHandler() = default;

void
PrepareRestart2RpcHandler::make_result()
{
    asciistream json;
    JsonStream stream(json, true);

    _req->GetReturn()->AddInt8(is_success() ? 1 : 0);
    stream << Object();
    stream << "wait_strategy_id" << _wait_strategy_id;
    if (_flush_history) {
        add_history(stream, *_flush_history);
    }
    stream << End();
    _req->GetReturn()->AddString(json.c_str());
}

}
