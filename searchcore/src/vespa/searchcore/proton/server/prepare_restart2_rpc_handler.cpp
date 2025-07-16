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
    if (flush_all_it == last.end()) {
        return prepare_restart_it.operator->();
    }
    return prepare_restart_it->id() > flush_all_it->id() ? prepare_restart_it.operator->() : flush_all_it.operator->();
}

void
add_flush_strategy(JsonStream& stream, const FlushStrategyHistoryEntry& entry)
{
    stream << "strategy" << entry.name();
    stream << "id" << entry.id();
    stream << "start_time" << as_system_microseconds(entry.start_time());
    if (entry.switch_time() != steady_clock::time_point()) {
        stream << "switch_time" << as_system_microseconds(entry.switch_time());
        if (entry.finish_time() != steady_clock::time_point()) {
            stream << "finish_time" << as_system_microseconds(entry.finish_time());
        }
    }
    if (entry.last_flush_finish_time() != steady_clock::time_point()) {
        stream << "last_flush_finish_time" << as_system_microseconds(entry.last_flush_finish_time());
    }

    auto& flush_counts = entry.flush_counts();
    auto flushed = flush_counts._finished + flush_counts._inherited_finished;
    auto flushing = flush_counts._started + flush_counts._inherited - flushed;
    stream << "flushed" << flushed;
    stream << "flushing" << flushing;
}

void
add_previous_flush_strategy(JsonStream& stream, const FlushStrategyHistoryEntry& entry)
{
    stream << "previous" << Object();
    add_flush_strategy(stream, entry);
    stream << End();
}

void
add_current_flush_strategy(JsonStream& stream, const FlushHistoryView& view)
{
    stream << "current" << Object();
    auto& active_strategy = view.active_strategy();
    add_flush_strategy(stream, active_strategy);
    stream << "pending_flushes" << view.pending().size();
    stream << End();
}

void
add_history(JsonStream& stream, const FlushHistory& flush_history)
{
    auto view = flush_history.make_view();
    auto previous = last_flush_all_or_prepare_restart_strategy(*view);
    if (previous != nullptr) {
        add_previous_flush_strategy(stream, *previous);
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
