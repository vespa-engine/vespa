// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedstates.h"
#include "feedconfigstore.h"
#include "ifeedview.h"
#include "ireplayconfig.h"
#include "replaypacketdispatcher.h"
#include "replay_throttling_policy.h"
#include <vespa/searchcore/proton/bucketdb/ibucketdbhandler.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/replay_feed_token_factory.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/shared_operation_throttler.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.feedstates");

using search::transactionlog::Packet;
using search::transactionlog::client::RPC;
using search::SerialNum;
using vespalib::Executor;
using vespalib::makeLambdaTask;
using vespalib::IDestructorCallback;
using vespalib::SharedOperationThrottler;
using vespalib::make_string;
using proton::bucketdb::IBucketDBHandler;

namespace proton {

namespace {

const search::SerialNum REPLAY_PROGRESS_INTERVAL = 50000;

void
handleProgress(TlsReplayProgress &progress, SerialNum currentSerial)
{
    progress.updateCurrent(currentSerial);
    if (LOG_WOULD_LOG(event) && (LOG_WOULD_LOG(debug) ||
            (progress.getCurrent() % REPLAY_PROGRESS_INTERVAL == 0)))
    {
        EventLogger::transactionLogReplayProgress(progress.getDomainName(),
                                                  progress.getProgress(),
                                                  progress.getFirst(),
                                                  progress.getLast(),
                                                  progress.getCurrent());
    }
}

class TransactionLogReplayPacketHandler : public IReplayPacketHandler {
    IFeedView *& _feed_view_ptr;  // Pointer can be changed in executor thread.
    IBucketDBHandler &_bucketDBHandler;
    IReplayConfig &_replay_config;
    FeedConfigStore &_config_store;
    IIncSerialNum   &_inc_serial_num;
    CommitTimeTracker _commitTimeTracker;
    std::unique_ptr<SharedOperationThrottler> _throttler;
    std::unique_ptr<feedtoken::ReplayFeedTokenFactory> _replay_feed_token_factory;

    static std::unique_ptr<SharedOperationThrottler> make_throttler(const ReplayThrottlingPolicy& replay_throttling_policy) {
        auto& params = replay_throttling_policy.get_params();
        if (!params.has_value()) {
            return SharedOperationThrottler::make_unlimited_throttler();
        }
        return SharedOperationThrottler::make_dynamic_throttler(params.value());
    }

public:
    TransactionLogReplayPacketHandler(IFeedView *& feed_view_ptr,
                                      IBucketDBHandler &bucketDBHandler,
                                      IReplayConfig &replay_config,
                                      FeedConfigStore &config_store,
                                      const ReplayThrottlingPolicy& replay_throttling_policy,
                                      IIncSerialNum &inc_serial_num)
        : _feed_view_ptr(feed_view_ptr),
          _bucketDBHandler(bucketDBHandler),
          _replay_config(replay_config),
          _config_store(config_store),
          _inc_serial_num(inc_serial_num),
          _commitTimeTracker(5ms),
        _throttler(make_throttler(replay_throttling_policy)),
        _replay_feed_token_factory(std::make_unique<feedtoken::ReplayFeedTokenFactory>(true))
    { }

    ~TransactionLogReplayPacketHandler() override = default;

    FeedToken make_replay_feed_token(const FeedOperation& op) {
        SharedOperationThrottler::Token throttler_token = _throttler->blocking_acquire_one();
        return _replay_feed_token_factory->make_replay_feed_token(std::move(throttler_token), op);
    }

    void replay(const PutOperation &op) override {
        _feed_view_ptr->handlePut(make_replay_feed_token(op), op);
    }
    void replay(const RemoveOperation &op) override {
        _feed_view_ptr->handleRemove(make_replay_feed_token(op), op);
    }
    void replay(const UpdateOperation &op) override {
        _feed_view_ptr->handleUpdate(make_replay_feed_token(op), op);
    }
    void replay(const NoopOperation &) override {} // ignored
    void replay(const NewConfigOperation &op) override {
        _replay_config.replayConfig(op.getSerialNum());
    }

