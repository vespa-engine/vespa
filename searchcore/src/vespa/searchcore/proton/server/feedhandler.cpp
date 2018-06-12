// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ddbstate.h"
#include "feedhandler.h"
#include "feedstates.h"
#include "i_feed_handler_owner.h"
#include "ifeedview.h"
#include "tlcproxy.h"
#include "configstore.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchcore/proton/bucketdb/ibucketdbhandler.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/persistenceengine/transport_latch.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/common/gatecallback.h>
#include <vespa/vespalib/util/exceptions.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.feedhandler");

using document::BucketId;
using document::Document;
using document::DocumentTypeRepo;
using storage::spi::PartitionId;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Timestamp;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::Executor;
using vespalib::IllegalStateException;
using vespalib::makeLambdaTask;
using vespalib::make_string;
using std::make_unique;
using std::make_shared;

namespace proton {

namespace {

bool
ignoreOperation(const DocumentOperation &op) {
    return (op.getPrevTimestamp() != 0) && (op.getTimestamp() < op.getPrevTimestamp());
}

}  // namespace

void FeedHandler::TlsMgrWriter::storeOperation(const FeedOperation &op, DoneCallback onDone) {
    TlcProxy(_tls_mgr.getDomainName(), *_tlsDirectWriter).storeOperation(op, std::move(onDone));
}
bool FeedHandler::TlsMgrWriter::erase(SerialNum oldest_to_keep) {
    return _tls_mgr.getSession()->erase(oldest_to_keep);
}

search::SerialNum
FeedHandler::TlsMgrWriter::sync(SerialNum syncTo)
{
    for (int retryCount = 0; retryCount < 10; ++retryCount) {
        SerialNum syncedTo(0);
        LOG(spam, "Trying tls sync(%" PRIu64 ")", syncTo);
        bool res = _tls_mgr.getSession()->sync(syncTo, syncedTo);
        if (!res) {
            LOG(spam, "Tls sync failed, retrying");
            sleep(1);
            continue;
        }
        if (syncedTo >= syncTo) {
            LOG(spam, "Tls sync complete, reached %" PRIu64", returning", syncedTo);
            return syncedTo;
        }
        LOG(spam, "Tls sync incomplete, reached %" PRIu64 ", retrying", syncedTo);
    }
    throw IllegalStateException(make_string("Failed to sync TLS to token %" PRIu64 ".", syncTo));
}

void
FeedHandler::doHandleOperation(FeedToken token, FeedOperation::UP op)
{
    assert(_writeService.master().isCurrentThread());
    std::lock_guard<std::mutex> guard(_feedLock);
    _feedState->handleOperation(std::move(token), std::move(op));
}

void FeedHandler::performPut(FeedToken token, PutOperation &op) {
    op.assertValid();
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
    storeOperation(op, token);
    if (token) {
        token->setResult(make_unique<Result>(), false);
    }
    _activeFeedView->handlePut(std::move(token), op);
}


void
FeedHandler::performUpdate(FeedToken token, UpdateOperation &op)
{
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
    storeOperation(op, token);
    if (token) {
        token->setResult(make_unique<UpdateResult>(op.getPrevTimestamp()), true);
    }
    _activeFeedView->handleUpdate(std::move(token), op);
}


void
FeedHandler::createNonExistingDocument(FeedToken token, const UpdateOperation &op)
{
    auto doc = make_shared<Document>(op.getUpdate()->getType(), op.getUpdate()->getId());
    doc->setRepo(*_activeFeedView->getDocumentTypeRepo());
    op.getUpdate()->applyTo(*doc);
    PutOperation putOp(op.getBucketId(), op.getTimestamp(), doc);
    _activeFeedView->preparePut(putOp);
    storeOperation(putOp, token);
    if (token) {
        token->setResult(make_unique<UpdateResult>(putOp.getTimestamp()), true);
    }
    TransportLatch latch(1);
    _activeFeedView->handlePut(feedtoken::make(latch), putOp);
    latch.await();
}


void FeedHandler::performRemove(FeedToken token, RemoveOperation &op) {
    _activeFeedView->prepareRemove(op);
    if (ignoreOperation(op)) {
        LOG(debug, "performRemove(): ignoreOperation: docId(%s), timestamp(%" PRIu64 "), prevTimestamp(%" PRIu64 ")",
            op.getDocumentId().toString().c_str(), (uint64_t)op.getTimestamp(), (uint64_t)op.getPrevTimestamp());
        if (token) {
            token->setResult(make_unique<RemoveResult>(false), false);
        }
        return;
    }
    if (op.getPrevDbDocumentId().valid()) {
        assert(op.getValidNewOrPrevDbdId());
        assert(op.notMovingLidInSameSubDb());
        storeOperation(op, token);
        if (token) {
            bool documentWasFound = !op.getPrevMarkedAsRemoved();
            token->setResult(make_unique<RemoveResult>(documentWasFound), documentWasFound);
        }
        _activeFeedView->handleRemove(std::move(token), op);
    } else if (op.hasDocType()) {
        assert(op.getDocType() == _docTypeName.getName());
        storeOperation(op, token);
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
    storeOperation(op, token);
    _bucketDBHandler->handleCreateBucket(op.getBucketId());
}

void FeedHandler::performDeleteBucket(FeedToken token, DeleteBucketOperation &op) {
    _activeFeedView->prepareDeleteBucket(op);
    storeOperation(op, token);
    // Delete documents in bucket
    _activeFeedView->handleDeleteBucket(op);
    // Delete bucket itself, should no longer have documents.
    _bucketDBHandler->handleDeleteBucket(op.getBucketId());

}

void FeedHandler::performSplit(FeedToken token, SplitBucketOperation &op) {
    storeOperation(op, token);
    _bucketDBHandler->handleSplit(op.getSerialNum(), op.getSource(), op.getTarget1(), op.getTarget2());
}

void FeedHandler::performJoin(FeedToken token, JoinBucketsOperation &op) {
    storeOperation(op, token);
    _bucketDBHandler->handleJoin(op.getSerialNum(), op.getSource1(), op.getSource2(), op.getTarget());
}

void
FeedHandler::performSync()
{
    assert(_writeService.master().isCurrentThread());
    _activeFeedView->sync();
}

void
FeedHandler::performEof()
{
    assert(_writeService.master().isCurrentThread());
    _writeService.sync();
    LOG(debug, "Visiting done for transaction log domain '%s', eof received", _tlsMgr.getDomainName().c_str());
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


FeedState::SP
FeedHandler::getFeedState() const
{
    FeedState::SP state;
    {
        std::lock_guard<std::mutex> guard(_feedLock);
        state = _feedState;
    }
    return state;
}


void
FeedHandler::changeFeedState(FeedState::SP newState)
{
    std::lock_guard<std::mutex> guard(_feedLock);
    changeFeedState(std::move(newState), guard);
}


void
FeedHandler::changeFeedState(FeedState::SP newState, const std::lock_guard<std::mutex> &)
{
    LOG(debug, "Change feed state from '%s' -> '%s'", _feedState->getName().c_str(), newState->getName().c_str());
    _feedState = newState;
}


FeedHandler::FeedHandler(IThreadingService &writeService,
                         const vespalib::string &tlsSpec,
                         const DocTypeName &docTypeName,
                         DDBState &state,
                         IFeedHandlerOwner &owner,
                         const IResourceWriteFilter &writeFilter,
                         IReplayConfig &replayConfig,
                         search::transactionlog::Writer & tlsDirectWriter,
                         TlsWriter * tlsWriter)
    : search::transactionlog::TransLogClient::Session::Callback(),
      IDocumentMoveHandler(),
      IPruneRemovedDocumentsHandler(),
      IHeartBeatHandler(),
      IOperationStorer(),
      IGetSerialNum(),
      _writeService(writeService),
      _docTypeName(docTypeName),
      _state(state),
      _owner(owner),
      _writeFilter(writeFilter),
      _replayConfig(replayConfig),
      _tlsMgr(tlsSpec, docTypeName.getName()),
      _tlsMgrWriter(_tlsMgr, &tlsDirectWriter),
      _tlsWriter(tlsWriter ? *tlsWriter : _tlsMgrWriter),
      _tlsReplayProgress(),
      _serialNum(0),
      _prunedSerialNum(0),
      _delayedPrune(false),
      _feedLock(),
      _feedState(make_shared<InitState>(getDocTypeName())),
      _activeFeedView(nullptr),
      _repo(nullptr),
      _documentType(nullptr),
      _bucketDBHandler(nullptr),
      _syncLock(),
      _syncedSerialNum(0),
      _allowSync(false)
{ }


FeedHandler::~FeedHandler() = default;

// Called on DocumentDB creatio
void
FeedHandler::init(SerialNum oldestConfigSerial)
{
    _tlsMgr.init(oldestConfigSerial, _prunedSerialNum, _serialNum);
    _allowSync = true;
    syncTls(_serialNum);
}


void
FeedHandler::close()
{
    if (_allowSync) {
        syncTls(_serialNum);
    }
    _allowSync = false;
    _tlsMgr.close();
}

void
FeedHandler::replayTransactionLog(SerialNum flushedIndexMgrSerial, SerialNum flushedSummaryMgrSerial,
                                  SerialNum oldestFlushedSerial, SerialNum newestFlushedSerial,
                                  ConfigStore &config_store)
{
    (void) newestFlushedSerial;
    assert(_activeFeedView);
    assert(_bucketDBHandler);
    FeedState::SP state = make_shared<ReplayTransactionLogState>
                          (getDocTypeName(), _activeFeedView, *_bucketDBHandler, _replayConfig, config_store);
    changeFeedState(state);
    // Resurrected attribute vector might cause oldestFlushedSerial to
    // be lower than _prunedSerialNum, so don't warn for now.
    (void) oldestFlushedSerial;
    assert(_serialNum >= newestFlushedSerial);

    TransactionLogManager::prepareReplay(_tlsMgr.getClient(), _docTypeName.getName(),
                                         flushedIndexMgrSerial, flushedSummaryMgrSerial, config_store);

    _tlsReplayProgress = _tlsMgr.startReplay(_prunedSerialNum, _serialNum, *this);
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
FeedHandler::storeOperation(const FeedOperation &op, TlsWriter::DoneCallback onDone) {
    if (!op.getSerialNum()) {
        const_cast<FeedOperation &>(op).setSerialNum(incSerialNum());
    }
    _tlsWriter.storeOperation(op, std::move(onDone));
}

void
FeedHandler::storeOperationSync(const FeedOperation &op) {
    vespalib::Gate gate;
    storeOperation(op, make_shared<search::GateCallback>(gate));
    gate.await();
}

void
FeedHandler::tlsPrune(SerialNum oldest_to_keep) {
    if (!_tlsWriter.erase(oldest_to_keep)) {
        throw IllegalStateException(make_string("Failed to prune TLS to token %" PRIu64 ".", oldest_to_keep));
    }
    _prunedSerialNum = oldest_to_keep;
}

namespace {

bool
isRejectableFeedOperation(FeedOperation::Type type)
{
    return type == FeedOperation::PUT || type == FeedOperation::UPDATE_42 || type == FeedOperation::UPDATE;
}

template <typename ResultType>
void feedOperationRejected(FeedToken & token, const vespalib::string &opType, const vespalib::string &docId,
                           const DocTypeName & docTypeName, const vespalib::string &rejectMessage)
{
    if (token) {
        auto message = make_string("%s operation rejected for document '%s' of type '%s': '%s'",
                                   opType.c_str(), docId.c_str(), docTypeName.toString().c_str(), rejectMessage.c_str());
        token->setResult(make_unique<ResultType>(Result::RESOURCE_EXHAUSTED, message), false);
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

}

bool
FeedHandler::considerWriteOperationForRejection(FeedToken & token, const FeedOperation &op)
{
    if (!_writeFilter.acceptWriteOperation() && isRejectableFeedOperation(op.getType())) {
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
            op.deserializeUpdate(*_repo);
        } catch (document::FieldNotFoundException &e) {
            if (token) {
                auto message = make_string("Update operation rejected for document '%s' of type '%s': 'Field not found'",
                                           update.getId().toString().c_str(), _docTypeName.toString().c_str());
                token->setResult(make_unique<UpdateResult>(Result::TRANSIENT_ERROR, message), false);
                token->fail();
            }
            return true;
        } catch (document::DocumentTypeNotFoundException &e) {
            auto message = make_string("Update operation rejected for document '%s' of type '%s': 'Uknown document type', expected '%s'",
                                       update.getId().toString().c_str(),
                                       e.getDocumentTypeName().c_str(),
                                       _docTypeName.toString().c_str());
            token->setResult(make_unique<UpdateResult>(Result::TRANSIENT_ERROR, message), false);
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
    _writeService.master().execute(makeLambdaTask([this, token = std::move(token), op = std::move(op)]() mutable {
        doHandleOperation(std::move(token), std::move(op));
    }));
}

void
FeedHandler::handleMove(MoveOperation &op, std::shared_ptr<search::IDestructorCallback> moveDoneCtx)
{
    assert(_writeService.master().isCurrentThread());
    _activeFeedView->prepareMove(op);
    assert(op.getValidDbdId());
    assert(op.getValidPrevDbdId());
    assert(op.getSubDbId() != op.getPrevSubDbId());
    storeOperation(op, moveDoneCtx);
    _activeFeedView->handleMove(op, std::move(moveDoneCtx));
}

void
FeedHandler::heartBeat()
{
    assert(_writeService.master().isCurrentThread());
    _activeFeedView->heartBeat(_serialNum);
}

void
FeedHandler::sync()
{
    _writeService.master().execute(makeLambdaTask([this]() { performSync(); }));
    _writeService.sync();
}

FeedHandler::RPC::Result
FeedHandler::receive(const Packet &packet)
{
    // Called directly when replaying transaction log
    // (by fnet thread).  Called via DocumentDB::recoverPacket() when
    // recovering from another node.
    FeedState::SP state = getFeedState();
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
FeedHandler::
performPruneRemovedDocuments(PruneRemovedDocumentsOperation &pruneOp)
{
    const LidVectorContext::SP lids_to_remove = pruneOp.getLidsToRemove();
    if (lids_to_remove && lids_to_remove->getNumLids() != 0) {
        storeOperationSync(pruneOp);
        _activeFeedView->handlePruneRemovedDocuments(pruneOp);
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
    SerialNum syncedTo(_tlsWriter.sync(syncTo));
    {
        std::lock_guard<std::mutex> guard(_syncLock);
        if (_syncedSerialNum < syncedTo) 
            _syncedSerialNum = syncedTo;
    }
}

} // namespace proton
