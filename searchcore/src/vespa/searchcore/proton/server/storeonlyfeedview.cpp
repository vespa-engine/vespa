// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forcecommitcontext.h"
#include "ireplayconfig.h"
#include "operationdonecontext.h"
#include "putdonecontext.h"
#include "removedonecontext.h"
#include "storeonlyfeedview.h"
#include "updatedonecontext.h"
#include "remove_batch_done_context.h"
#include <vespa/searchcore/proton/common/commit_time_tracker.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/documentmetastore/ilidreusedelayer.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.storeonlyfeedview");

using document::BucketId;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using search::index::Schema;
using vespalib::makeLambdaTask;
using search::IDestructorCallback;
using search::SerialNum;
using storage::spi::BucketInfoResult;
using storage::spi::Timestamp;
using vespalib::IllegalStateException;
using vespalib::make_string;

namespace proton {

namespace {

class PutDoneContextForMove : public PutDoneContext {
private:
    IDestructorCallback::SP _moveDoneCtx;

public:
    PutDoneContextForMove(FeedToken token, IGidToLidChangeHandler &gidToLidChangeHandler,
                          const document::GlobalId &gid, uint32_t lid, search::SerialNum serialNum,
                          bool enableNotifyPut, IDestructorCallback::SP moveDoneCtx)
        : PutDoneContext(std::move(token), gidToLidChangeHandler, gid, lid, serialNum, enableNotifyPut),
          _moveDoneCtx(std::move(moveDoneCtx))
    {}
    ~PutDoneContextForMove() = default;
};

std::shared_ptr<PutDoneContext>
createPutDoneContext(FeedToken token,
                     IGidToLidChangeHandler &gidToLidChangeHandler,
                     const document::GlobalId &gid, uint32_t lid,
                     SerialNum serialNum, bool enableNotifyPut,
                     IDestructorCallback::SP moveDoneCtx)
{
    std::shared_ptr<PutDoneContext> result;
    if (moveDoneCtx) {
        result = std::make_shared<PutDoneContextForMove>(std::move(token), gidToLidChangeHandler, gid, lid, serialNum, enableNotifyPut, std::move(moveDoneCtx));
    } else {
        result = std::make_shared<PutDoneContext>(std::move(token), gidToLidChangeHandler, gid, lid, serialNum, enableNotifyPut);
    }
    return result;
}

std::shared_ptr<PutDoneContext>
createPutDoneContext(FeedToken token, IGidToLidChangeHandler &gidToLidChangeHandler,
                     const document::GlobalId &gid, uint32_t lid, SerialNum serialNum, bool enableNotifyPut)
{
    return createPutDoneContext(token, gidToLidChangeHandler, gid, lid, serialNum, enableNotifyPut, IDestructorCallback::SP());
}

std::shared_ptr<UpdateDoneContext>
createUpdateDoneContext(FeedToken token, const DocumentUpdate::SP &upd)
{
    return std::make_shared<UpdateDoneContext>(std::move(token), upd);
}

void setPrev(DocumentOperation &op, const documentmetastore::IStore::Result &result,
             uint32_t subDbId, bool markedAsRemoved)
{
    if (result._found) {
        op.setPrevDbDocumentId(DbDocumentId(subDbId, result._lid));
        op.setPrevMarkedAsRemoved(markedAsRemoved);
        op.setPrevTimestamp(result._timestamp);
    }
}

class RemoveDoneContextForMove : public RemoveDoneContext {
private:
    IDestructorCallback::SP _moveDoneCtx;

public:
    RemoveDoneContextForMove(FeedToken token, vespalib::Executor &executor, IDocumentMetaStore &documentMetaStore,
                             PendingNotifyRemoveDone &&pendingNotifyRemoveDone,
                             uint32_t lid, IDestructorCallback::SP moveDoneCtx)
        : RemoveDoneContext(std::move(token), executor, documentMetaStore, std::move(pendingNotifyRemoveDone) ,lid),
          _moveDoneCtx(std::move(moveDoneCtx))
    {}
    ~RemoveDoneContextForMove() = default;
};

std::shared_ptr<RemoveDoneContext>
createRemoveDoneContext(FeedToken token, vespalib::Executor &executor, IDocumentMetaStore &documentMetaStore,
                        PendingNotifyRemoveDone &&pendingNotifyRemoveDone,
                        uint32_t lid, IDestructorCallback::SP moveDoneCtx)
{
    if (moveDoneCtx) {
        return std::make_shared<RemoveDoneContextForMove>
            (std::move(token), executor, documentMetaStore, std::move(pendingNotifyRemoveDone),
             lid, std::move(moveDoneCtx));
    } else {
        return std::make_shared<RemoveDoneContext>
            (std::move(token), executor, documentMetaStore, std::move(pendingNotifyRemoveDone), lid);
    }
}

std::vector<document::GlobalId> getGidsToRemove(const IDocumentMetaStore &metaStore,
                                                const LidVectorContext::LidVector &lidsToRemove)
{
    std::vector<document::GlobalId> gids;
    gids.reserve(lidsToRemove.size());
    for (const auto &lid : lidsToRemove) {
        document::GlobalId gid;
        if (metaStore.getGid(lid, gid)) {
            gids.emplace_back(gid);
        }
    }
    return gids;
}

void putMetaData(documentmetastore::IStore &meta_store, const DocumentId &doc_id,
                 const DocumentOperation &op, bool is_removed_doc)
{
    documentmetastore::IStore::Result putRes(
            meta_store.put(doc_id.getGlobalId(),
                           op.getBucketId(), op.getTimestamp(), op.getSerializedDocSize(), op.getLid()));
    if (!putRes.ok()) {
        throw IllegalStateException(
                make_string("Could not put <lid, gid> pair for %sdocument with id '%s' and gid '%s'",
                            is_removed_doc ? "removed " : "", doc_id.toString().c_str(),
                            doc_id.getGlobalId().toString().c_str()));
    }
    assert(op.getLid() == putRes._lid);
}

void removeMetaData(documentmetastore::IStore &meta_store, const DocumentId &doc_id,
                    const DocumentOperation &op, bool is_removed_doc) {
    assert(meta_store.validLid(op.getPrevLid()));
    assert(is_removed_doc == op.getPrevMarkedAsRemoved());
    const RawDocumentMetaData &meta(meta_store.getRawMetaData(op.getPrevLid()));
    assert(meta.getGid() == doc_id.getGlobalId());
    (void) meta;
    if (!meta_store.remove(op.getPrevLid())) {
        throw IllegalStateException(
                make_string("Could not remove <lid, gid> pair for %sdocument with id '%s' and gid '%s'",
                            is_removed_doc ? "removed " : "", doc_id.toString().c_str(),
                            doc_id.getGlobalId().toString().c_str()));
    }
}

void
moveMetaData(documentmetastore::IStore &meta_store, const DocumentId &doc_id, const DocumentOperation &op)
{
    (void) doc_id;
    assert(op.getLid() != op.getPrevLid());
    assert(meta_store.validLid(op.getPrevLid()));
    assert(!meta_store.validLid(op.getLid()));
    const RawDocumentMetaData &meta(meta_store.getRawMetaData(op.getPrevLid()));
    (void) meta;
    assert(meta.getGid() == doc_id.getGlobalId());
    assert(meta.getTimestamp() == op.getTimestamp());
    meta_store.move(op.getPrevLid(), op.getLid());
}

}  // namespace

StoreOnlyFeedView::StoreOnlyFeedView(const Context &ctx, const PersistentParams &params)
    : IFeedView(),
      FeedDebugger(),
      _summaryAdapter(ctx._summaryAdapter),
      _documentMetaStoreContext(ctx._documentMetaStoreContext),
      _repo(ctx._repo),
      _docType(nullptr),
      _lidReuseDelayer(ctx._lidReuseDelayer),
      _commitTimeTracker(ctx._commitTimeTracker),
      _pendingLidTracker(),
      _schema(ctx._schema),
      _writeService(ctx._writeService),
      _params(params),
      _metaStore(_documentMetaStoreContext->get()),
      _gidToLidChangeHandler(ctx._gidToLidChangeHandler)
{
    _docType = _repo->getDocumentType(_params._docTypeName.getName());
}

StoreOnlyFeedView::~StoreOnlyFeedView() = default;

void
StoreOnlyFeedView::sync()
{
    _writeService.summary().sync();
}

void
StoreOnlyFeedView::forceCommit(SerialNum serialNum)
{
    forceCommit(serialNum, std::make_shared<ForceCommitContext>(_writeService.master(), _metaStore));
}

void
StoreOnlyFeedView::forceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone)
{
    (void) serialNum;
    std::vector<uint32_t> lidsToReuse;
    lidsToReuse = _lidReuseDelayer.getReuseLids();
    if (!lidsToReuse.empty()) {
        onCommitDone->reuseLids(std::move(lidsToReuse));
    }
}

