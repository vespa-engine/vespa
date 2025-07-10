// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "set_flush_strategy_rpc_handler.h"

namespace proton {

/*
 * Prepare restart rpc request to proton rpc interface that has been detached.
 * Also used for trigger flush rpc request.
 */
class PrepareRestartRpcHandler : public SetFlushStrategyRpcHandler {
public:
    PrepareRestartRpcHandler(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                             vespalib::ref_counted<FRT_RPCRequest> req,
                             std::shared_ptr<flushengine::FlushStrategyIdNotifier> notifier,
                             FNET_Scheduler* scheduler,
                             uint32_t wait_strategy_id,
                             std::chrono::steady_clock::duration timeout_time);
    ~PrepareRestartRpcHandler() override;
    void make_done_result() override;
    void make_timeout_result() override;
};

}
