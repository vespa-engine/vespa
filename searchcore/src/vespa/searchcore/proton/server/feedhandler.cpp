// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedhandler.h"
#include "ddbstate.h"
#include "feedstates.h"
#include "i_feed_handler_owner.h"
#include "ifeedview.h"
#include "configstore.h"
#include <vespa/document/util/feed_reject_helper.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcore/proton/bucketdb/ibucketdbhandler.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/persistenceengine/transport_latch.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/searchlib/transactionlog/client_session.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <cassert>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.feedhandler");

using document::BucketId;
using document::Document;
using document::DocumentTypeRepo;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::Executor;
using vespalib::IllegalStateException;
using vespalib::makeLambdaTask;
using vespalib::make_string;
using std::make_unique;
using std::make_shared;
using search::CommitParam;
using namespace vespalib::atomic;

namespace proton {

namespace {

using search::SerialNum;

bool
ignoreOperation(const DocumentOperation &op) {
    return (op.getPrevTimestamp() != 0) && (op.getTimestamp() < op.getPrevTimestamp());
}

class TlsMgrWriter : public TlsWriter {
    TransactionLogManager &_tls_mgr;
    std::shared_ptr<search::transactionlog::Writer> _writer;
public:
    TlsMgrWriter(TransactionLogManager &tls_mgr,
                 const search::transactionlog::WriterFactory & factory)
        : _tls_mgr(tls_mgr),
          _writer(factory.getWriter(tls_mgr.getDomainName()))
    { }
    void appendOperation(const FeedOperation &op, DoneCallback onDone) override;
    [[nodiscard]] CommitResult startCommit(DoneCallback onDone) override {
        return _writer->startCommit(std::move(onDone));
    }
    bool erase(SerialNum oldest_to_keep) override;
    SerialNum sync(SerialNum syncTo) override;
};

void
TlsMgrWriter::appendOperation(const FeedOperation &op, DoneCallback onDone) {
    using Packet = search::transactionlog::Packet;
    vespalib::nbostream stream;
    op.serialize(stream);
    LOG(debug, "appendOperation(): serialNum(%" PRIu64 "), type(%u), size(%zu)",
        op.getSerialNum(), (uint32_t)op.getType(), stream.size());
    Packet::Entry entry(op.getSerialNum(), op.getType(), vespalib::ConstBufferRef(stream.data(), stream.size()));
    Packet packet(entry.serializedSize());
    packet.add(entry);
    _writer->append(packet, std::move(onDone));
}

bool
TlsMgrWriter::erase(SerialNum oldest_to_keep) {
    return _tls_mgr.getSession()->erase(oldest_to_keep);
}

SerialNum
TlsMgrWriter::sync(SerialNum syncTo)
{
    for (int retryCount = 0; retryCount < 10; ++retryCount) {
        SerialNum syncedTo(0);
        LOG(debug, "Trying tls sync(%" PRIu64 ")", syncTo);
        bool res = _tls_mgr.getSession()->sync(syncTo, syncedTo);
        if (!res) {
            LOG(debug, "Tls sync failed, retrying");
            std::this_thread::sleep_for(100ms);
            continue;
        }
        if (syncedTo >= syncTo) {
            LOG(debug, "Tls sync complete, reached %" PRIu64", returning", syncedTo);
            return syncedTo;
        }
        LOG(debug, "Tls sync incomplete, reached %" PRIu64 ", retrying", syncedTo);
    }
    throw IllegalStateException(make_string("Failed to sync TLS to token %" PRIu64 ".", syncTo));
}

class OnCommitDone : public vespalib::IDestructorCallback {
public:
    OnCommitDone(Executor & executor, std::unique_ptr<Executor::Task> task) noexcept
        : _executor(executor),
          _task(std::move(task))
    {}
    ~OnCommitDone() override { _executor.execute(std::move(_task)); }
private:
    Executor & _executor;
    std::unique_ptr<Executor::Task> _task;
};

/**
 * Wraps the original feed token so that it will be delivered
 * when the derived operation is completed.
 */
class DaisyChainedFeedToken : public feedtoken::ITransport {
public:
    DaisyChainedFeedToken(FeedToken token) : _token(std::move(token)) {}
    ~DaisyChainedFeedToken() override;
    void send(ResultUP, bool ) override {
        _token.reset();
    }
private:
    FeedToken _token;
};

DaisyChainedFeedToken::~DaisyChainedFeedToken() = default;

}  // namespace

void
FeedHandler::doHandleOperation(FeedToken token, FeedOperation::UP op)
{
    assert(_writeService.master().isCurrentThread());
    // Since _feedState is only modified in the master thread we can skip the lock here.
    _feedState->handleOperation(std::move(token), std::move(op));
}

void
FeedHandler::performPut(FeedToken token, PutOperation &op) {
    op.assertValid();
    op.set_prepare_serial_num(inc_prepare_serial_num());
    _activeFeedView->preparePut(op);
    if (ignoreOperation(op)) {
        LOG(debug, "performPut(): ignoreOperation: docId(%s), timestamp(%" PRIu64 "), prevTimestamp(%" PRIu64 ")",
            op.getDocument()->getId().toString().c_str(), (uint64_t)op.getTimestamp(), (uint64_t)op.getPrevTimestamp());
        if (token) {
            token->setResult(make_unique<Result>(), false);
        }
        return;
    }
    /*
     * Check if document type repos are equal. DocumentTypeRepoFactory::make
     * returns the same document type repo if document type configs are equal,
     * thus we can just perform a cheaper identity check here.
     */
    if (_repo != op.getDocument()->getRepo()) {
        op.deserializeDocument(*_repo);
    }
    appendOperation(op, token);
    if (token) {
        token->setResult(make_unique<Result>(), false);
    }
    _activeFeedView->handlePut(std::move(token), op);
}


void
FeedHandler::performUpdate(FeedToken token, UpdateOperation &op)
{
    op.set_prepare_serial_num(inc_prepare_serial_num());
    _activeFeedView->prepareUpdate(op);
    if (op.getPrevDbDocumentId().valid() && !op.getPrevMarkedAsRemoved()) {
        if (considerUpdateOperationForRejection(token, op)) {
            return;
        }
        performInternalUpdate(std::move(token), op);
    } else if (op.getUpdate()->getCreateIfNonExistent()) {
        if (considerUpdateOperationForRejection(token, op)) {
            return;
        }
        createNonExistingDocument(std::move(token), op);
    } else {
        if (token) {
            token->setResult(make_unique<UpdateResult>(Timestamp(0)), false);
        }
    }
}


void
FeedHandler::performInternalUpdate(FeedToken token, UpdateOperation &op)
{
    appendOperation(op, token);
    if (token) {
        token->setResult(make_unique<UpdateResult>(Timestamp(op.getPrevTimestamp())), true);
    }
    _activeFeedView->handleUpdate(std::move(token), op);
}


void
FeedHandler::createNonExistingDocument(FeedToken token, const UpdateOperation &op)
{
    auto doc = make_shared<Document>(op.getUpdate()->getType(), op.getUpdate()->getId());
    doc->setRepo(*_activeFeedView->getDocumentTypeRepo());
    op.getUpdate()->applyTo(*doc);
    PutOperation putOp(op.getBucketId(), op.getTimestamp(), std::move(doc));
    putOp.set_prepare_serial_num(op.get_prepare_serial_num());
    _activeFeedView->preparePut(putOp);
    appendOperation(putOp, token);
    if (token) {
        token->setResult(make_unique<UpdateResult>(Timestamp(putOp.getTimestamp())), true);
    }

    _activeFeedView->handlePut(feedtoken::make(std::make_unique<DaisyChainedFeedToken>(std::move(token))), putOp);
}


void
FeedHandler::performRemove(FeedToken token, RemoveOperation &op) {
    op.set_prepare_serial_num(inc_prepare_serial_num());
    _activeFeedView->prepareRemove(op);
    if (ignoreOperation(op)) {
        LOG(debug, "performRemove(): ignoreOperation: remove(%s), timestamp(%" PRIu64 "), prevTimestamp(%" PRIu64 ")",
            op.toString().c_str(), (uint64_t)op.getTimestamp(), (uint64_t)op.getPrevTimestamp());
        if (token) {
            token->setResult(make_unique<RemoveResult>(false), false);
        }
        return;
    }
    if (op.getPrevDbDocumentId().valid()) {
        assert(op.getValidNewOrPrevDbdId());
        assert(op.notMovingLidInSameSubDb());
        appendOperation(op, token);
        if (token) {
            bool documentWasFound = !op.getPrevMarkedAsRemoved();
            token->setResult(make_unique<RemoveResult>(documentWasFound), documentWasFound);
        }
        _activeFeedView->handleRemove(std::move(token), op);
    } else if (op.hasDocType()) {
        assert(op.getDocType() == _docTypeName.getName());
        appendOperation(op, token);
        if (token) {
            token->setResult(make_unique<RemoveResult>(false), false);
        }
        _activeFeedView->handleRemove(std::move(token), op);
    } else {
        if (token) {
            token->setResult(make_unique<RemoveResult>(false), false);
        }
    }
}

void
FeedHandler::performGarbageCollect(FeedToken token)
{
    (void) token;
}

void
FeedHandler::performCreateBucket(FeedToken token, CreateBucketOperation &op)
{
    appendOperation(op, token);
    _bucketDBHandler->handleCreateBucket(op.getBucketId());
}

void
FeedHandler::performDeleteBucket(FeedToken token, DeleteBucketOperation &op) {
    _activeFeedView->prepareDeleteBucket(op);
    appendOperation(op, token);
    // Delete documents in bucket
    _activeFeedView->handleDeleteBucket(op, token);
    // Delete bucket itself, should no longer have documents.
    _bucketDBHandler->handleDeleteBucket(op.getBucketId());
    initiateCommit(vespalib::steady_clock::now());
}

void
FeedHandler::performSplit(FeedToken token, SplitBucketOperation &op) {
    appendOperation(op, token);
    _bucketDBHandler->handleSplit(op.getSerialNum(), op.getSource(), op.getTarget1(), op.getTarget2());
}

void
FeedHandler::performJoin(FeedToken token, JoinBucketsOperation &op) {
    appendOperation(op, token);
    _bucketDBHandler->handleJoin(op.getSerialNum(), op.getSource1(), op.getSource2(), op.getTarget());
}

void
FeedHandler::performEof()
{
    assert(_writeService.master().isCurrentThread());
    _activeFeedView->forceCommitAndWait(CommitParam(load_relaxed(_serialNum)));
    LOG(debug, "Visiting done for transaction log domain '%s', eof received", _tlsMgr.getDomainName().c_str());
    // Replay must be complete
    if (_replay_end_serial_num != load_relaxed(_serialNum)) {
        LOG(warning, "Expected replay end serial number %" PRIu64 ", got serial number %" PRIu64,
            _replay_end_serial_num, load_relaxed(_serialNum));
        assert(_replay_end_serial_num == load_relaxed(_serialNum));
    }
    _owner.onTransactionLogReplayDone();
    _tlsMgr.replayDone();
    changeToNormalFeedState();
    _owner.enterRedoReprocessState();
}


void
FeedHandler::performFlushDone(SerialNum flushedSerial)
{
    assert(_writeService.master().isCurrentThread());
    // XXX: flushedSerial can go backwards when attribute vectors are
    // resurrected.  This can be avoided if resurrected attribute vectors
    // pretends to have been flushed at resurrect time.
    if (flushedSerial <= _prunedSerialNum) {
        return;                                // Cannot unprune.
    } 
    if (!_owner.getAllowPrune()) {
        _prunedSerialNum = flushedSerial;
        _delayedPrune = true;
        return;
    }
    _delayedPrune = false;
    performPrune(flushedSerial);
}


void
FeedHandler::performPrune(SerialNum flushedSerial)
{
    try {
        tlsPrune(flushedSerial);  // throws on error
        LOG(debug, "Pruned TLS to token %" PRIu64 ".", flushedSerial);
        _owner.onPerformPrune(flushedSerial);
        EventLogger::transactionLogPruneComplete(_tlsMgr.getDomainName(), flushedSerial);
    } catch (const IllegalStateException & e) {
        LOG(warning, "FeedHandler::performPrune failed due to '%s'.", e.what());
    }
}


void
FeedHandler::considerDelayedPrune()
{
    if (_delayedPrune) {
        _delayedPrune = false;
        performPrune(_prunedSerialNum);
    }
}


std::shared_ptr<FeedState>
FeedHandler::getFeedState() const
{
    ReadGuard guard(_feedLock);
    return _feedState;
}

void
FeedHandler::changeFeedState(FeedStateSP newState)
{
    if (_writeService.master().isCurrentThread()) {
        doChangeFeedState(std::move(newState));
    } else {
        _writeService.master().execute(makeLambdaTask([this, newState=std::move(newState)] () {
            doChangeFeedState(std::move(newState));
        }));
        _writeService.master().sync();
    }
}

void
FeedHandler::doChangeFeedState(FeedStateSP newState)
{
    WriteGuard guard(_feedLock);
    LOG(debug, "Change feed state from '%s' -> '%s'", _feedState->getName().c_str(), newState->getName().c_str());
    _feedState = std::move(newState);
}

FeedHandler::FeedHandler(IThreadingService &writeService,
                         const vespalib::string &tlsSpec,
                         const DocTypeName &docTypeName,
                         IFeedHandlerOwner &owner,
                         const IResourceWriteFilter &writeFilter,
                         IReplayConfig &replayConfig,
                         const TlsWriterFactory & tlsWriterFactory,
                         TlsWriter * tlsWriter)
    : search::transactionlog::client::Callback(),
      IDocumentMoveHandler(),
      IPruneRemovedDocumentsHandler(),
      IHeartBeatHandler(),
      IOperationStorer(),
      IGetSerialNum(),
      _writeService(writeService),
      _docTypeName(docTypeName),
      _owner(owner),
      _writeFilter(writeFilter),
      _replayConfig(replayConfig),
      _tlsMgr(writeService.transport(), tlsSpec, docTypeName.getName()),
      _tlsWriterfactory(tlsWriterFactory),
      _tlsMgrWriter(),
      _tlsWriter(tlsWriter),
      _tlsReplayProgress(),
      _serialNum(0),
      _prunedSerialNum(0),
      _replay_end_serial_num(0),
      _prepare_serial_num(0),
      _numOperations(),
      _delayedPrune(false),
      _feedLock(),
      _feedState(make_shared<InitState>(getDocTypeName())),
      _activeFeedView(nullptr),
      _repo(nullptr),
      _documentType(nullptr),
      _bucketDBHandler(nullptr),
      _syncLock(),
      _syncedSerialNum(0),
      _allowSync(false),
      _heart_beat_time(vespalib::steady_time()),
      _stats_lock(),
      _stats()
{ }


FeedHandler::~FeedHandler() = default;

// Called on DocumentDB creatio
void
FeedHandler::init(SerialNum oldestConfigSerial)
{
    _tlsMgr.init(oldestConfigSerial, _prunedSerialNum, _replay_end_serial_num);
    store_relaxed(_serialNum, _prunedSerialNum);
    if (_tlsWriter == nullptr) {
        _tlsMgrWriter = std::make_unique<TlsMgrWriter>(_tlsMgr, _tlsWriterfactory);
        _tlsWriter = _tlsMgrWriter.get();
    }
    _allowSync = true;
    syncTls(_replay_end_serial_num);
}


void
FeedHandler::close()
{
    if (_allowSync) {
        syncTls(load_relaxed(_serialNum));
    }
    _allowSync = false;
    _tlsMgr.close();
}

void
FeedHandler::replayTransactionLog(SerialNum flushedIndexMgrSerial, SerialNum flushedSummaryMgrSerial,
                                  SerialNum oldestFlushedSerial, SerialNum newestFlushedSerial,
                                  ConfigStore &config_store,
                                  const ReplayThrottlingPolicy& replay_throttling_policy)
{
    (void) newestFlushedSerial;
    assert(_activeFeedView);
    assert(_bucketDBHandler);
    auto state = make_shared<ReplayTransactionLogState>
                          (getDocTypeName(), _activeFeedView, *_bucketDBHandler, _replayConfig, config_store, replay_throttling_policy, *this);
    changeFeedState(state);
    // Resurrected attribute vector might cause oldestFlushedSerial to
    // be lower than _prunedSerialNum, so don't warn for now.
    (void) oldestFlushedSerial;
    assert(_replay_end_serial_num >= newestFlushedSerial);

    TransactionLogManager::prepareReplay(_tlsMgr.getClient(), _docTypeName.getName(),
                                         flushedIndexMgrSerial, flushedSummaryMgrSerial, config_store);

    _tlsReplayProgress = _tlsMgr.make_replay_progress(load_relaxed(_serialNum), _replay_end_serial_num);
    _tlsMgr.startReplay(load_relaxed(_serialNum), _replay_end_serial_num, *this);
}

void
FeedHandler::flushDone(SerialNum flushedSerial)
{
    // Called by flush scheduler thread after flush worker thread has completed a flush task
    _writeService.master().execute(makeLambdaTask([this, flushedSerial]() { performFlushDone(flushedSerial); }));
    _writeService.master().sync();

}

void FeedHandler::changeToNormalFeedState() {
    changeFeedState(make_shared<NormalState>(*this));
}

void
FeedHandler::setActiveFeedView(IFeedView *feedView)
{
    _activeFeedView = feedView;
    _repo = feedView->getDocumentTypeRepo().get();
    _documentType = _repo->getDocumentType(_docTypeName.getName());
}

bool
FeedHandler::isDoingReplay() const {
    return _tlsMgr.isDoingReplay();
}

bool
FeedHandler::getTransactionLogReplayDone() const {
    return _tlsMgr.getReplayDone();
}

void
FeedHandler::onCommitDone(size_t numOperations, vespalib::steady_time start_time) {
    _numOperations.commitCompleted(numOperations);
    if (_numOperations.shouldScheduleCommit()) {
        enqueCommitTask();
    }
    vespalib::steady_time now = vespalib::steady_clock::now();
    auto latency = vespalib::to_s(now - start_time);
    std::lock_guard guard(_stats_lock);
    _stats.add_commit(numOperations, latency);
}

void FeedHandler::enqueCommitTask() {
    _writeService.master().execute(makeLambdaTask([this, start_time(vespalib::steady_clock::now())]() {
        initiateCommit(start_time);
    }));
}

void
FeedHandler::initiateCommit(vespalib::steady_time start_time) {
    auto onCommitDoneContext = std::make_shared<OnCommitDone>(
            _writeService.master(),
            makeLambdaTask([this, operations=_numOperations.operationsSinceLastCommitStart(), start_time]() {
                onCommitDone(operations, start_time);
            }));
    auto commitResult = _tlsWriter->startCommit(onCommitDoneContext);
    _numOperations.startCommit();
    if (_activeFeedView) {
        using KeepAlivePair = vespalib::KeepAlive<std::pair<CommitResult, DoneCallback>>;
        auto pair = std::make_pair(std::move(commitResult), std::move(onCommitDoneContext));
        _activeFeedView->forceCommit(CommitParam(load_relaxed(_serialNum), CommitParam::UpdateStats::SKIP), std::make_shared<KeepAlivePair>(std::move(pair)));
    }
}

void
FeedHandler::appendOperation(const FeedOperation &op, TlsWriter::DoneCallback onDone) {
    if (!op.getSerialNum()) {
        const_cast<FeedOperation &>(op).setSerialNum(inc_serial_num());
    }
    _tlsWriter->appendOperation(op, std::move(onDone));
    _numOperations.startOperation();
    if (_numOperations.operationsInFlight() == 1) {
        enqueCommitTask();
    }
}

FeedHandler::CommitResult
FeedHandler::startCommit(DoneCallback onDone) {
    return _tlsWriter->startCommit(std::move(onDone));
}

FeedHandler::CommitResult
FeedHandler::storeOperationSync(const FeedOperation &op) {
    vespalib::Gate gate;
    auto commit_result = appendAndCommitOperation(op, make_shared<vespalib::GateCallback>(gate));
    gate.await();
    return commit_result;
}

void
FeedHandler::tlsPrune(SerialNum oldest_to_keep) {
    if (!_tlsWriter->erase(oldest_to_keep)) {
        throw IllegalStateException(make_string("Failed to prune TLS to token %" PRIu64 ".", oldest_to_keep));
    }
    _prunedSerialNum = oldest_to_keep;
}

namespace {

template <typename ResultType>
void
feedOperationRejected(FeedToken & token, const vespalib::string &opType, const vespalib::string &docId,
                           const DocTypeName & docTypeName, const vespalib::string &rejectMessage)
{
    if (token) {
        auto message = make_string("%s operation rejected for document '%s' of type '%s': '%s'",
                                   opType.c_str(), docId.c_str(), docTypeName.toString().c_str(), rejectMessage.c_str());
        token->setResult(make_unique<ResultType>(Result::ErrorType::RESOURCE_EXHAUSTED, message), false);
        token->fail();
    }
}

void
notifyFeedOperationRejected(FeedToken & token, const FeedOperation &op,
                            const DocTypeName & docTypeName, const vespalib::string &rejectMessage)
{
    if ((op.getType() == FeedOperation::UPDATE_42) || (op.getType() == FeedOperation::UPDATE)) {
        vespalib::string docId = (static_cast<const UpdateOperation &>(op)).getUpdate()->getId().toString();
        feedOperationRejected<UpdateResult>(token, "Update", docId, docTypeName, rejectMessage);
    } else if (op.getType() == FeedOperation::PUT) {
        vespalib::string docId = (static_cast<const PutOperation &>(op)).getDocument()->getId().toString();
        feedOperationRejected<Result>(token, "Put", docId, docTypeName, rejectMessage);
    } else {
        feedOperationRejected<Result>(token, "Feed", "", docTypeName, rejectMessage);
    }
}

/**
 * Tells wether an operation should be blocked when resourcelimits have been reached.
 * It looks at the operation type and also the content if it is an 'update' operation.
 */
class FeedRejectHelper {
public:
    static bool isRejectableFeedOperation(const FeedOperation & op);
    static bool mustReject(const UpdateOperation & updateOperation);
};

bool
FeedRejectHelper::mustReject(const UpdateOperation & updateOperation) {
    if (updateOperation.getUpdate()) {
        return document::FeedRejectHelper::mustReject(*updateOperation.getUpdate());
    }
    return false;
}

bool
FeedRejectHelper::isRejectableFeedOperation(const FeedOperation & op)
{
    FeedOperation::Type type = op.getType();
    if (type == FeedOperation::PUT) {
        return true;
    } else if (type == FeedOperation::UPDATE_42 || type == FeedOperation::UPDATE) {
        return mustReject(dynamic_cast<const UpdateOperation &>(op));
    }
    return false;
}

}

bool
FeedHandler::considerWriteOperationForRejection(FeedToken & token, const FeedOperation &op)
{
    if (!_writeFilter.acceptWriteOperation() && FeedRejectHelper::isRejectableFeedOperation(op)) {
        IResourceWriteFilter::State state = _writeFilter.getAcceptState();
        if (!state.acceptWriteOperation()) {
            notifyFeedOperationRejected(token, op, _docTypeName, state.message());
            return true;
        }
    }
    return false;
}

bool
FeedHandler::considerUpdateOperationForRejection(FeedToken &token, UpdateOperation &op)
{
    const auto &update = *op.getUpdate();
    /*
     * Check if document types are equal. DocumentTypeRepoFactory::make returns
     * the same document type repo if document type configs are equal, thus we
     * can just perform a cheaper identity check here.
     */
    if (_documentType != &update.getType()) {
        try {
            op.verifyUpdate(*_repo);
        } catch (document::FieldNotFoundException &e) {
            if (token) {
                auto message = make_string("Update operation rejected for document '%s' of type '%s': 'Field not found'",
                                           update.getId().toString().c_str(), _docTypeName.toString().c_str());
                token->setResult(make_unique<UpdateResult>(Result::ErrorType::TRANSIENT_ERROR, message), false);
                token->fail();
            }
            return true;
        } catch (document::DocumentTypeNotFoundException &e) {
            auto message = make_string("Update operation rejected for document '%s' of type '%s': 'Uknown document type', expected '%s'",
                                       update.getId().toString().c_str(),
                                       e.getDocumentTypeName().c_str(),
                                       _docTypeName.toString().c_str());
            token->setResult(make_unique<UpdateResult>(Result::ErrorType::TRANSIENT_ERROR, message), false);
            token->fail();
            return true;
        } catch (document::WrongTensorTypeException &e) {
            auto message = make_string("Update operation rejected for document '%s' of type '%s': 'Wrong tensor type: %s'",
                                       update.getId().toString().c_str(),
                                       _docTypeName.toString().c_str(),
                                       e.getMessage().c_str());
            token->setResult(make_unique<UpdateResult>(Result::ErrorType::TRANSIENT_ERROR, message), false);
            token->fail();
            return true;
        }
    }
    return false;
}


void
FeedHandler::performOperation(FeedToken token, FeedOperation::UP op)
{
    if (considerWriteOperationForRejection(token, *op)) {
        return;
    }
    switch(op->getType()) {
    case FeedOperation::PUT:
        performPut(std::move(token), static_cast<PutOperation &>(*op));
        return;
    case FeedOperation::REMOVE:
    case FeedOperation::REMOVE_GID:
        performRemove(std::move(token), static_cast<RemoveOperation &>(*op));
        return;
    case FeedOperation::UPDATE_42:
    case FeedOperation::UPDATE:
        performUpdate(std::move(token), static_cast<UpdateOperation &>(*op));
        return;
    case FeedOperation::DELETE_BUCKET:
        performDeleteBucket(std::move(token), static_cast<DeleteBucketOperation &>(*op));
        return;
    case FeedOperation::SPLIT_BUCKET:
        performSplit(std::move(token), static_cast<SplitBucketOperation &>(*op));
        return;
    case FeedOperation::JOIN_BUCKETS:
        performJoin(std::move(token), static_cast<JoinBucketsOperation &>(*op));
        return;
    case FeedOperation::WIPE_HISTORY:
        performGarbageCollect(std::move(token));
        return;
    case FeedOperation::CREATE_BUCKET:
        performCreateBucket(std::move(token), static_cast<CreateBucketOperation &>(*op));
        return;
    default:
        assert(!"Illegal operation type");
    }
}

void
FeedHandler::handleOperation(FeedToken token, FeedOperation::UP op)
{
    // This function is only called when handling external feed operations (see PersistenceHandlerProxy),
    // and ensures that the calling thread (persistence thread) is blocked until the master thread has capacity to handle more tasks.
    // This helps keeping feed operation latencies and memory usage in check.
    // NOTE: Tasks that are created and executed from the master thread itself or some of its helpers
    //       cannot use blocking_master_execute() as that could lead to deadlocks.
    //       See FeedHandler::initiateCommit() for a concrete example.
    _writeService.blocking_master_execute(makeLambdaTask([this, token = std::move(token), op = std::move(op)]() mutable {
        doHandleOperation(std::move(token), std::move(op));
    }));
}

void
FeedHandler::handleMove(MoveOperation &op, vespalib::IDestructorCallback::SP moveDoneCtx)
{
    assert(_writeService.master().isCurrentThread());
    op.set_prepare_serial_num(inc_prepare_serial_num());
    _activeFeedView->prepareMove(op);
    assert(op.getValidDbdId());
    assert(op.getValidPrevDbdId());
    assert(op.getSubDbId() != op.getPrevSubDbId());
    appendOperation(op, moveDoneCtx);
    _activeFeedView->handleMove(op, std::move(moveDoneCtx));
}

void
FeedHandler::heartBeat()
{
    assert(_writeService.master().isCurrentThread());
    _heart_beat_time.store(vespalib::steady_clock::now());
    _activeFeedView->heartBeat(load_relaxed(_serialNum), vespalib::IDestructorCallback::SP());
}

FeedHandler::RPC::Result
FeedHandler::receive(const Packet &packet)
{
    // Called directly when replaying transaction log (by fnet thread).
    FeedStateSP state = getFeedState();
    auto wrap = make_shared<PacketWrapper>(packet, _tlsReplayProgress.get());
    state->receive(wrap, _writeService.master());
    wrap->gate.await();
    return wrap->result;
}

void
FeedHandler::eof()
{
    // Only called by visit, subscription gets one or more inSync() callbacks.
    _writeService.master().execute(makeLambdaTask([this]() { performEof(); }));
}

void
FeedHandler::performPruneRemovedDocuments(PruneRemovedDocumentsOperation &pruneOp)
{
    const LidVectorContext::SP lids_to_remove = pruneOp.getLidsToRemove();
    vespalib::IDestructorCallback::SP onDone;
    if (lids_to_remove && lids_to_remove->getNumLids() != 0) {
        appendOperation(pruneOp, onDone);
        _activeFeedView->handlePruneRemovedDocuments(pruneOp, onDone);
    }
}

void
FeedHandler::syncTls(SerialNum syncTo)
{
    {
        std::lock_guard<std::mutex> guard(_syncLock);
        if (_syncedSerialNum >= syncTo)
            return;
    }
    if (!_allowSync) {
        throw IllegalStateException(make_string("Attempted to sync TLS to token %" PRIu64 " at wrong time.", syncTo));
    }
    SerialNum syncedTo(_tlsWriter->sync(syncTo));
    {
        std::lock_guard<std::mutex> guard(_syncLock);
        if (_syncedSerialNum < syncedTo) 
            _syncedSerialNum = syncedTo;
    }
}

vespalib::steady_time
FeedHandler::get_heart_beat_time() const
{
    return _heart_beat_time.load(std::memory_order_relaxed);
}

FeedHandlerStats
FeedHandler::get_stats(bool reset_min_max) const {
    std::lock_guard guard(_stats_lock);
    auto result = _stats;
    if (reset_min_max) {
        _stats.reset_min_max();
    }
    return result;
}

} // namespace proton