void
StoreOnlyFeedView::considerEarlyAck(FeedToken & token)
{
    if (_commitTimeTracker.hasVisibilityDelay() && token) {
        token.reset();
    }
}

void
StoreOnlyFeedView::putAttributes(SerialNum, Lid, const Document &, bool, OnPutDoneType) {}

void
StoreOnlyFeedView::putIndexedFields(SerialNum, Lid, const Document::SP &, bool, OnOperationDoneType) {}

void
StoreOnlyFeedView::preparePut(PutOperation &putOp)
{
    const DocumentId &docId = putOp.getDocument()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    documentmetastore::IStore::Result inspectResult = _metaStore.inspect(gid);
    putOp.setDbDocumentId(DbDocumentId(_params._subDbId, inspectResult._lid));
    assert(_params._subDbType != SubDbType::REMOVED);
    setPrev(putOp, inspectResult, _params._subDbId, false);
}

void
StoreOnlyFeedView::handlePut(FeedToken token, const PutOperation &putOp)
{
    internalPut(std::move(token), putOp);
}

void
StoreOnlyFeedView::internalPut(FeedToken token, const PutOperation &putOp)
{
    assert(putOp.getValidDbdId());
    assert(putOp.notMovingLidInSameSubDb());

    const SerialNum serialNum = putOp.getSerialNum();
    const Document::SP &doc = putOp.getDocument();
    const DocumentId &docId = doc->getId();
    VLOG(getDebugLevel(putOp.getNewOrPrevLid(_params._subDbId), doc->getId()),
         "database(%s): internalPut: serialNum(%" PRIu64 "), docId(%s), "
         "lid(%u,%u) prevLid(%u,%u)"
         " subDbId %u document(%ld) = {\n%s\n}",
         _params._docTypeName.toString().c_str(), serialNum, doc->getId().toString().c_str(),
         putOp.getSubDbId(), putOp.getLid(), putOp.getPrevSubDbId(), putOp.getPrevLid(),
         _params._subDbId, doc->toString(true).size(), doc->toString(true).c_str());

    PendingNotifyRemoveDone pendingNotifyRemoveDone = adjustMetaStore(putOp, docId);
    considerEarlyAck(token);

    bool docAlreadyExists = putOp.getValidPrevDbdId(_params._subDbId);

    if (putOp.getValidDbdId(_params._subDbId)) {
        bool immediateCommit = _commitTimeTracker.needCommit();
        const document::GlobalId &gid = docId.getGlobalId();
        std::shared_ptr<PutDoneContext> onWriteDone =
            createPutDoneContext(std::move(token), _gidToLidChangeHandler, gid, putOp.getLid(), serialNum,
                                 putOp.changedDbdId() && useDocumentMetaStore(serialNum));
        putSummary(serialNum, putOp.getLid(), doc, onWriteDone);
        putAttributes(serialNum, putOp.getLid(), *doc, immediateCommit, onWriteDone);
        putIndexedFields(serialNum, putOp.getLid(), doc, immediateCommit, onWriteDone);
    }
    if (docAlreadyExists && putOp.changedDbdId()) {
        assert(!putOp.getValidDbdId(_params._subDbId));
        internalRemove(std::move(token), serialNum, std::move(pendingNotifyRemoveDone),
                       putOp.getPrevLid(), IDestructorCallback::SP());
    }
}

