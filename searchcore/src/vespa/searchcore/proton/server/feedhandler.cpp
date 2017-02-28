// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedhandler.h"
#include "feedstates.h"
#include "replaypacketdispatcher.h"
#include "tlcproxy.h"
#include "ddbstate.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <vespa/documentapi/messagebus/messages/feedreply.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentreply.h>
#include <vespa/searchcore/proton/common/bucketfactory.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/persistenceengine/transport_latch.h>
#include <vespa/searchcore/proton/bucketdb/ibucketdbhandler.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.feedhandler");

using document::BucketId;
using document::Document;
using document::DocumentTypeRepo;
using documentapi::DocumentProtocol;
using documentapi::DocumentReply;
using documentapi::FeedReply;
using documentapi::RemoveDocumentReply;
using documentapi::UpdateDocumentReply;
using storage::spi::PartitionId;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Timestamp;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::Executor;
using vespalib::IllegalStateException;
using vespalib::ThreadStackExecutorBase;
using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::make_string;
using vespalib::MonitorGuard;
using vespalib::LockGuard;

namespace proton {


namespace {
void
setUpdateWasFound(mbus::Reply &reply, bool was_found)
{
    assert(static_cast<DocumentReply&>(reply).getType() ==
           DocumentProtocol::REPLY_UPDATEDOCUMENT);
    UpdateDocumentReply &update_rep = static_cast<UpdateDocumentReply&>(reply);
    update_rep.setWasFound(was_found);
}


void
setRemoveWasFound(mbus::Reply &reply, bool was_found)
{
    assert(static_cast<DocumentReply&>(reply).getType() ==
           DocumentProtocol::REPLY_REMOVEDOCUMENT);
    RemoveDocumentReply &remove_rep = static_cast<RemoveDocumentReply&>(reply);
    remove_rep.setWasFound(was_found);
}


bool
ignoreOperation(const DocumentOperation &op)
{
    return op.getPrevTimestamp() != 0 &&
        op.getTimestamp() < op.getPrevTimestamp();
}


}  // namespace


void FeedHandler::TlsMgrWriter::storeOperation(const FeedOperation &op) {
    TlcProxy(*_tls_mgr.getSession(), _tlsDirectWriter).storeOperation(op);
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
            LOG(spam,
                "Tls sync complete, reached %" PRIu64", returning",
                syncedTo);
            return syncedTo;
        }
        LOG(spam,
            "Tls sync incomplete, reached %" PRIu64 ", retrying",
            syncedTo);
    }
    throw vespalib::IllegalStateException(
            vespalib::make_string(
                    "Failed to sync TLS to token %" PRIu64 ".",
                    syncTo));
    return 0;
}

void
FeedHandler::doHandleOperation(FeedToken token, FeedOperation::UP op)
{
    assert(_writeService.master().isCurrentThread());
    vespalib::LockGuard guard(_feedLock);
    _feedState->handleOperation(token, std::move(op));
}

namespace {
template <typename ResultType>
void configRejected(FeedToken *token, DocTypeName docTypeName) {
    if (token) {
        vespalib::string str =
            make_string("Feed rejected for documenttype '%s'"
                        " due to incompatible changes to search definition.",
                        docTypeName.toString().c_str());
        token->setResult(
                ResultUP(new ResultType(Result::PERMANENT_ERROR, str)), false);
        token->fail(documentapi::DocumentProtocol::ERROR_REJECTED, str);
    }
}

void notifyConfigRejected(FeedToken *token, FeedOperation::Type type,
                          DocTypeName docTypeName) {
    if (type == FeedOperation::REMOVE) {
        configRejected<RemoveResult>(token, docTypeName);
    } else if ((type == FeedOperation::UPDATE42) || (type == FeedOperation::UPDATE)) {
        configRejected<UpdateResult>(token, docTypeName);
    } else {
        configRejected<Result>(token, docTypeName);
    }
}
}  // namespace


