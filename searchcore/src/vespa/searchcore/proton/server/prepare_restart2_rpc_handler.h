// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "set_flush_strategy_rpc_handler.h"

namespace proton::flushengine { class FlushHistory; }

namespace proton {

/*
 * Prepare restart2 rpc request to proton rpc interface that has been detached.
 */
class PrepareRestart2RpcHandler : public SetFlushStrategyRpcHandler {
    std::shared_ptr<flushengine::FlushHistory> _flush_history;
public:
    PrepareRestart2RpcHandler(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                              vespalib::ref_counted<FRT_RPCRequest> req,
                              std::shared_ptr<flushengine::FlushStrategyIdNotifier> notifier,
                              FNET_Scheduler* scheduler,
                              uint32_t wait_strategy_id,
                              std::chrono::steady_clock::duration timeout_time,
                              std::shared_ptr<flushengine::FlushHistory> flush_history);
    ~PrepareRestart2RpcHandler() override;
    void make_result() override;
};

}