void
StoreOnlyFeedView::heartBeatIndexedFields(SerialNum ) {}


void
StoreOnlyFeedView::heartBeatAttributes(SerialNum ) {}


StoreOnlyFeedView::UpdateScope
StoreOnlyFeedView::getUpdateScope(const DocumentUpdate &upd)
{
    UpdateScope updateScope;
    if (!upd.getUpdates().empty() || !upd.getFieldPathUpdates().empty()) {
        updateScope._nonAttributeFields = true;
    }
    return updateScope;
}


void
StoreOnlyFeedView::updateAttributes(SerialNum, Lid, const DocumentUpdate &, bool, OnOperationDoneType) {}

void
StoreOnlyFeedView::updateAttributes(SerialNum, Lid, FutureDoc, bool, OnOperationDoneType)
{
}

void
StoreOnlyFeedView::updateIndexedFields(SerialNum, Lid, FutureDoc, bool, OnOperationDoneType)
{
    abort(); // Should never be called.
}

void
StoreOnlyFeedView::prepareUpdate(UpdateOperation &updOp)
{
    const DocumentId &docId = updOp.getUpdate()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    documentmetastore::IStore::Result inspectResult = _metaStore.inspect(gid);
    updOp.setDbDocumentId(DbDocumentId(_params._subDbId, inspectResult._lid));
    assert(_params._subDbType != SubDbType::REMOVED);
    setPrev(updOp, inspectResult, _params._subDbId, false);
}