void FeedHandler::performPut(FeedToken::UP token, PutOperation &op) {
    op.assertValid();
    _activeFeedView->preparePut(op);
    if (ignoreOperation(op)) {
        LOG(debug, "performPut(): ignoreOperation: docId(%s), "
            "timestamp(%" PRIu64 "), prevTimestamp(%" PRIu64 ")",
            op.getDocument()->getId().toString().c_str(),
            (uint64_t)op.getTimestamp(),
            (uint64_t)op.getPrevTimestamp());
        if (token.get() != NULL) {
            token->setResult(ResultUP(new Result), false);
            token->ack(op.getType(), _metrics);
        }
        return;
    }
    storeOperation(op);
    if (token.get() != NULL) {
        token->setResult(ResultUP(new Result), false);
        if (token->shouldTrace(1)) {
            const document::DocumentId &docId = op.getDocument()->getId();
            const document::GlobalId &gid = docId.getGlobalId();
            token->trace(1,
                         make_string(
                                 "Indexing document '%s' (gid = '%s',"
                                 " lid = '%u,%u' prevlid = '%u,%u').",
                                 docId.toString().c_str(),
                                 gid.toString().c_str(),
                                 op.getSubDbId(),
                                 op.getLid(),
                                 op.getPrevSubDbId(),
                                 op.getPrevLid()));
        }
    }
    _activeFeedView->handlePut(token.get(), op);
}


void
FeedHandler::performUpdate(FeedToken::UP token, UpdateOperation &op)
{
    _activeFeedView->prepareUpdate(op);
    if (op.getPrevDbDocumentId().valid() && !op.getPrevMarkedAsRemoved()) {
        performInternalUpdate(std::move(token), op);
    } else if (op.getUpdate()->getCreateIfNonExistent()) {
        createNonExistingDocument(std::move(token), op);
    } else {
        if (token.get() != NULL) {
            token->setResult(ResultUP(new UpdateResult(Timestamp(0))), false);
            if (token->shouldTrace(1)) {
                const document::DocumentId &docId = op.getUpdate()->getId();
                 token->trace(1,
                             make_string(
                                         "Document '%s' not found."
                                         " Update operation ignored",
                                         docId.toString().c_str()));
            }
            setUpdateWasFound(token->getReply(), false);
            token->ack(op.getType(), _metrics);
        }
    }
}


void
FeedHandler::performInternalUpdate(FeedToken::UP token, UpdateOperation &op)
{
    storeOperation(op);
    if (token.get() != NULL) {
        token->setResult(ResultUP(new UpdateResult(op.getPrevTimestamp())),
                         true);
        if (token->shouldTrace(1)) {
            const document::DocumentId &docId = op.getUpdate()->getId();
            const document::GlobalId &gid = docId.getGlobalId();
            token->trace(1,
                         make_string(
                                     "Updating document '%s' (gid = '%s',"
                                     " lid = '%u,%u' prevlid = '%u,%u').",
                                     docId.toString().c_str(),
                                     gid.toString().c_str(),
                                     op.getSubDbId(),
                                     op.getLid(),
                                     op.getPrevSubDbId(),
                                     op.getPrevLid()));
        }
        setUpdateWasFound(token->getReply(), true);
    }
    _activeFeedView->handleUpdate(token.get(), op);
}


void
FeedHandler::createNonExistingDocument(FeedToken::UP token, const UpdateOperation &op)
{
    Document::SP doc(new Document(op.getUpdate()->getType(),
                                  op.getUpdate()->getId()));
    doc->setRepo(*_activeFeedView->getDocumentTypeRepo());
    op.getUpdate()->applyTo(*doc);
    PutOperation putOp(op.getBucketId(),
                       op.getTimestamp(),
                       doc);
    _activeFeedView->preparePut(putOp);
    storeOperation(putOp);
    if (token.get() != NULL) {
        token->setResult(ResultUP(new UpdateResult(putOp.getTimestamp())), true);
        if (token->shouldTrace(1)) {
            const document::DocumentId &docId = putOp.getDocument()->getId();
            const document::GlobalId &gid = docId.getGlobalId();
            token->trace(1, make_string("Creating non-existing document '%s' for update (gid='%s',"
                                     " lid= %u,%u' prevlid='%u,%u').",
                                     docId.toString().c_str(),
                                     gid.toString().c_str(),
                                     putOp.getSubDbId(),
                                     putOp.getLid(),
                                     putOp.getPrevSubDbId(),
                                     putOp.getPrevLid()));
        }
        setUpdateWasFound(token->getReply(), true);
    }
    TransportLatch latch(1);
    FeedToken putToken(latch, mbus::Reply::UP(new FeedReply(DocumentProtocol::REPLY_PUTDOCUMENT)));
    _activeFeedView->handlePut(&putToken, putOp);
    latch.await();
    if (token.get() != NULL) {
        token->ack();
    }
}


