// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/feedhandler.h>
#include <vespa/searchcore/proton/server/feedstate.h>
#include <vespa/searchcore/proton/server/ireplaypackethandler.h>

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
    vespalib::string _doc_type_name;

public:
    InitState(const vespalib::string &name)
        : FeedState(INIT),
          _doc_type_name(name)
    {
    }

    virtual void handleOperation(FeedToken, FeedOperation::UP op) override {
        throwExceptionInHandleOperation(_doc_type_name, *op);
    }

    virtual void
    receive(const PacketWrapper::SP &wrap, vespalib::Executor &) override {
        throwExceptionInReceive(_doc_type_name.c_str(),
                                wrap->packet.range().from(),
                                wrap->packet.range().to(),
                                wrap->packet.size());
    }
};


/**
 * The feed handler is replaying the transaction log.
 * Replayed messages from the transaction log are sent to the active feed view.
 */
class ReplayTransactionLogState : public FeedState {
    vespalib::string _doc_type_name;
    std::unique_ptr<IReplayPacketHandler> _packet_handler;

public:
    ReplayTransactionLogState(const vespalib::string &name,
            IFeedView *& feed_view_ptr,
            bucketdb::IBucketDBHandler &bucketDBHandler,
            IReplayConfig &replay_config,
            FeedConfigStore &config_store);

    virtual void handleOperation(FeedToken, FeedOperation::UP op) override {
        throwExceptionInHandleOperation(_doc_type_name, *op);
    }

    virtual void receive(const PacketWrapper::SP &wrap,
                         vespalib::Executor &executor) override;
};


/**
 * Normal feed state.
 * Incoming messages are sent to the active feed view.
 */
class NormalState : public FeedState {
    FeedHandler         &_handler;

public:
    NormalState(FeedHandler &handler)
        : FeedState(NORMAL),
          _handler(handler) {
    }

    virtual void handleOperation(FeedToken token, FeedOperation::UP op) override {
        _handler.performOperation(FeedToken::UP(new FeedToken(token)), std::move(op));
    }

    virtual void
    receive(const PacketWrapper::SP &wrap, vespalib::Executor &) override
    {
        throwExceptionInReceive(_handler.getDocTypeName().c_str(),
                                wrap->packet.range().from(),
                                wrap->packet.range().to(),
                                wrap->packet.size());
    }
};
}  // namespace proton