void
StoreOnlyFeedView::handleUpdate(FeedToken token, const UpdateOperation &updOp)
{
    internalUpdate(std::move(token), updOp);
}

void StoreOnlyFeedView::putSummary(SerialNum serialNum, Lid lid,
                                   FutureStream futureStream, OnOperationDoneType onDone)
{
    _pendingLidTracker.produce(lid);
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winline" // Avoid spurious inlining warning from GCC related to lambda destructor.
    summaryExecutor().execute(
            makeLambdaTask([serialNum, lid, futureStream = std::move(futureStream), onDone, this] () mutable {
                (void) onDone;
                vespalib::nbostream os = std::move(futureStream.get());
                if (!os.empty()) {
                    _summaryAdapter->put(serialNum, lid, os);
                }
                _pendingLidTracker.consume(lid);
            }));
#pragma GCC diagnostic pop
}

void StoreOnlyFeedView::putSummary(SerialNum serialNum, Lid lid, Document::SP doc, OnOperationDoneType onDone)
{
    _pendingLidTracker.produce(lid);
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winline" // Avoid spurious inlining warning from GCC related to lambda destructor.
    summaryExecutor().execute(
            makeLambdaTask([serialNum, doc = std::move(doc), onDone, lid, this] {
                (void) onDone;
                _summaryAdapter->put(serialNum, lid, *doc);
                _pendingLidTracker.consume(lid);
            }));
#pragma GCC diagnostic pop
}
void StoreOnlyFeedView::removeSummary(SerialNum serialNum, Lid lid, OnWriteDoneType onDone) {
    _pendingLidTracker.produce(lid);
    summaryExecutor().execute(
            makeLambdaTask([serialNum, lid, onDone, this] {
                (void) onDone;
                _summaryAdapter->remove(serialNum, lid);
                _pendingLidTracker.consume(lid);
            }));
}
void StoreOnlyFeedView::heartBeatSummary(SerialNum serialNum) {
    summaryExecutor().execute(
            makeLambdaTask([serialNum, this] {
                _summaryAdapter->heartBeat(serialNum);
            }));
}

