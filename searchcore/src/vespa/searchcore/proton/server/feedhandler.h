// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_inc_serial_num.h"
#include "i_operation_storer.h"
#include "idocumentmovehandler.h"
#include "igetserialnum.h"
#include "iheartbeathandler.h"
#include "ipruneremoveddocumentshandler.h"
#include "tlswriter.h"
#include "transactionlogmanager.h"
#include <persistence/spi/types.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchlib/transactionlog/client_common.h>
#include <shared_mutex>

namespace searchcorespi::index { struct IThreadingService; }
namespace document { class DocumentTypeRepo; }

namespace proton {
struct ConfigStore;
class CreateBucketOperation;
class DDBState;
class DeleteBucketOperation;
struct FeedConfigStore;
class FeedState;
class IDocumentDBOwner;
struct IFeedHandlerOwner;
class IFeedView;
struct IResourceWriteFilter;
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
class FeedHandler: private search::transactionlog::client::Callback,
                   public IDocumentMoveHandler,
                   public IPruneRemovedDocumentsHandler,
                   public IHeartBeatHandler,
                   public IOperationStorer,
                   public IGetSerialNum,
                   public IIncSerialNum
{
private:
    using Packet = search::transactionlog::Packet;
    using RPC = search::transactionlog::client::RPC;
    using SerialNum = search::SerialNum;
    using Timestamp = storage::spi::Timestamp;
    using BucketId =  document::BucketId;
    using FeedStateSP = std::shared_ptr<FeedState>;
    using FeedOperationUP = std::unique_ptr<FeedOperation>;
    using ReadGuard = std::shared_lock<std::shared_mutex>;
    using WriteGuard = std::unique_lock<std::shared_mutex>;
    using IThreadingService = searchcorespi::index::IThreadingService;
    using TlsWriterFactory = search::transactionlog::WriterFactory;

    IThreadingService                     &_writeService;
    DocTypeName                            _docTypeName;
    IFeedHandlerOwner                     &_owner;
    const IResourceWriteFilter            &_writeFilter;
    IReplayConfig                         &_replayConfig;
    TransactionLogManager                  _tlsMgr;
    const TlsWriterFactory                &_tlsWriterfactory;
    std::unique_ptr<TlsWriter>             _tlsMgrWriter;
    TlsWriter                             *_tlsWriter;
    TlsReplayProgress::UP                  _tlsReplayProgress;
    // the serial num of the last feed operation processed by feed handler.
    SerialNum                              _serialNum;
    // the serial num considered to be fully procssessed and flushed to stable storage. Used to prune transaction log.
    SerialNum                              _prunedSerialNum;
    // the serial num of the last feed operation in the transaction log at startup before replay
    SerialNum                              _replay_end_serial_num;
    uint64_t                               _prepare_serial_num;
    size_t                                 _numOperationsPendingCommit;
    size_t                                 _numOperationsCompleted;
    size_t                                 _numCommitsCompleted;
    bool                                   _delayedPrune;
    mutable std::shared_mutex              _feedLock;
    FeedStateSP                            _feedState;
    // used by master write thread tasks
    IFeedView                             *_activeFeedView;
    const document::DocumentTypeRepo      *_repo;
    const document::DocumentType          *_documentType;
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
    bool considerUpdateOperationForRejection(FeedToken &token, UpdateOperation &op);

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
    void doChangeFeedState(FeedStateSP newState);
    void onCommitDone(size_t numPendingAtStart);
    void initiateCommit();
    void enqueCommitTask();
public:
    FeedHandler(const FeedHandler &) = delete;
    FeedHandler & operator = (const FeedHandler &) = delete;
    /**
     * Create a new feed handler.
     *
     * @param writeService  The thread service used for all write tasks.
     * @param tlsSpec       The spec to connect to the transaction log server.
     * @param docTypeName   The name and version of the document type we are feed handler for.
     * @param owner         Reference to the owner of this feed handler.
     * @param replayConfig  Reference to interface used for replaying config changes.
     * @param writer        Inject writer for tls, or nullptr to use internal.
     */
    FeedHandler(IThreadingService &writeService,
                const vespalib::string &tlsSpec,
                const DocTypeName &docTypeName,
                IFeedHandlerOwner &owner,
                const IResourceWriteFilter &writerFilter,
                IReplayConfig &replayConfig,
                const TlsWriterFactory & writer,
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
    void setActiveFeedView(IFeedView *feedView);

    void setBucketDBHandler(bucketdb::IBucketDBHandler *bucketDBHandler) {
        _bucketDBHandler = bucketDBHandler;
    }

    void setSerialNum(SerialNum serialNum) { _serialNum = serialNum; }
    SerialNum inc_serial_num() override { return ++_serialNum; }
    SerialNum getSerialNum() const override { return _serialNum; }
    // The two following methods are used when saving initial config
    SerialNum get_replay_end_serial_num() const { return _replay_end_serial_num; }
    SerialNum inc_replay_end_serial_num() { return ++_replay_end_serial_num; }
    SerialNum getPrunedSerialNum() const { return _prunedSerialNum; }
    uint64_t  inc_prepare_serial_num() { return ++_prepare_serial_num; }

    bool isDoingReplay() const;
    float getReplayProgress() const {
        return _tlsReplayProgress ? _tlsReplayProgress->getProgress() : 0;
    }
    bool getTransactionLogReplayDone() const;
    vespalib::string getDocTypeName() const { return _docTypeName.getName(); }
    void tlsPrune(SerialNum oldest_to_keep);

    void performOperation(FeedToken token, FeedOperationUP op);
    void handleOperation(FeedToken token, FeedOperationUP op);

    void handleMove(MoveOperation &op, std::shared_ptr<vespalib::IDestructorCallback> moveDoneCtx) override;
    void heartBeat() override;

    void sync();
    RPC::Result receive(const Packet &packet) override;

    void eof() override;
    void performPruneRemovedDocuments(PruneRemovedDocumentsOperation &pruneOp) override;
    void syncTls(SerialNum syncTo);
    void appendOperation(const FeedOperation &op, DoneCallback onDone) override;
    [[nodiscard]] CommitResult startCommit(DoneCallback onDone) override;
    [[nodiscard]] CommitResult storeOperationSync(const FeedOperation & op);
    void considerDelayedPrune();
};

} // namespace proton