void FeedHandler::performRemove(FeedToken::UP token, RemoveOperation &op) {
    _activeFeedView->prepareRemove(op);
    if (ignoreOperation(op)) {
        LOG(debug, "performRemove(): ignoreOperation: docId(%s), "
            "timestamp(%" PRIu64 "), prevTimestamp(%" PRIu64 ")",
            op.getDocumentId().toString().c_str(),
            (uint64_t)op.getTimestamp(),
            (uint64_t)op.getPrevTimestamp());
        if (token.get() != NULL) {
            token->setResult(ResultUP(new RemoveResult(false)), false);
            token->ack(op.getType(), _metrics);
        }
        return;
    }
    if (op.getPrevDbDocumentId().valid()) {
        assert(op.getValidNewOrPrevDbdId());
        assert(op.notMovingLidInSameSubDb());
        storeOperation(op);
        if (token.get() != NULL) {
            bool documentWasFound = !op.getPrevMarkedAsRemoved();
            token->setResult(ResultUP(new RemoveResult(documentWasFound)),
                             documentWasFound);
            if (token->shouldTrace(1)) {
                const document::DocumentId &docId = op.getDocumentId();
                const document::GlobalId &gid = docId.getGlobalId();
                token->trace(1,
                             make_string(
                                     "Removing document '%s' (gid = '%s',"
                                     " lid = '%u,%u' prevlid = '%u,%u').",
                                     docId.toString().c_str(),
                                     gid.toString().c_str(),
                                     op.getSubDbId(),
                                     op.getLid(),
                                     op.getPrevSubDbId(),
                                     op.getPrevLid()));
            }
            setRemoveWasFound(token->getReply(), documentWasFound);
        }
        _activeFeedView->handleRemove(token.get(), op);
    } else if (op.hasDocType()) {
        assert(op.getDocType() == _docTypeName.getName());
        storeOperation(op);
        if (token.get() != NULL) {
            token->setResult(ResultUP(new RemoveResult(false)), false);
            if (token->shouldTrace(1)) {
                token->trace(1,
                             make_string(
                                     "Document '%s' not found."
                                     " Remove operation stored.",
                                     op.getDocumentId().toString().c_str()));
            }
            setRemoveWasFound(token->getReply(), false);
        }
        _activeFeedView->handleRemove(token.get(), op);
    } else {
        if (token.get() != NULL) {
            token->setResult(ResultUP(new RemoveResult(false)), false);
            if (token->shouldTrace(1)) {
                token->trace(1,
                             make_string(
                                     "Document '%s' not found."
                                     " Remove operation ignored",
                                     op.getDocumentId().toString().c_str()));
            }
            setRemoveWasFound(token->getReply(), false);
            token->ack(op.getType(), _metrics);
        }
    }
}

void
FeedHandler::performGarbageCollect(FeedToken::UP token)
{
    _owner.performWipeHistory();
    if (token.get() != NULL) {
        token->ack();
    }
}


void
FeedHandler::performCreateBucket(FeedToken::UP token,
                                 CreateBucketOperation &op)
{
    storeOperation(op);
    _bucketDBHandler->handleCreateBucket(op.getBucketId());
    if (token) {
        token->ack();
    }
}


void FeedHandler::performDeleteBucket(FeedToken::UP token,
                                      DeleteBucketOperation &op) {
    _activeFeedView->prepareDeleteBucket(op);
    storeOperation(op);
    // Delete documents in bucket
    _activeFeedView->handleDeleteBucket(op);
    // Delete bucket itself, should no longer have documents.
    _bucketDBHandler->handleDeleteBucket(op.getBucketId());
    if (token) {
        token->ack();
    }
}


void FeedHandler::performSplit(FeedToken::UP token, SplitBucketOperation &op) {
    storeOperation(op);
    _bucketDBHandler->handleSplit(op.getSerialNum(),
                                  op.getSource(),
                                  op.getTarget1(),
                                  op.getTarget2());
    if (token) {
        token->ack();
    }
}


void FeedHandler::performJoin(FeedToken::UP token, JoinBucketsOperation &op) {
    storeOperation(op);
    _bucketDBHandler->handleJoin(op.getSerialNum(),
                                 op.getSource1(),
                                 op.getSource2(),
                                 op.getTarget());
    if (token) {
        token->ack();
    }
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
    LOG(debug,
        "Visiting done for transaction log domain '%s', eof received",
        _tlsMgr.getDomainName().c_str());
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
    } catch (const vespalib::IllegalStateException & e) {
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
        vespalib::LockGuard guard(_feedLock);
        state = _feedState;
    }
    return state;
}


void
FeedHandler::changeFeedState(FeedState::SP newState)
{
    vespalib::LockGuard guard(_feedLock);
    changeFeedState(newState, guard);
}


void
FeedHandler::changeFeedState(FeedState::SP newState,
                             const vespalib::LockGuard &)
{
    LOG(debug,
        "Change feed state from '%s' -> '%s'",
        _feedState->getName().c_str(), newState->getName().c_str());
    _feedState = newState;
}