void
StoreOnlyFeedView::internalUpdate(FeedToken token, const UpdateOperation &updOp) {
    if ( ! updOp.getUpdate()) {
        LOG(warning, "database(%s): ignoring invalid update operation",
            _params._docTypeName.toString().c_str());
        return;
    }

    const SerialNum serialNum = updOp.getSerialNum();
    const DocumentUpdate &upd = *updOp.getUpdate();
    const DocumentId &docId = upd.getId();
    const Lid lid = updOp.getLid();
    VLOG(getDebugLevel(lid, upd.getId()),
         "database(%s): internalUpdate: serialNum(%lu), docId(%s), lid(%d)",
         _params._docTypeName.toString().c_str(), serialNum,
         upd.getId().toString().c_str(), lid);

    if (useDocumentMetaStore(serialNum)) {
        Lid storedLid;
        bool lookupOk = lookupDocId(docId, storedLid);
        assert(lookupOk);
        (void) lookupOk;
        assert(storedLid == updOp.getLid());
        bool updateOk = _metaStore.updateMetaData(updOp.getLid(), updOp.getBucketId(), updOp.getTimestamp());
        assert(updateOk);
        (void) updateOk;
        _metaStore.commit(serialNum, serialNum);
    }
    considerEarlyAck(token);

    bool immediateCommit = _commitTimeTracker.needCommit();
    auto onWriteDone = createUpdateDoneContext(std::move(token), updOp.getUpdate());
    updateAttributes(serialNum, lid, upd, immediateCommit, onWriteDone);

    UpdateScope updateScope(getUpdateScope(upd));
    if (updateScope.hasIndexOrNonAttributeFields()) {
        PromisedDoc promisedDoc;
        FutureDoc futureDoc = promisedDoc.get_future().share();
        _pendingLidTracker.waitForConsumedLid(lid);
        if (updateScope._indexedFields) {
            updateIndexedFields(serialNum, lid, futureDoc, immediateCommit, onWriteDone);
        }
        PromisedStream promisedStream;
        FutureStream futureStream = promisedStream.get_future();
        if (useDocumentStore(serialNum)) {
            putSummary(serialNum, lid, std::move(futureStream), onWriteDone);
        }
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winline" // Avoid spurious inlining warning from GCC related to lambda destructor.
        _writeService
                .attributeFieldWriter()
                .execute(serialNum,
                         [upd = updOp.getUpdate(), serialNum, lid, onWriteDone, promisedDoc = std::move(promisedDoc),
                          promisedStream = std::move(promisedStream), this]() mutable
                         {
                             makeUpdatedDocument(serialNum, lid, upd, onWriteDone,
                                                 std::move(promisedDoc), std::move(promisedStream));
                         });
#pragma GCC diagnostic pop
        updateAttributes(serialNum, lid, std::move(futureDoc), immediateCommit, onWriteDone);
    }
}

void
StoreOnlyFeedView::makeUpdatedDocument(SerialNum serialNum, Lid lid, DocumentUpdate::SP update,
                                       OnOperationDoneType onWriteDone, PromisedDoc promisedDoc,
                                       PromisedStream promisedStream)
{
    Document::UP prevDoc = _summaryAdapter->get(lid, *_repo);
    const DocumentUpdate & upd = *update;
    Document::UP newDoc;
    vespalib::nbostream newStream(12345);
    assert(!onWriteDone->hasToken() || useDocumentStore(serialNum));
    if (useDocumentStore(serialNum)) {
        assert(prevDoc);
    }
    if (!prevDoc) {
        // Replaying, document removed later before summary was flushed.
        assert(!onWriteDone->hasToken());
        // If we've passed serial number for flushed index then we could
        // also check that this operation is marked for ignore by index
        // proxy.
    } else {
        if (upd.getId() == prevDoc->getId()) {
            newDoc = std::move(prevDoc);
            if (useDocumentStore(serialNum)) {
                upd.applyTo(*newDoc);
                newDoc->serialize(newStream);
            }
        } else {
            // Replaying, document removed and lid reused before summary
            // was flushed.
            assert(!onWriteDone->hasToken() && !useDocumentStore(serialNum));
        }
    }
    promisedDoc.set_value(std::move(newDoc));
    promisedStream.set_value(std::move(newStream));
}

bool
StoreOnlyFeedView::lookupDocId(const DocumentId &docId, Lid &lid) const
{
    // This function should only be called by the updater thread.
    // Readers need to take a guard on the document meta store
    // attribute before accessing.
    if (!_metaStore.getLid(docId.getGlobalId(), lid)) {
        return false;
    }
    if (_params._subDbType == SubDbType::REMOVED)
        return false;
    return true;
}

void
StoreOnlyFeedView::removeAttributes(SerialNum, Lid, bool, OnRemoveDoneType) {}

void
StoreOnlyFeedView::removeIndexedFields(SerialNum, Lid, bool, OnRemoveDoneType) {}

void
StoreOnlyFeedView::prepareRemove(RemoveOperation &rmOp)
{
    const DocumentId &id = rmOp.getDocumentId();
    const document::GlobalId &gid = id.getGlobalId();
    documentmetastore::IStore::Result inspectRes = _metaStore.inspect(gid);
    if (_params._subDbType == SubDbType::REMOVED) {
        rmOp.setDbDocumentId(DbDocumentId(_params._subDbId, inspectRes._lid));
    }
    setPrev(rmOp, inspectRes, _params._subDbId, _params._subDbType == SubDbType::REMOVED);
}

