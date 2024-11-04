// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feedhandler.h"
#include "feedstate.h"
#include "packetwrapper.h"
#include "ireplaypackethandler.h"
#include <vespa/searchcore/proton/common/commit_time_tracker.h>

namespace proton {

/**
 * There are 3 feed states, and one paths through the state machine:
 *
 *  INIT -> REPLAY_TRANSACTION_LOG -> NORMAL.
 */


/**
 * The feed handler owner is initializing components.
 */
class InitState : public FeedState {
    std::string _doc_type_name;

public:
    InitState(const std::string &name) noexcept
        : FeedState(INIT),
          _doc_type_name(name)
    {
    }

    void handleOperation(FeedToken, FeedOperationUP op) override {
        throwExceptionInHandleOperation(_doc_type_name, *op);
    }

    void receive(const PacketWrapperSP &wrap, vespalib::Executor &) override {
        throwExceptionInReceive(_doc_type_name.c_str(), wrap->packet.range().from(),
                                wrap->packet.range().to(), wrap->packet.size());
    }
};


/**
 * The feed handler is replaying the transaction log.
 * Replayed messages from the transaction log are sent to the active feed view.
 */
class ReplayTransactionLogState : public FeedState {
    std::string _doc_type_name;
    std::unique_ptr<IReplayPacketHandler> _packet_handler;

public:
    ReplayTransactionLogState(const std::string &name,
            IFeedView *& feed_view_ptr,
            bucketdb::IBucketDBHandler &bucketDBHandler,
            IReplayConfig &replay_config,
            FeedConfigStore &config_store,
            const ReplayThrottlingPolicy &replay_throttling_policy,
            IIncSerialNum &inc_serial_num);

    ~ReplayTransactionLogState() override;
    void handleOperation(FeedToken, FeedOperationUP op) override {
        throwExceptionInHandleOperation(_doc_type_name, *op);
    }

    void receive(const PacketWrapperSP &wrap, vespalib::Executor &executor) override;
};


/**
 * Normal feed state.
 * Incoming messages are sent to the active feed view.
 */
class NormalState : public FeedState {
    FeedHandler         &_handler;

public:
    NormalState(FeedHandler &handler) noexcept;
    ~NormalState() override;
    void handleOperation(FeedToken token, FeedOperationUP op) override;
    void receive(const PacketWrapperSP &wrap, vespalib::Executor &) override;
};
}  // namespace proton