FeedHandler::FeedHandler(IThreadingService &writeService,
                         const vespalib::string &tlsSpec,
                         const DocTypeName &docTypeName,
                         PerDocTypeFeedMetrics &metrics,
                         DDBState &state,
                         IOwner &owner,
                         const IResourceWriteFilter &writeFilter,
                         IReplayConfig &replayConfig,
                         search::transactionlog::Writer *tlsDirectWriter,
                         TlsWriter *tls_writer)
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
      _tlsMgrWriter(_tlsMgr, tlsDirectWriter),
      _tlsWriter(tls_writer ? *tls_writer : _tlsMgrWriter),
      _tlsReplayProgress(),
      _serialNum(0),
      _prunedSerialNum(0),
      _delayedPrune(false),
      _feedLock(),
      _feedState(std::make_shared<InitState>(getDocTypeName())),
      _activeFeedView(NULL),
      _bucketDBHandler(nullptr),
      _metrics(metrics),
      _syncLock(),
      _syncedSerialNum(0),
      _allowSync(false)
{
}


FeedHandler::~FeedHandler()
{
}


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
FeedHandler::replayTransactionLog(SerialNum flushedIndexMgrSerial,
                                  SerialNum flushedSummaryMgrSerial,
                                  SerialNum oldestFlushedSerial,
                                  SerialNum newestFlushedSerial,
                                  ConfigStore &config_store)
{
    (void) newestFlushedSerial;
    assert(_activeFeedView);
    assert(_bucketDBHandler);
    FeedState::SP state = std::make_shared<ReplayTransactionLogState>
                          (getDocTypeName(),
                           _activeFeedView,
                           *_bucketDBHandler,
                           _replayConfig,
                           config_store);
    changeFeedState(state);
    // Resurrected attribute vector might cause oldestFlushedSerial to
    // be lower than _prunedSerialNum, so don't warn for now.
    (void) oldestFlushedSerial;
    assert(_serialNum >= newestFlushedSerial);

    TransactionLogManager::prepareReplay(
            _tlsMgr.getClient(),
            _docTypeName.getName(),
            flushedIndexMgrSerial,
            flushedSummaryMgrSerial,
            config_store);

    _tlsReplayProgress = _tlsMgr.startReplay(_prunedSerialNum, _serialNum, *this);
}


void
FeedHandler::flushDone(SerialNum flushedSerial)
{
    // Called by flush worker thread after performing a flush task
    _writeService.master().execute(
            makeTask(
                    makeClosure(
                            this,
                            &FeedHandler::performFlushDone,
                            flushedSerial)));
}

void FeedHandler::changeToNormalFeedState(void) {
    changeFeedState(FeedState::SP(new NormalState(*this)));
}

void
FeedHandler::waitForReplayDone()
{
    _tlsMgr.waitForReplayDone();
}

void FeedHandler::setReplayDone() {
    _tlsMgr.changeReplayDone();
}

bool FeedHandler::getReplayDone() const {
    return _tlsMgr.getReplayDone();
}

bool
FeedHandler::isDoingReplay() const {
    return _tlsMgr.isDoingReplay();
}

bool FeedHandler::getTransactionLogReplayDone() const {
    return _tlsMgr.getReplayDone();
}

void FeedHandler::storeOperation(FeedOperation &op) {
    if (!op.getSerialNum()) {
        op.setSerialNum(incSerialNum());
    }
    _tlsWriter.storeOperation(op);
}

void FeedHandler::tlsPrune(SerialNum oldest_to_keep) {
    if (!_tlsWriter.erase(oldest_to_keep)) {
        throw vespalib::IllegalStateException(vespalib::make_string(
                        "Failed to prune TLS to token %" PRIu64 ".",
                        oldest_to_keep));
    }
    _prunedSerialNum = oldest_to_keep;
}