    void replay(const DeleteBucketOperation &op) override {
        _feed_view_ptr->handleDeleteBucket(op, make_replay_feed_token(op));
    }
    void replay(const SplitBucketOperation &op) override {
        _bucketDBHandler.handleSplit(op.getSerialNum(), op.getSource(),
                                     op.getTarget1(), op.getTarget2());
    }
    void replay(const JoinBucketsOperation &op) override {
        _bucketDBHandler.handleJoin(op.getSerialNum(), op.getSource1(),
                                    op.getSource2(), op.getTarget());
    }
    void replay(const PruneRemovedDocumentsOperation &op) override {
        _feed_view_ptr->handlePruneRemovedDocuments(op, make_replay_feed_token(op));
    }
    void replay(const MoveOperation &op) override {
        _feed_view_ptr->handleMove(op, make_replay_feed_token(op));
    }
    void replay(const CreateBucketOperation &) override {
    }
    void replay(const CompactLidSpaceOperation &op) override {
        _feed_view_ptr->handleCompactLidSpace(op, make_replay_feed_token(op));
    }
    NewConfigOperation::IStreamHandler &getNewConfigStreamHandler() override {
        return _config_store;
    }
    const document::DocumentTypeRepo &getDeserializeRepo() override {
        return *_feed_view_ptr->getDocumentTypeRepo();
    }
    void check_serial_num(search::SerialNum serial_num) override {
        auto exp_serial_num = _inc_serial_num.inc_serial_num();
        if (exp_serial_num != serial_num) {
            LOG(warning, "Expected replay serial number %" PRIu64 ", got serial number %" PRIu64, exp_serial_num, serial_num);
            assert(exp_serial_num == serial_num);
        }
    }
    void optionalCommit(search::SerialNum serialNum) override {
        if (_commitTimeTracker.needCommit()) {
            _feed_view_ptr->forceCommit(serialNum);
        }
    }
};

class PacketDispatcher {
public:
    PacketDispatcher(IReplayPacketHandler *packet_handler)
        : _packet_handler(packet_handler)
    {}

    void handlePacket(PacketWrapper & wrap);
private:
    void handleEntry(const Packet::Entry &entry);
    IReplayPacketHandler *_packet_handler;
};

void
PacketDispatcher::handlePacket(PacketWrapper & wrap)
{
    vespalib::nbostream_longlivedbuf handle(wrap.packet.getHandle().data(), wrap.packet.getHandle().size());
    while ( !handle.empty() ) {
        Packet::Entry entry;
        entry.deserialize(handle);
        handleEntry(entry);
        if (wrap.progress != nullptr) {
            handleProgress(*wrap.progress, entry.serial());
        }
    }
    wrap.result = RPC::OK;
    wrap.gate.countDown();
}

void
PacketDispatcher::handleEntry(const Packet::Entry &entry) {
    // Called by handlePacket() in executor thread.
    LOG(spam, "replay packet entry: entrySerial(%" PRIu64 "), entryType(%u)", entry.serial(), entry.type());

    auto entry_serial_num = entry.serial();
    _packet_handler->check_serial_num(entry_serial_num);
    ReplayPacketDispatcher dispatcher(*_packet_handler);
    dispatcher.replayEntry(entry);
    _packet_handler->optionalCommit(entry_serial_num);
}

}  // namespace

ReplayTransactionLogState::ReplayTransactionLogState(
        const std::string &name,
        IFeedView *& feed_view_ptr,
        IBucketDBHandler &bucketDBHandler,
        IReplayConfig &replay_config,
        FeedConfigStore &config_store,
        const ReplayThrottlingPolicy &replay_throttling_policy,
        IIncSerialNum& inc_serial_num)
    : FeedState(REPLAY_TRANSACTION_LOG),
      _doc_type_name(name),
      _packet_handler(std::make_unique<TransactionLogReplayPacketHandler>(feed_view_ptr, bucketDBHandler, replay_config, config_store, replay_throttling_policy, inc_serial_num))
{ }

ReplayTransactionLogState::~ReplayTransactionLogState() = default;

void
ReplayTransactionLogState::receive(const PacketWrapper::SP &wrap, Executor &executor) {
    executor.execute(makeLambdaTask([this, wrap = wrap] () {
        PacketDispatcher dispatcher(_packet_handler.get());
        dispatcher.handlePacket(*wrap);
    }));
}

NormalState::NormalState(FeedHandler &handler) noexcept
    : FeedState(NORMAL),
      _handler(handler)
{
}

NormalState::~NormalState() = default;

void
NormalState::handleOperation(FeedToken token, FeedOperationUP op)
{
    _handler.performOperation(std::move(token), std::move(op));
}

void
NormalState::receive(const PacketWrapperSP &wrap, vespalib::Executor &)
{
    throwExceptionInReceive(_handler.getDocTypeName().c_str(), wrap->packet.range().from(),
                            wrap->packet.range().to(), wrap->packet.size());
}

}  // namespace proton