void
StoreOnlyFeedView::handleRemove(FeedToken token, const RemoveOperation &rmOp) {
    internalRemove(std::move(token), rmOp);
}

void
StoreOnlyFeedView::internalRemove(FeedToken token, const RemoveOperation &rmOp)
{
    assert(rmOp.getValidNewOrPrevDbdId());
    assert(rmOp.notMovingLidInSameSubDb());
    const SerialNum serialNum = rmOp.getSerialNum();
    const DocumentId &docId = rmOp.getDocumentId();
    VLOG(getDebugLevel(rmOp.getNewOrPrevLid(_params._subDbId), docId),
         "database(%s): internalRemove: serialNum(%" PRIu64 "), docId(%s), "
         "lid(%u,%u) prevlid(%u,%u), subDbId %u",
         _params._docTypeName.toString().c_str(), serialNum, docId.toString().c_str(),
         rmOp.getSubDbId(), rmOp.getLid(), rmOp.getPrevSubDbId(), rmOp.getPrevLid(), _params._subDbId);

    PendingNotifyRemoveDone pendingNotifyRemoveDone = adjustMetaStore(rmOp, docId);
    considerEarlyAck(token);

    if (rmOp.getValidDbdId(_params._subDbId)) {
        Document::UP clearDoc(new Document(*_docType, docId));
        clearDoc->setRepo(*_repo);

        putSummary(serialNum, rmOp.getLid(), std::move(clearDoc), std::shared_ptr<OperationDoneContext>());
    }
    if (rmOp.getValidPrevDbdId(_params._subDbId)) {
        if (rmOp.changedDbdId()) {
            assert(!rmOp.getValidDbdId(_params._subDbId));
            internalRemove(std::move(token), serialNum, std::move(pendingNotifyRemoveDone),
                           rmOp.getPrevLid(), IDestructorCallback::SP());
        }
    }
}

void
StoreOnlyFeedView::internalRemove(FeedToken token, SerialNum serialNum,
                                  PendingNotifyRemoveDone &&pendingNotifyRemoveDone, Lid lid,
                                  IDestructorCallback::SP moveDoneCtx)
{
    bool explicitReuseLid = _lidReuseDelayer.delayReuse(lid);
    std::shared_ptr<RemoveDoneContext> onWriteDone;
    onWriteDone = createRemoveDoneContext(std::move(token), _writeService.master(), _metaStore,
                                          std::move(pendingNotifyRemoveDone), (explicitReuseLid ? lid : 0u),
                                          std::move(moveDoneCtx));
    removeSummary(serialNum, lid, onWriteDone);
    bool immediateCommit = _commitTimeTracker.needCommit();
    removeAttributes(serialNum, lid, immediateCommit, onWriteDone);
    removeIndexedFields(serialNum, lid, immediateCommit, onWriteDone);
}

PendingNotifyRemoveDone
StoreOnlyFeedView::adjustMetaStore(const DocumentOperation &op, const DocumentId &docId)
{
    PendingNotifyRemoveDone pendingNotifyRemoveDone;
    const SerialNum serialNum = op.getSerialNum();
    if (useDocumentMetaStore(serialNum)) {
        if (op.getValidDbdId(_params._subDbId)) {
            if (op.getType() == FeedOperation::MOVE &&
                op.getValidPrevDbdId(_params._subDbId) &&
                op.getLid() != op.getPrevLid())
            {
                moveMetaData(_metaStore, docId, op);
            } else {
                putMetaData(_metaStore, docId, op, _params._subDbType == SubDbType::REMOVED);
            }
        } else if (op.getValidPrevDbdId(_params._subDbId)) {
            _gidToLidChangeHandler.notifyRemove(docId.getGlobalId(), serialNum);
            pendingNotifyRemoveDone.setup(_gidToLidChangeHandler, docId.getGlobalId(), serialNum);
            removeMetaData(_metaStore, docId, op, _params._subDbType == SubDbType::REMOVED);
        }
        _metaStore.commit(serialNum, serialNum);
    }
    return pendingNotifyRemoveDone;
}

void
StoreOnlyFeedView::removeAttributes(SerialNum, const LidVector &, bool , OnWriteDoneType ) {}

