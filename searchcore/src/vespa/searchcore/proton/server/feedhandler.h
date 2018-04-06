// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_operation_storer.h"
#include "idocumentmovehandler.h"
#include "igetserialnum.h"
#include "iheartbeathandler.h"
#include "ipruneremoveddocumentshandler.h"
#include "tlswriter.h"
#include "transactionlogmanager.h"
#include <persistence/spi/types.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <mutex>

namespace searchcorespi { namespace index { class IThreadingService; } }

namespace proton {
class ConfigStore;
class CreateBucketOperation;
class DDBState;
class DeleteBucketOperation;
class FeedConfigStore;
class FeedState;
class IDocumentDBOwner;
class IFeedHandlerOwner;
class IFeedView;
class IResourceWriteFilter;
class IReplayConfig;
class JoinBucketsOperation;
class PutOperation;
class RemoveOperation;
class SplitBucketOperation;
class UpdateOperation;

namespace bucketdb { class IBucketDBHandler; }

/**
 * Class handling all aspects of feeding for a document database.
 * In addition to regular feeding this also includes handling the transaction log.
 */
class FeedHandler: private search::transactionlog::TransLogClient::Session::Callback,
                   public IDocumentMoveHandler,
                   public IPruneRemovedDocumentsHandler,
                   public IHeartBeatHandler,
                   public IOperationStorer,
                   public IGetSerialNum
{
private:
    typedef search::transactionlog::Packet  Packet;
    typedef search::transactionlog::RPC     RPC;
    typedef search::SerialNum               SerialNum;
    typedef storage::spi::Timestamp         Timestamp;
    typedef document::BucketId              BucketId;
    using FeedStateSP = std::shared_ptr<FeedState>;
    using FeedOperationUP = std::unique_ptr<FeedOperation>;

    class TlsMgrWriter : public TlsWriter {
        TransactionLogManager &_tls_mgr;
        search::transactionlog::Writer *_tlsDirectWriter;
    public:
        TlsMgrWriter(TransactionLogManager &tls_mgr,
                     search::transactionlog::Writer * tlsDirectWriter) :
            _tls_mgr(tls_mgr),
            _tlsDirectWriter(tlsDirectWriter)
        { }
        void storeOperation(const FeedOperation &op, DoneCallback onDone) override;
        bool erase(SerialNum oldest_to_keep) override;
        SerialNum sync(SerialNum syncTo) override;
    };
    typedef searchcorespi::index::IThreadingService IThreadingService;

    IThreadingService                     &_writeService;
    DocTypeName                            _docTypeName;
    DDBState                              &_state;
    IFeedHandlerOwner                     &_owner;
    const IResourceWriteFilter            &_writeFilter;
    IReplayConfig                         &_replayConfig;
    TransactionLogManager                  _tlsMgr;
    TlsMgrWriter                           _tlsMgrWriter;
    TlsWriter                             &_tlsWriter;
    TlsReplayProgress::UP                  _tlsReplayProgress;
    // the serial num of the last message in the transaction log
    SerialNum                              _serialNum;
    SerialNum                              _prunedSerialNum;
    bool                                   _delayedPrune;
    mutable std::mutex                     _feedLock;
    FeedStateSP                            _feedState;
    // used by master write thread tasks
    IFeedView                             *_activeFeedView;
    bucketdb::IBucketDBHandler            *_bucketDBHandler;
    std::mutex                             _syncLock;
    SerialNum                              _syncedSerialNum; 
    bool                                   _allowSync; // Sanity check

    /**
     * Delayed handling of feed operations, in master write thread.
     * The current feed state is sampled here.
     */
    void doHandleOperation(FeedToken token, FeedOperationUP op);

    bool considerWriteOperationForRejection(FeedToken & token, const FeedOperation &op);
    bool considerUpdateOperationForRejection(FeedToken &token, const UpdateOperation &op);

    /**
     * Delayed execution of feed operations against feed view, in
     * master write thread.
     */
    void performPut(FeedToken token, PutOperation &op);

    void performUpdate(FeedToken token, UpdateOperation &op);
    void performInternalUpdate(FeedToken token, UpdateOperation &op);
    void createNonExistingDocument(FeedToken, const UpdateOperation &op);

    void performRemove(FeedToken token, RemoveOperation &op);
    void performGarbageCollect(FeedToken token);
    void performCreateBucket(FeedToken token, CreateBucketOperation &op);
    void performDeleteBucket(FeedToken token, DeleteBucketOperation &op);
    void performSplit(FeedToken token, SplitBucketOperation &op);
    void performJoin(FeedToken token, JoinBucketsOperation &op);
    void performSync();
    void performEof();

    /**
     * Used when flushing is done
     */
    void performFlushDone(SerialNum flushedSerial);
    void performPrune(SerialNum flushedSerial);

    FeedStateSP getFeedState() const;
    void changeFeedState(FeedStateSP newState);
    void changeFeedState(FeedStateSP newState, const std::lock_guard<std::mutex> &feedGuard);
public:
    FeedHandler(const FeedHandler &) = delete;
    FeedHandler & operator = (const FeedHandler &) = delete;
    /**
     * Create a new feed handler.
     *
     * @param writeService  The thread service used for all write tasks.
     * @param tlsSpec       The spec to connect to the transaction log server.
     * @param docTypeName   The name and version of the document type we are feed handler for.
     * @param state         Document db state
     * @param owner         Reference to the owner of this feed handler.
     * @param replayConfig  Reference to interface used for replaying config changes.
     * @param writer        Inject writer for tls, or nullptr to use internal.
     */
    FeedHandler(IThreadingService &writeService,
                const vespalib::string &tlsSpec,
                const DocTypeName &docTypeName,
                DDBState &state,
                IFeedHandlerOwner &owner,
                const IResourceWriteFilter &writerFilter,
                IReplayConfig &replayConfig,
                search::transactionlog::Writer & writer,
                TlsWriter * tlsWriter = nullptr);

    ~FeedHandler() override;

    /**
     * Init this feed handler.
     *
     * @param oldestConfigSerial The serial number of the oldest config snapshot.
     */
    void init(SerialNum oldestConfigSerial);

    /**
     * Close this feed handler and its components.
     */
    void close();

    /**
     * Start replay of the transaction log.
     *
     * @param flushedIndexMgrSerial   The flushed serial number of the
     *                                index manager.
     * @param flushedSummaryMgrSerial The flushed serial number of the
     *                                document store.
     * @param config_store            Reference to the config store.
     */

    void
    replayTransactionLog(SerialNum flushedIndexMgrSerial,
                         SerialNum flushedSummaryMgrSerial,
                         SerialNum oldestFlushedSerial,
                         SerialNum newestFlushedSerial,
                         ConfigStore &config_store);

    /**
     * Called when a flush is done and allows pruning of the transaction log.
     *
     * @param flushedSerial serial number flushed for all relevant flush targets.
     */
    void flushDone(SerialNum flushedSerial);

    /**
     * Used to flip between normal and recovery feed states.
     */
    void changeToNormalFeedState();

    /**
     * Update the active feed view.
     * Always called by the master write thread so locking is not needed.
     */
    void setActiveFeedView(IFeedView *feedView) {
        _activeFeedView = feedView;
    }

    void setBucketDBHandler(bucketdb::IBucketDBHandler *bucketDBHandler) {
        _bucketDBHandler = bucketDBHandler;
    }

    void setSerialNum(SerialNum serialNum) { _serialNum = serialNum; }
    SerialNum incSerialNum() { return ++_serialNum; }
    SerialNum getSerialNum() const override { return _serialNum; }
    SerialNum getPrunedSerialNum() const { return _prunedSerialNum; }

    bool isDoingReplay() const;
    float getReplayProgress() const {
        return _tlsReplayProgress ? _tlsReplayProgress->getProgress() : 0;
    }
    bool getTransactionLogReplayDone() const;
    vespalib::string getDocTypeName() const { return _docTypeName.getName(); }
    void tlsPrune(SerialNum oldest_to_keep);

    void performOperation(FeedToken token, FeedOperationUP op);
    void handleOperation(FeedToken token, FeedOperationUP op);

    void handleMove(MoveOperation &op, std::shared_ptr<search::IDestructorCallback> moveDoneCtx) override;
    void heartBeat() override;

    virtual void sync();
    RPC::Result receive(const Packet &packet) override;

    void eof() override;
    void performPruneRemovedDocuments(PruneRemovedDocumentsOperation &pruneOp) override;
    void syncTls(SerialNum syncTo);
    void storeOperation(const FeedOperation &op, DoneCallback onDone) override;
    void storeOperationSync(const FeedOperation & op);
    void considerDelayedPrune();
};

} // namespace proton
