// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart_rpc_handler.h"

namespace proton {

PrepareRestartRpcHandler::PrepareRestartRpcHandler(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                                                   vespalib::ref_counted<FRT_RPCRequest> req,
                                                   std::shared_ptr<flushengine::FlushStrategyIdNotifier> notifier,
                                                   FNET_Scheduler *scheduler,
                                                   uint32_t wait_strategy_id,
                                                   std::chrono::steady_clock::duration timeout)
    : SetFlushStrategyRpcHandler(std::move(owner), std::move(req), std::move(notifier), scheduler, wait_strategy_id,
                                 timeout)
{
}

PrepareRestartRpcHandler::~PrepareRestartRpcHandler() = default;

void
PrepareRestartRpcHandler::make_done_result()
{
    _req->GetReturn()->AddInt8(1);
}

void
PrepareRestartRpcHandler::make_timeout_result()
{
    _req->GetReturn()->AddInt8(0);
}

}
