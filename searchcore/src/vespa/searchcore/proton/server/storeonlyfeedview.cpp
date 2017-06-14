// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ireplayconfig.h"
#include "storeonlyfeedview.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include "updatedonecontext.h"
#include "putdonecontext.h"
#include <vespa/searchcore/proton/common/commit_time_tracker.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/metrics/feed_metrics.h>
#include <vespa/searchcore/proton/documentmetastore/ilidreusedelayer.h>
#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/closuretask.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.storeonlyfeedview");

using document::BucketId;
using document::DocumentTypeRepo;
using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using search::index::Schema;
using storage::spi::BucketInfoResult;
using storage::spi::Timestamp;
using vespalib::IllegalStateException;
using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::make_string;
using search::makeLambdaTask;

namespace proton {

namespace {

bool shouldTrace(StoreOnlyFeedView::OnOperationDoneType onWriteDone,
                 uint32_t traceLevel)
{
    return onWriteDone && onWriteDone->shouldTrace(traceLevel);
}

FeedToken::UP dupFeedToken(FeedToken *token)
{
    // If token is not NULL then a new feed token is created, referencing
    // same shared state as old token.
    if (token != NULL) {
        return FeedToken::UP(new FeedToken(*token));
    } else {
        return FeedToken::UP();
    }
}

std::shared_ptr<PutDoneContext>
createPutDoneContext(FeedToken::UP &token,
                     FeedOperation::Type opType,
                     PerDocTypeFeedMetrics &metrics,
                     bool force)
{
    std::shared_ptr<PutDoneContext> result;
    if (token || force) {
        result = std::make_shared<PutDoneContext>(std::move(token), opType, metrics);
    }
    return result;
}

std::shared_ptr<UpdateDoneContext>
createUpdateDoneContext(FeedToken::UP &token,
                        FeedOperation::Type opType,
                        PerDocTypeFeedMetrics &metrics,
                        const document::DocumentUpdate::SP &upd)
{
    return std::make_shared<UpdateDoneContext>(std::move(token), opType, metrics, upd);
}

}  // namespace


StoreOnlyFeedView::StoreOnlyFeedView(const Context &ctx,
                                     const PersistentParams &params)
    : IFeedView(),
      FeedDebugger(),
      _summaryAdapter(ctx._summaryAdapter),
      _schema(ctx._schema),
      _documentMetaStoreContext(ctx._documentMetaStoreContext),
      _repo(ctx._repo),
      _writeService(ctx._writeService),
      _params(params),
      _metaStore(_documentMetaStoreContext->get()),
      _docType(NULL),
      _lidReuseDelayer(ctx._lidReuseDelayer),
      _commitTimeTracker(ctx._commitTimeTracker)
{
    _docType = _repo->getDocumentType(_params._docTypeName.getName());
}


void
StoreOnlyFeedView::sync()
{
}

void
StoreOnlyFeedView::forceCommit(SerialNum serialNum)
{
    forceCommit(serialNum,
                std::make_shared<ForceCommitContext>(_writeService.master(),
                                                     _metaStore));
}


void
StoreOnlyFeedView::forceCommit(SerialNum serialNum,
                               OnForceCommitDoneType onCommitDone)
{
    (void) serialNum;
    std::vector<uint32_t> lidsToReuse;
    lidsToReuse = _lidReuseDelayer.getReuseLids();
    if (!lidsToReuse.empty()) {
        onCommitDone->reuseLids(std::move(lidsToReuse));
    }
}


void
StoreOnlyFeedView::considerEarlyAck(FeedToken::UP &token,
                                    FeedOperation::Type opType)
{
    if (_commitTimeTracker.hasVisibilityDelay() && token) {
        token->ack(opType, _params._metrics);
        token.reset();
    }
}


void
StoreOnlyFeedView::putAttributes(SerialNum serialNum,
                                 search::DocumentIdT lid,
                                 const Document &doc,
                                 bool immediateCommit,
                                 OnPutDoneType onWriteDone)
{
    (void) serialNum;
    (void) lid;
    (void) doc;
    (void) immediateCommit;
    (void) onWriteDone;
}

void
StoreOnlyFeedView::putIndexedFields(SerialNum serialNum,
                                    search::DocumentIdT lid,
                                    const Document::SP &newDoc,
                                    bool immediateCommit,
                                    OnOperationDoneType onWriteDone)
{
    (void) serialNum;
    (void) lid;
    (void) newDoc;
    (void) immediateCommit;
    (void) onWriteDone;
}


namespace {
void setPrev(DocumentOperation &op,
             const documentmetastore::IStore::Result &result,
             uint32_t subDbId, bool markedAsRemoved) {
    if (result._found) {
        op.setPrevDbDocumentId(DbDocumentId(subDbId, result._lid));
        op.setPrevMarkedAsRemoved(markedAsRemoved);
        op.setPrevTimestamp(result._timestamp);
    }
}
}  // namespace

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
StoreOnlyFeedView::handlePut(FeedToken *token,
                             const PutOperation &putOp)
{
    internalPut(dupFeedToken(token), putOp);
}


void
StoreOnlyFeedView::internalPut(FeedToken::UP token,
                               const PutOperation &putOp)
{
    assert(putOp.getValidDbdId());
    assert(putOp.notMovingLidInSameSubDb());

    const SerialNum serialNum = putOp.getSerialNum();
    const Document::SP &doc = putOp.getDocument();
    const DocumentId &docId = doc->getId();
    VLOG(getDebugLevel(putOp.getNewOrPrevLid(_params._subDbId), doc->getId()),
         "database(%s): internalPut: serialNum(%" PRIu64 "), "
         "docId(%s), lid(%u,%u) prevLid(%u,%u)"
         " subDbId %u document(%ld) = {\n%s\n}",
         _params._docTypeName.toString().c_str(),
         serialNum,
         doc->getId().toString().c_str(),
         putOp.getSubDbId(),
         putOp.getLid(),
         putOp.getPrevSubDbId(),
         putOp.getPrevLid(),
         _params._subDbId,
         doc->toString(true).size(),
         doc->toString(true).c_str());

    uint32_t oldDocIdLimit = _metaStore.getCommittedDocIdLimit();
    adjustMetaStore(putOp, docId);
    considerEarlyAck(token, putOp.getType());

    bool docAlreadyExists = putOp.getValidPrevDbdId(_params._subDbId);
    if (putOp.getValidDbdId(_params._subDbId)) {
        _summaryAdapter->put(serialNum, *doc, putOp.getLid());
        bool immediateCommit = _commitTimeTracker.needCommit();
        std::shared_ptr<PutDoneContext> onWriteDone =
            createPutDoneContext(token, putOp.getType(), _params._metrics,
                                 immediateCommit &&
                                 putOp.getLid() >= oldDocIdLimit);
        putAttributes(serialNum, putOp.getLid(), *doc, immediateCommit, onWriteDone);
        putIndexedFields(serialNum, putOp.getLid(), doc, immediateCommit, onWriteDone);
    }
    if (docAlreadyExists && putOp.changedDbdId()) {
        assert(!putOp.getValidDbdId(_params._subDbId));
        internalRemove(std::move(token), serialNum, putOp.getPrevLid(), putOp.getType());
    }
    if (token.get() != NULL) {
        token->ack(putOp.getType(), _params._metrics);
    }
}


void
StoreOnlyFeedView::heartBeatIndexedFields(SerialNum serialNum)
{
    (void) serialNum;
}


void
StoreOnlyFeedView::heartBeatAttributes(SerialNum serialNum)
{
    (void) serialNum;
}


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
StoreOnlyFeedView::updateAttributes(SerialNum serialNum,
                                    search::DocumentIdT lid,
                                    const document::DocumentUpdate &upd,
                                    bool immediateCommit,
                                    OnOperationDoneType onWriteDone)
{
    (void) serialNum;
    (void) lid;
    (void) upd;
    (void) immediateCommit;
    (void) onWriteDone;
}


void
StoreOnlyFeedView::updateIndexedFields(SerialNum serialNum,
                                       search::DocumentIdT lid,
                                       const Document::SP &newDoc,
                                       bool immediateCommit,
                                       OnOperationDoneType onWriteDone)
{
    (void) serialNum;
    (void) lid;
    (void) newDoc;
    (void) immediateCommit;
    (void) onWriteDone;
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
StoreOnlyFeedView::handleUpdate(FeedToken *token,
                                const UpdateOperation &updOp)
{
    internalUpdate(dupFeedToken(token), updOp);
}


void
StoreOnlyFeedView::internalUpdate(FeedToken::UP token,
                                  const UpdateOperation &updOp)
{
    if (updOp.getUpdate().get() == NULL) {
        LOG(warning, "database(%s): ignoring invalid update operation",
            _params._docTypeName.toString().c_str());
        return;
    }

    const SerialNum serialNum = updOp.getSerialNum();
    const DocumentUpdate &upd = *updOp.getUpdate();
    const DocumentId &docId = upd.getId();
    const search::DocumentIdT lid = updOp.getLid();
    VLOG(getDebugLevel(lid, upd.getId()),
        "database(%s): internalUpdate: serialNum(%" PRIu64
        "), docId(%s), lid(%d)",
        _params._docTypeName.toString().c_str(),
        serialNum,
        upd.getId().toString().c_str(),
        lid);

    if (useDocumentMetaStore(serialNum)) {
        search::DocumentIdT storedLid;
        bool lookupOk = lookupDocId(docId, storedLid);
        assert(lookupOk);
        (void) lookupOk;
        assert(storedLid == updOp.getLid());
        bool updateOk = _metaStore.updateMetaData(updOp.getLid(),
                                                  updOp.getBucketId(),
                                                  updOp.getTimestamp());
        assert(updateOk);
        (void) updateOk;
        _metaStore.commit(serialNum, serialNum);
    }
    considerEarlyAck(token, updOp.getType());

    bool immediateCommit = _commitTimeTracker.needCommit();
    auto onWriteDone = createUpdateDoneContext(token, updOp.getType(),
            _params._metrics, updOp.getUpdate());
    updateAttributes(serialNum, lid, upd, immediateCommit, onWriteDone);
    UpdateScope updateScope(getUpdateScope(upd));
    /*
     * XXX: Flushing issue
     *
     * If subclass has indexed fields, but no partial updates updates
     * affect indexed fields then serial number is not updated, for very
     * specific feed patterns, this can cause flush engine issues.
     *
     * Similarly, if subclass has attributes that are not updated.
     *
     */
    if (updateScope._indexedFields || updateScope._nonAttributeFields) {
        updateIndexAndDocumentStore(updateScope._indexedFields,
                                    serialNum, lid, upd,
                                    immediateCommit,
                                    onWriteDone);
    }
    if (!updateScope._indexedFields && onWriteDone) {
        if (onWriteDone->shouldTrace(1))
        {
            token->trace(1, "Partial update applied.");
        }
    }
}


void
StoreOnlyFeedView::updateIndexAndDocumentStore(bool indexedFieldsInScope,
                                               SerialNum serialNum,
                                               search::DocumentIdT lid,
                                               const DocumentUpdate &upd,
                                               bool immediateCommit,
                                               OnOperationDoneType onWriteDone)
{
    Document::UP prevDoc(_summaryAdapter->get(lid, *_repo));
    assert(onWriteDone->getToken() == NULL || useDocumentStore(serialNum));
    if (useDocumentStore(serialNum)) {
        assert(prevDoc.get() != NULL);
    }
    if (prevDoc.get() == NULL) {
        // Replaying, document removed later before summary was flushed.
        assert(onWriteDone->getToken() == NULL);
        // If we've passed serial number for flushed index then we could
        // also check that this operation is marked for ignore by index
        // proxy.
        return;
    }
    if (upd.getId() == prevDoc->getId()) {
        if (shouldTrace(onWriteDone, 1))
        {
            FeedToken *token = onWriteDone->getToken();
            token->trace(1, "The update looks like : " +
                         upd.toString(token->shouldTrace(2)));
        }
        vespalib::nbostream os;
        prevDoc->serialize(os);
        Document::SP newDoc(new Document(*_repo, os));
        if (useDocumentStore(serialNum)) {
            LOG(spam, "Original document :\n%s", newDoc->toXml("  ").c_str());
            LOG(spam, "Update\n%s", upd.toXml().c_str());
            upd.applyTo(*newDoc);
            LOG(spam, "Updated document :\n%s", newDoc->toXml("  ").c_str());
            if (shouldTrace(onWriteDone, 1)) {
                onWriteDone->getToken()->trace(1, "Then we update summary.");
            }
            _summaryAdapter->put(serialNum, *newDoc, lid);
        }
        if (indexedFieldsInScope) {
            updateIndexedFields(serialNum, lid, newDoc, immediateCommit, onWriteDone);
        }
    } else {
        // Replaying, document removed and lid reused before summary
        // was flushed.
        assert(onWriteDone->getToken() == NULL && !useDocumentStore(serialNum));
    }
}


bool
StoreOnlyFeedView::lookupDocId(const DocumentId &docId,
                               search::DocumentIdT &lid) const
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
StoreOnlyFeedView::removeAttributes(SerialNum serialNum,
                                    search::DocumentIdT lid,
                                    bool immediateCommit,
                                    OnRemoveDoneType onWriteDone)
{
    (void) serialNum;
    (void) lid;
    (void) immediateCommit;
    (void) onWriteDone;
}


void
StoreOnlyFeedView::removeIndexedFields(SerialNum serialNum,
                                       search::DocumentIdT lid,
                                       bool immediateCommit,
                                       OnRemoveDoneType onWriteDone)
{
    (void) serialNum;
    (void) lid;
    (void) immediateCommit;
    (void) onWriteDone;
}


void
StoreOnlyFeedView::prepareRemove(RemoveOperation &rmOp)
{
    const DocumentId &id = rmOp.getDocumentId();
    const document::GlobalId &gid = id.getGlobalId();
    documentmetastore::IStore::Result inspectRes = _metaStore.inspect(gid);
    if (_params._subDbType == SubDbType::REMOVED)
        rmOp.setDbDocumentId(DbDocumentId(_params._subDbId, inspectRes._lid));
    setPrev(rmOp, inspectRes, _params._subDbId,
            _params._subDbType == SubDbType::REMOVED);
}


void
StoreOnlyFeedView::handleRemove(FeedToken *token,
                                const RemoveOperation &rmOp)
{
    internalRemove(dupFeedToken(token), rmOp);
}


void
StoreOnlyFeedView::internalRemove(FeedToken::UP token,
                                  const RemoveOperation &rmOp)
{
    assert(rmOp.getValidNewOrPrevDbdId());
    assert(rmOp.notMovingLidInSameSubDb());

    const SerialNum serialNum = rmOp.getSerialNum();
    const DocumentId &docId = rmOp.getDocumentId();
    VLOG(getDebugLevel(rmOp.getNewOrPrevLid(_params._subDbId), docId),
         "database(%s): internalRemove: serialNum(%" PRIu64 "), "
         "docId(%s), lid(%u,%u) prevlid(%u,%u), subDbId %u",
         _params._docTypeName.toString().c_str(),
         serialNum,
         docId.toString().c_str(),
         rmOp.getSubDbId(),
         rmOp.getLid(),
         rmOp.getPrevSubDbId(),
         rmOp.getPrevLid(),
         _params._subDbId);

    adjustMetaStore(rmOp, docId);
    considerEarlyAck(token, rmOp.getType());
    if (rmOp.getValidDbdId(_params._subDbId)) {
        Document::UP clearDoc(new Document(*_docType, docId));
        clearDoc->setRepo(*_repo);
        _summaryAdapter->put(serialNum, *clearDoc, rmOp.getLid());
    }
    if (rmOp.getValidPrevDbdId(_params._subDbId)) {
        if (rmOp.changedDbdId()) {
            assert(!rmOp.getValidDbdId(_params._subDbId));
            internalRemove(std::move(token), serialNum, rmOp.getPrevLid(), rmOp.getType());
        }
    }
    if (token.get() != NULL) {
        token->ack(rmOp.getType(), _params._metrics);
    }
}

void
StoreOnlyFeedView::internalRemove(FeedToken::UP token,
                                  SerialNum serialNum,
                                  search::DocumentIdT lid,
                                  FeedOperation::Type opType)
{
    _summaryAdapter->remove(serialNum, lid);
    bool explicitReuseLid = _lidReuseDelayer.delayReuse(lid);
    std::shared_ptr<RemoveDoneContext> onWriteDone;
    if (explicitReuseLid || token) {
        onWriteDone = std::make_shared<RemoveDoneContext>
                      (std::move(token), opType, _params._metrics,
                       _writeService.master(), _metaStore,
                       explicitReuseLid ? lid : 0u);
    }
    bool immediateCommit = _commitTimeTracker.needCommit();
    removeAttributes(serialNum, lid, immediateCommit, onWriteDone);
    removeIndexedFields(serialNum, lid, immediateCommit, onWriteDone);
}

namespace {
void putMetaData(documentmetastore::IStore &meta_store, const DocumentId &doc_id,
                 const DocumentOperation &op, bool is_removed_doc) {
    documentmetastore::IStore::Result putRes(
            meta_store.put(doc_id.getGlobalId(),
                           op.getBucketId(), op.getTimestamp(), op.getSerializedDocSize(), op.getLid()));
    if (!putRes.ok()) {
        throw IllegalStateException(
                make_string("Could not put <lid, gid> pair for "
                            "%sdocument with id '%s' and gid '%s'",
                            is_removed_doc ? "removed " : "",
                            doc_id.toString().c_str(),
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
                make_string("Could not remove <lid, gid> pair for "
                            "%sdocument with id '%s' and gid '%s'",
                            is_removed_doc ? "removed " : "",
                            doc_id.toString().c_str(),
                            doc_id.getGlobalId().toString().c_str()));
    }
}

void
moveMetaData(documentmetastore::IStore &meta_store,
             const DocumentId &doc_id,
             const DocumentOperation &op)
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

void
StoreOnlyFeedView::adjustMetaStore(const DocumentOperation &op,
                                   const DocumentId &docId)
{
    const SerialNum serialNum = op.getSerialNum();
    if (useDocumentMetaStore(serialNum)) {
        if (op.getValidDbdId(_params._subDbId)) {
            if (op.getType() == FeedOperation::MOVE &&
                op.getValidPrevDbdId(_params._subDbId) &&
                op.getLid() != op.getPrevLid()) {
                moveMetaData(_metaStore, docId, op);
                notifyGidToLidChange(docId.getGlobalId(), op.getLid());
            } else {
                putMetaData(_metaStore, docId, op,
                            _params._subDbType == SubDbType::REMOVED);
                if (op.getDbDocumentId() != op.getPrevDbDocumentId()) {
                    notifyGidToLidChange(docId.getGlobalId(), op.getLid());
                }
            }
        } else if (op.getValidPrevDbdId(_params._subDbId)) {
            removeMetaData(_metaStore, docId, op,
                           _params._subDbType == SubDbType::REMOVED);
            notifyGidToLidChange(docId.getGlobalId(), 0u);
        }
        _metaStore.commit(serialNum, serialNum);
    }
}


void
StoreOnlyFeedView::removeAttributes(SerialNum serialNum,
                                    const LidVector &lidsToRemove,
                                    bool immediateCommit,
                                    OnWriteDoneType onWriteDone)
{
    (void) serialNum;
    (void) lidsToRemove;
    (void) immediateCommit;
    (void) onWriteDone;
}


void
StoreOnlyFeedView::removeIndexedFields(SerialNum serialNum,
                                       const LidVector &lidsToRemove,
                                       bool immediateCommit,
                                       OnWriteDoneType onWriteDone)
{
    (void) serialNum;
    (void) lidsToRemove;
    (void) onWriteDone;
    (void) immediateCommit;
}


namespace {

std::vector<document::GlobalId> getGidsToRemove(const IDocumentMetaStore &metaStore, const LidVectorContext::LidVector &lidsToRemove) {
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

}

size_t
StoreOnlyFeedView::removeDocuments(const RemoveDocumentsOperation &op,
                                   bool remove_index_and_attributes,
                                   bool immediateCommit)
{
    const SerialNum serialNum = op.getSerialNum();
    const LidVectorContext::SP &ctx = op.getLidsToRemove(_params._subDbId);
    if (!ctx.get()) {
        if (useDocumentMetaStore(serialNum)) {
            _metaStore.commit(serialNum, serialNum);
        }
        return 0;
    }
    const LidVector &lidsToRemove(ctx->getLidVector());
    bool useDMS = useDocumentMetaStore(serialNum);
    bool explicitReuseLids = false;
    if (useDMS) {
        std::vector<document::GlobalId> gidsToRemove(getGidsToRemove(_metaStore, lidsToRemove));
        _metaStore.removeBatch(lidsToRemove, ctx->getDocIdLimit());
        for (const auto &gid : gidsToRemove) {
            notifyGidToLidChange(gid, 0u);
        }
        _metaStore.commit(serialNum, serialNum);
        explicitReuseLids = _lidReuseDelayer.delayReuse(lidsToRemove);
    }
    std::shared_ptr<search::IDestructorCallback> onWriteDone;
    if (remove_index_and_attributes) {
        if (explicitReuseLids) {
            onWriteDone = std::make_shared<search::ScheduleTaskCallback>(
                    _writeService.master(),
                    makeLambdaTask([=]()
            { _metaStore.removeBatchComplete(lidsToRemove); }));
        }
        removeIndexedFields(serialNum, lidsToRemove, immediateCommit, onWriteDone);
        removeAttributes(serialNum, lidsToRemove, immediateCommit, onWriteDone);
    }
    if (useDocumentStore(serialNum + 1)) {
        for (const auto &lid : lidsToRemove) {
            _summaryAdapter->remove(serialNum, lid);
        }
    }
    if (explicitReuseLids && !onWriteDone) {
        _metaStore.removeBatchComplete(lidsToRemove);
    }
    return lidsToRemove.size();
}

void
StoreOnlyFeedView::prepareDeleteBucket(DeleteBucketOperation &delOp)
{
    const BucketId &bucket = delOp.getBucketId();
    LidVector lidsToRemove;
    _metaStore.getLids(bucket, lidsToRemove);
    LOG(debug,
        "prepareDeleteBucket(): docType(%s), bucket(%s), lidsToRemove(%zu)",
        _params._docTypeName.toString().c_str(),
        bucket.toString().c_str(), lidsToRemove.size());

    if (!lidsToRemove.empty()) {
        LidVectorContext::SP ctx
            (new LidVectorContext(_metaStore.getCommittedDocIdLimit(),
                                  lidsToRemove));
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
    LOG(debug,
        "internalDeleteBucket(): docType(%s), bucket(%s), lidsToRemove(%zu)",
        _params._docTypeName.toString().c_str(),
        delOp.getBucketId().toString().c_str(), rm_count);
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
StoreOnlyFeedView::handleMove(const MoveOperation &moveOp)
{
    assert(moveOp.getValidDbdId());
    assert(moveOp.getValidPrevDbdId());
    assert(moveOp.movingLidIfInSameSubDb());

    const SerialNum serialNum = moveOp.getSerialNum();
    const Document::SP &doc = moveOp.getDocument();
    const DocumentId &docId = doc->getId();
    VLOG(getDebugLevel(moveOp.getNewOrPrevLid(_params._subDbId), doc->getId()),
         "database(%s): handleMove: serialNum(%" PRIu64 "), "
         "docId(%s), lid(%u,%u) prevLid(%u,%u)"
         " subDbId %u document(%ld) = {\n%s\n}",
         _params._docTypeName.toString().c_str(),
         serialNum,
         doc->getId().toString().c_str(),
         moveOp.getSubDbId(),
         moveOp.getLid(),
         moveOp.getPrevSubDbId(),
         moveOp.getPrevLid(),
         _params._subDbId,
         doc->toString(true).size(),
         doc->toString(true).c_str());

    uint32_t oldDocIdLimit = _metaStore.getCommittedDocIdLimit();
    adjustMetaStore(moveOp, docId);
    bool docAlreadyExists = moveOp.getValidPrevDbdId(_params._subDbId);
    if (moveOp.getValidDbdId(_params._subDbId)) {
        _summaryAdapter->put(serialNum, *doc, moveOp.getLid());
        bool immediateCommit = _commitTimeTracker.needCommit();
        FeedToken::UP token;
        std::shared_ptr<PutDoneContext> onWriteDone =
            createPutDoneContext(token, moveOp.getType(), _params._metrics,
                                 immediateCommit &&
                                 moveOp.getLid() >= oldDocIdLimit);
        putAttributes(serialNum, moveOp.getLid(), *doc, immediateCommit, onWriteDone);
        putIndexedFields(serialNum, moveOp.getLid(), doc, immediateCommit, onWriteDone);
    }
    if (docAlreadyExists && moveOp.changedDbdId()) {
        internalRemove(FeedToken::UP(), serialNum, moveOp.getPrevLid(), moveOp.getType());
    }
}


void
StoreOnlyFeedView::heartBeat(search::SerialNum serialNum)
{
    assert(_writeService.master().isCurrentThread());
    _metaStore.removeAllOldGenerations();
    if (serialNum > _metaStore.getLastSerialNum()) {
        _metaStore.commit(serialNum, serialNum);
    }
    _summaryAdapter->heartBeat(serialNum);
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

    LOG(debug,
        "MinimalFeedView::handlePruneRemovedDocuments called,"
        " doctype(%s)"
        " %u lids pruned,"
        " limit %u",
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
            commitContext(std::make_shared<ForceCommitContext>
                          (_writeService.master(),
                           _metaStore));
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

void
StoreOnlyFeedView::notifyGidToLidChange(const document::GlobalId &gid, uint32_t lid)
{
    (void) gid;
    (void) lid;
}

} // namespace proton
