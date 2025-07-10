// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "set_flush_strategy_rpc_handler.h"
#include "detached_rpc_requests_owner.h"
#include <algorithm>
#include <cassert>

#include <vespa/log/log.h>

LOG_SETUP(".proton.server.set_flush_strategy_rpc_handler");

using proton::flushengine::FlushStrategyIdListener;
using proton::flushengine::FlushStrategyIdNotifier;

namespace proton {

SetFlushStrategyRpcHandler::SetFlushStrategyRpcHandler(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                                                       vespalib::ref_counted<FRT_RPCRequest> req,
                                                       std::shared_ptr<FlushStrategyIdNotifier> notifier,
                                                       FNET_Scheduler* scheduler,
                                                       uint32_t wait_strategy_id,
                                                       std::chrono::steady_clock::duration timeout)
    : DetachedRpcRequest(std::move(owner), std::move(req)),
      FlushStrategyIdListener(std::move(notifier)),
      FNET_Task(scheduler),
      _ticks(0),
      _wait_strategy_id(wait_strategy_id),
      _strategy_id(0),
      _completed(0u),
      _start_time(std::chrono::steady_clock::now()),
      _timeout(timeout)
{
    LOG(debug, "SetFlushStrategyHandler::SetFlushStrategyHandler, timeout=%f",
        duration_cast<std::chrono::duration<double>>(_timeout).count());
}

SetFlushStrategyRpcHandler::~SetFlushStrategyRpcHandler() = default;

bool
SetFlushStrategyRpcHandler::set_complete(uint8_t value)
{
    uint8_t expected = 0u;
    return _completed.compare_exchange_strong(expected, value);
}

void
SetFlushStrategyRpcHandler::setup()
{
    auto self = shared_from_this();
    if (add_to_owner(self)) {
        if (add_to_notifier(self)) {
            ScheduleNow();
        }
    }
}

std::future<void>
SetFlushStrategyRpcHandler::owner_aborted()
{
    auto self = shared_from_this();
    assert(self);
    LOG(debug, "PrepareRestart2Handler::owner_aborted");
    auto future = _promise.get_future();
    if (set_complete(Completed::owner_aborted)) {
        Kill();
        // Currently in progress of being removed from owner, cf. DetachedRpcRequestsOwner::close
        remove_from_notifier(self);
    }
    return future;
}

void
SetFlushStrategyRpcHandler::set_strategy_id(uint32_t strategy_id)
{
    auto self = shared_from_this();
    assert(self);
    {
        std::lock_guard guard(_lock);
        LOG(debug, "PrepareRestart2Handler::set_strategy_id(%u), _strategy_id=%u, _wait_strategy_id=%u",
            strategy_id, _strategy_id, _wait_strategy_id);
        if (strategy_id <= _strategy_id) {
            return;
        }
        _strategy_id = strategy_id;
        if (_strategy_id < _wait_strategy_id) {
            return;
        }
    }
    if (set_complete(Completed::done)) {
        LOG(debug, "PrepareRestart2Handler::set_strategy_id(%u) completed request", strategy_id);
        make_done_result();
        _req.internal_detach()->Return(); // handover
        Kill();
        remove_from_owner(self);
        remove_from_notifier(self);
    }
}

void
SetFlushStrategyRpcHandler::notifier_closed()
{
    auto self = shared_from_this();
    assert(self);
    LOG(debug, "PrepareRestart2Handler::notifier_close");
    if (set_complete(Completed::notifier_closed)) {
        Kill();
        remove_from_owner(self);
        // Already removed from notifier.
    }
}

void
SetFlushStrategyRpcHandler::PerformTask()
{
    auto self = shared_from_this();
    assert(self);
    auto now = std::chrono::steady_clock::now();
    auto elapsed = now - _start_time;
    double elapsed_s = duration_cast<std::chrono::duration<double>>(elapsed).count();
    double timeout_s = duration_cast<std::chrono::duration<double>>(_timeout).count();
    double time_left = timeout_s - elapsed_s;
    ++_ticks;
    LOG(debug, "PrepareRestart2Handler::PerformTask, _ticks=%u, elapsed=%f, timeout=%f", _ticks, elapsed_s, timeout_s);
    if (time_left <= 0.0) {
        if (set_complete(Completed::timeout)) {
            LOG(debug, "PrepareRestart2Handler::PerformTask, _ticks=%u, elapsed=%f considered a timeout", _ticks, elapsed_s);
            make_timeout_result();
            _req.internal_detach()->Return(); // handover
            // No reschedule
            remove_from_owner(self);
            remove_from_notifier(self);
        }
    } else if (_conn->GetState() >= FNET_Connection::State::FNET_CLOSING) {
        if (set_complete(Completed::lost_conn)) {
            LOG(debug, "PrepareRestart2Handler::PerformTask, _ticks=%u elapsed=%f lost connection", _ticks, elapsed_s);
            // No reschedule
            remove_from_owner(self);
            remove_from_notifier(self);
        }
    } else if (_completed.load(std::memory_order_acquire) == Completed::started) {
        Schedule(std::min(10.0, time_left)); // Schedule new task in 10 second or earlier if less time left
    }
}

}