namespace {

bool
isRejectableFeedOperation(FeedOperation::Type type)
{
    return type == FeedOperation::PUT || type == FeedOperation::UPDATE42 || type == FeedOperation::UPDATE;
}

template <typename ResultType>
void feedOperationRejected(FeedToken *token, const vespalib::string &opType, const vespalib::string &docId,
                           DocTypeName docTypeName, const vespalib::string &rejectMessage)
{
    if (token) {
        vespalib::string message = make_string("%s operation rejected for document '%s' of type '%s': '%s'",
                                               opType.c_str(), docId.c_str(), docTypeName.toString().c_str(), rejectMessage.c_str());
        token->setResult(ResultUP(new ResultType(Result::RESOURCE_EXHAUSTED, message)), false);
        token->fail(documentapi::DocumentProtocol::ERROR_REJECTED, message);
    }
}

void
notifyFeedOperationRejected(FeedToken *token, const FeedOperation &op,
                            DocTypeName docTypeName, const vespalib::string &rejectMessage)
{
    if ((op.getType() == FeedOperation::UPDATE42) || (op.getType() == FeedOperation::UPDATE)) {
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
FeedHandler::considerWriteOperationForRejection(FeedToken *token, const FeedOperation &op)
{
    if (_owner.isFeedBlockedByRejectedConfig()) {
        notifyConfigRejected(token, op.getType(), _docTypeName);
        return true;
    }
    if (!_writeFilter.acceptWriteOperation() && isRejectableFeedOperation(op.getType())) {
        IResourceWriteFilter::State state = _writeFilter.getAcceptState();
        if (!state.acceptWriteOperation()) {
            notifyFeedOperationRejected(token, op, _docTypeName, state.message());
            return true;
        }
    }
    return false;
}

void
FeedHandler::performOperation(FeedToken::UP token, FeedOperation::UP op)
{
    if (considerWriteOperationForRejection(token.get(), *op)) {
        return;
    }
    switch(op->getType()) {
    case FeedOperation::PUT:
        performPut(std::move(token), static_cast<PutOperation &>(*op));
        return;
    case FeedOperation::REMOVE:
        performRemove(std::move(token), static_cast<RemoveOperation &>(*op));
        return;
    case FeedOperation::UPDATE42:
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
    _writeService.master().execute(
            makeTask(makeClosure(this,
                                 &FeedHandler::doHandleOperation, token, std::move(op))));
}

void
FeedHandler::handleMove(MoveOperation &op)
{
    assert(_writeService.master().isCurrentThread());
    _activeFeedView->prepareMove(op);
    assert(op.getValidDbdId());
    assert(op.getValidPrevDbdId());
    assert(op.getSubDbId() != op.getPrevSubDbId());
    storeOperation(op);
    _activeFeedView->handleMove(op);
}


void
FeedHandler::heartBeat(void)
{
    assert(_writeService.master().isCurrentThread());
    if (_owner.isFeedBlockedByRejectedConfig())
        return;
    _activeFeedView->heartBeat(_serialNum);
}


void
FeedHandler::sync()
{
    _writeService.master().execute(makeTask(makeClosure(this, &FeedHandler::performSync)));
    _writeService.sync();
}


FeedHandler::RPC::Result
FeedHandler::receive(const Packet &packet)
{
    // Called directly when replaying transaction log
    // (by fnet thread).  Called via DocumentDB::recoverPacket() when
    // recovering from another node.
    FeedState::SP state = getFeedState();
    PacketWrapper::SP wrap(new PacketWrapper(packet, _tlsReplayProgress.get()));
    state->receive(wrap, _writeService.master());
    wrap->gate.await();
    return wrap->result;
}


void
FeedHandler::eof()
{
    // Only called by visit, subscription gets one or more inSync() callbacks.
    _writeService.master().execute(makeTask(makeClosure(this, &FeedHandler::performEof)));
}


void
FeedHandler::inSync()
{
    // Called by visit callback thread, when in sync
}


void
FeedHandler::
performPruneRemovedDocuments(PruneRemovedDocumentsOperation &pruneOp)
{
    const LidVectorContext::LP lids_to_remove = pruneOp.getLidsToRemove();
    if (lids_to_remove.get() && lids_to_remove->getNumLids() != 0) {
        storeOperation(pruneOp);
        _activeFeedView->handlePruneRemovedDocuments(pruneOp);
    }
}


void
FeedHandler::syncTls(SerialNum syncTo)
{
    {
        LockGuard guard(_syncLock);
        if (_syncedSerialNum >= syncTo)
            return;
    }
    if (!_allowSync) {
        throw vespalib::IllegalStateException(
                vespalib::make_string(
                        "Attempted to sync TLS to token %" PRIu64
                        " at wrong time.",
                        syncTo));
    }
    SerialNum syncedTo(_tlsWriter.sync(syncTo));
    {
        LockGuard guard(_syncLock);
        if (_syncedSerialNum < syncedTo) 
            _syncedSerialNum = syncedTo;
    }
}

void
FeedHandler::storeRemoteOperation(const FeedOperation &op)
{
    SerialNum serialNum(op.getSerialNum()); 
    assert(serialNum != 0);
    if (serialNum > _serialNum) {
        _tlsWriter.storeOperation(op);
        _serialNum = serialNum;
    }
}


} // namespace proton