void
StoreOnlyFeedView::removeIndexedFields(SerialNum , const LidVector &, bool , OnWriteDoneType ) {}

size_t
StoreOnlyFeedView::removeDocuments(const RemoveDocumentsOperation &op, bool remove_index_and_attributes,
                                   bool immediateCommit)
{
    const SerialNum serialNum = op.getSerialNum();
    const LidVectorContext::SP &ctx = op.getLidsToRemove(_params._subDbId);
    if (!ctx) {
        if (useDocumentMetaStore(serialNum)) {
            _metaStore.commit(serialNum, serialNum);
        }
        return 0;
    }
    const LidVector &lidsToRemove(ctx->getLidVector());
    bool useDMS = useDocumentMetaStore(serialNum);
    bool explicitReuseLids = false;
    std::vector<document::GlobalId> gidsToRemove;
    if (useDMS) {
        gidsToRemove = getGidsToRemove(_metaStore, lidsToRemove);
        for (const auto &gid : gidsToRemove) {
            _gidToLidChangeHandler.notifyRemove(gid, serialNum);
        }
        _metaStore.removeBatch(lidsToRemove, ctx->getDocIdLimit());
        _metaStore.commit(serialNum, serialNum);
        explicitReuseLids = _lidReuseDelayer.delayReuse(lidsToRemove);
    }
    std::shared_ptr<search::IDestructorCallback> onWriteDone;
    vespalib::Executor::Task::UP removeBatchDoneTask;
    if (explicitReuseLids) {
        removeBatchDoneTask = makeLambdaTask([=]() { _metaStore.removeBatchComplete(lidsToRemove); });
    } else {
        removeBatchDoneTask = makeLambdaTask([]() {});
    }
    onWriteDone = std::make_shared<RemoveBatchDoneContext>(_writeService.master(), std::move(removeBatchDoneTask),
                                                           _gidToLidChangeHandler, std::move(gidsToRemove), serialNum);
    if (remove_index_and_attributes) {
        removeIndexedFields(serialNum, lidsToRemove, immediateCommit, onWriteDone);
        removeAttributes(serialNum, lidsToRemove, immediateCommit, onWriteDone);
    }
    if (useDocumentStore(serialNum + 1)) {
        for (const auto &lid : lidsToRemove) {
            removeSummary(serialNum, lid, onWriteDone);
        }
    }
    return lidsToRemove.size();
}

void
StoreOnlyFeedView::prepareDeleteBucket(DeleteBucketOperation &delOp)
{
    const BucketId &bucket = delOp.getBucketId();
    LidVector lidsToRemove;
    _metaStore.getLids(bucket, lidsToRemove);
    LOG(debug, "prepareDeleteBucket(): docType(%s), bucket(%s), lidsToRemove(%zu)",
        _params._docTypeName.toString().c_str(), bucket.toString().c_str(), lidsToRemove.size());

    if (!lidsToRemove.empty()) {
        LidVectorContext::SP ctx(new LidVectorContext(_metaStore.getCommittedDocIdLimit(), lidsToRemove));
        delOp.setLidsToRemove(_params._subDbId, ctx);
    }
}

void
StoreOnlyFeedView::handleDeleteBucket(const DeleteBucketOperation &delOp)
{
    internalDeleteBucket(delOp);
}

void
StoreOnlyFeedView::internalDeleteBucket(const DeleteBucketOperation &delOp)
{
    bool immediateCommit = _commitTimeTracker.needCommit();
    size_t rm_count = removeDocuments(delOp, true, immediateCommit);
    LOG(debug, "internalDeleteBucket(): docType(%s), bucket(%s), lidsToRemove(%zu)",
        _params._docTypeName.toString().c_str(), delOp.getBucketId().toString().c_str(), rm_count);
}

// CombiningFeedView calls this only for the subdb we're moving to.
void
StoreOnlyFeedView::prepareMove(MoveOperation &moveOp)
{
    const DocumentId &docId = moveOp.getDocument()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    documentmetastore::IStore::Result inspectResult = _metaStore.inspect(gid);
    assert(!inspectResult._found);
    moveOp.setDbDocumentId(DbDocumentId(_params._subDbId, inspectResult._lid));
}

