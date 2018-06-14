// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedconfigstore.h"
#include "feedstates.h"
#include "ifeedview.h"
#include "ireplayconfig.h"
#include "replaypacketdispatcher.h"
#include <vespa/searchcore/proton/bucketdb/ibucketdbhandler.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/exceptions.h>


#include <vespa/log/log.h>
LOG_SETUP(".proton.server.feedstates");

using search::transactionlog::Packet;
using search::transactionlog::RPC;
using search::SerialNum;
using vespalib::Executor;
using vespalib::IllegalStateException;
using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::make_string;
using proton::bucketdb::IBucketDBHandler;

namespace proton {

namespace {
typedef vespalib::Closure1<const Packet::Entry &>::UP EntryHandler;

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

void
handlePacket(PacketWrapper::SP wrap, EntryHandler entryHandler)
{
    vespalib::nbostream_longlivedbuf handle(wrap->packet.getHandle().c_str(), wrap->packet.getHandle().size());
    while (handle.size() > 0) {
        Packet::Entry entry;
        entry.deserialize(handle);
        entryHandler->call(entry);
        if (wrap->progress != NULL) {
            handleProgress(*wrap->progress, entry.serial());
        }
    }
    wrap->result = RPC::OK;
    wrap->gate.countDown();
}

class TransactionLogReplayPacketHandler : public IReplayPacketHandler {
    IFeedView *& _feed_view_ptr;  // Pointer can be changed in executor thread.
    IBucketDBHandler &_bucketDBHandler;
    IReplayConfig &_replay_config;
    FeedConfigStore &_config_store;

    void handleTransactionLogEntry(const Packet::Entry &entry);

public:
    TransactionLogReplayPacketHandler(IFeedView *& feed_view_ptr,
                                      IBucketDBHandler &bucketDBHandler,
                                      IReplayConfig &replay_config,
                                      FeedConfigStore &config_store)
        : _feed_view_ptr(feed_view_ptr),
          _bucketDBHandler(bucketDBHandler),
          _replay_config(replay_config),
          _config_store(config_store) {
    }

    virtual void replay(const PutOperation &op) override {
        _feed_view_ptr->handlePut(FeedToken(), op);
    }
    virtual void replay(const RemoveOperation &op) override {
        _feed_view_ptr->handleRemove(FeedToken(), op);
    }
    virtual void replay(const UpdateOperation &op) override {
        _feed_view_ptr->handleUpdate(FeedToken(), op);
    }
    virtual void replay(const NoopOperation &) override {} // ignored
    virtual void replay(const NewConfigOperation &op) override {
        _replay_config.replayConfig(op.getSerialNum());
    }
    virtual void replay(const WipeHistoryOperation &) override {
    }
    virtual void replay(const DeleteBucketOperation &op) override {
        _feed_view_ptr->handleDeleteBucket(op);
    }
    virtual void replay(const SplitBucketOperation &op) override {
        _bucketDBHandler.handleSplit(op.getSerialNum(), op.getSource(),
                                     op.getTarget1(), op.getTarget2());
    }
    virtual void replay(const JoinBucketsOperation &op) override {
        _bucketDBHandler.handleJoin(op.getSerialNum(), op.getSource1(),
                                    op.getSource2(), op.getTarget());
    }
    virtual void replay(const PruneRemovedDocumentsOperation &op) override {
        _feed_view_ptr->handlePruneRemovedDocuments(op);
    }
    virtual void replay(const SpoolerReplayStartOperation &op) override {
        (void) op;
    }
    virtual void replay(const SpoolerReplayCompleteOperation &op) override {
        (void) op;
    }
    virtual void replay(const MoveOperation &op) override {
        _feed_view_ptr->handleMove(op, search::IDestructorCallback::SP());
    }
    virtual void replay(const CreateBucketOperation &) override {
    }
    virtual void replay(const CompactLidSpaceOperation &op) override {
        _feed_view_ptr->handleCompactLidSpace(op);
    }
    virtual NewConfigOperation::IStreamHandler &getNewConfigStreamHandler() override {
        return _config_store;
    }
    virtual const document::DocumentTypeRepo &getDeserializeRepo() override {
        return *_feed_view_ptr->getDocumentTypeRepo();
    }
};

void startDispatch(IReplayPacketHandler *packet_handler,
                   const Packet::Entry &entry) {
    // Called by handlePacket() in executor thread.
    LOG(spam,
        "replay packet entry: entrySerial(%" PRIu64 "), entryType(%u)",
        entry.serial(), entry.type());

    ReplayPacketDispatcher dispatcher(*packet_handler);
    dispatcher.replayEntry(entry);
}

}  // namespace

ReplayTransactionLogState::ReplayTransactionLogState(
        const vespalib::string &name,
        IFeedView *& feed_view_ptr,
        IBucketDBHandler &bucketDBHandler,
        IReplayConfig &replay_config,
        FeedConfigStore &config_store)
    : FeedState(REPLAY_TRANSACTION_LOG),
      _doc_type_name(name),
      _packet_handler(new TransactionLogReplayPacketHandler(
                      feed_view_ptr, bucketDBHandler,
                      replay_config, config_store)) {
}

void ReplayTransactionLogState::receive(const PacketWrapper::SP &wrap,
                                        Executor &executor) {
    EntryHandler closure = makeClosure(&startDispatch, _packet_handler.get());
    executor.execute(makeTask(makeClosure(&handlePacket, wrap, std::move(closure))));
}

}  // namespace proton