// CombiningFeedView calls this for both source and target subdb.
void
StoreOnlyFeedView::handleMove(const MoveOperation &moveOp, IDestructorCallback::SP doneCtx)
{
    assert(moveOp.getValidDbdId());
    assert(moveOp.getValidPrevDbdId());
    assert(moveOp.movingLidIfInSameSubDb());

    const SerialNum serialNum = moveOp.getSerialNum();

    const Document::SP &doc = moveOp.getDocument();
    const DocumentId &docId = doc->getId();
    VLOG(getDebugLevel(moveOp.getNewOrPrevLid(_params._subDbId), doc->getId()),
         "database(%s): handleMove: serialNum(%" PRIu64 "), docId(%s), "
         "lid(%u,%u) prevLid(%u,%u) subDbId %u document(%ld) = {\n%s\n}",
         _params._docTypeName.toString().c_str(), serialNum, doc->getId().toString().c_str(),
         moveOp.getSubDbId(), moveOp.getLid(), moveOp.getPrevSubDbId(), moveOp.getPrevLid(),
         _params._subDbId, doc->toString(true).size(), doc->toString(true).c_str());

    PendingNotifyRemoveDone pendingNotifyRemoveDone = adjustMetaStore(moveOp, docId);
    bool docAlreadyExists = moveOp.getValidPrevDbdId(_params._subDbId);
    if (moveOp.getValidDbdId(_params._subDbId)) {
        bool immediateCommit = _commitTimeTracker.needCommit();
        const document::GlobalId &gid = docId.getGlobalId();
        std::shared_ptr<PutDoneContext> onWriteDone =
            createPutDoneContext(FeedToken(), _gidToLidChangeHandler, gid, moveOp.getLid(), serialNum,
                                 moveOp.changedDbdId() && useDocumentMetaStore(serialNum), doneCtx);
        putSummary(serialNum, moveOp.getLid(), doc, onWriteDone);
        putAttributes(serialNum, moveOp.getLid(), *doc, immediateCommit, onWriteDone);
        putIndexedFields(serialNum, moveOp.getLid(), doc, immediateCommit, onWriteDone);
    }
    if (docAlreadyExists && moveOp.changedDbdId()) {
        internalRemove(FeedToken(), serialNum, std::move(pendingNotifyRemoveDone), moveOp.getPrevLid(), doneCtx);
    }
}

void
StoreOnlyFeedView::heartBeat(SerialNum serialNum)
{
    assert(_writeService.master().isCurrentThread());
    _metaStore.removeAllOldGenerations();
    if (serialNum > _metaStore.getLastSerialNum()) {
        _metaStore.commit(serialNum, serialNum);
    }
    heartBeatSummary(serialNum);
    heartBeatIndexedFields(serialNum);
    heartBeatAttributes(serialNum);
}

// CombiningFeedView calls this only for the removed subdb.
void
StoreOnlyFeedView::
handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp)
{
    assert(_params._subDbType == SubDbType::REMOVED);
    assert(pruneOp.getSubDbId() == _params._subDbId);
    uint32_t rm_count = removeDocuments(pruneOp, false, false);

    LOG(debug, "MinimalFeedView::handlePruneRemovedDocuments called, doctype(%s) %u lids pruned, limit %u",
        _params._docTypeName.toString().c_str(), rm_count,
        static_cast<uint32_t>(pruneOp.getLidsToRemove()->getDocIdLimit()));
}

void
StoreOnlyFeedView::handleCompactLidSpace(const CompactLidSpaceOperation &op)
{
    assert(_params._subDbId == op.getSubDbId());
    const SerialNum serialNum = op.getSerialNum();
    if (useDocumentMetaStore(serialNum)) {
        getDocumentMetaStore()->get().compactLidSpace(op.getLidLimit());
        std::shared_ptr<ForceCommitContext>
            commitContext(std::make_shared<ForceCommitContext>(_writeService.master(), _metaStore));
        commitContext->holdUnblockShrinkLidSpace();
        forceCommit(serialNum, commitContext);
    }
    if (useDocumentStore(serialNum)) {
        _summaryAdapter->compactLidSpace(op.getLidLimit());
    }
}

const ISimpleDocumentMetaStore *
StoreOnlyFeedView::getDocumentMetaStorePtr() const
{
    return &_documentMetaStoreContext->get();
}

} // namespace proton
