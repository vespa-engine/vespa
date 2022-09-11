// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "combiningfeedview.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/idestructorcallback.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.combiningfeedview");

using document::DocumentTypeRepo;
using document::DocumentId;
using vespalib::IDestructorCallback;
using vespalib::Trinary;

namespace proton {

namespace {

std::shared_ptr<const DocumentTypeRepo>
getRepo(const std::vector<IFeedView::SP> &views)
{
    for (const auto &view : views) {
        if (view) {
            return view->getDocumentTypeRepo();
        }
    }
    LOG_ABORT("should not be reached");
}

const char *
toStr(Trinary v) {
    return (v == Trinary::True) ? "true" : ((v == Trinary::False) ? "false" : "undefined");
}

}

CombiningFeedView::CombiningFeedView(const std::vector<IFeedView::SP> &views,
                                     document::BucketSpace bucketSpace,
                                     const std::shared_ptr<IBucketStateCalculator> &calc)
    : _repo(getRepo(views)),
      _views(views),
      _metaStores(),
      _calc(calc),
      _clusterUp(calc && calc->clusterUp()),
      _forceReady(!_clusterUp || !hasNotReadyFeedView()),
      _bucketSpace(bucketSpace)
{
    _metaStores.reserve(views.size());
    for (const auto &view : views) {
        _metaStores.push_back(view->getDocumentMetaStorePtr());
    }
    assert(getReadyFeedView() != nullptr);
    assert(getRemFeedView() != nullptr);
    if (hasNotReadyFeedView()) {
        assert(getNotReadyFeedView() != nullptr);
    }
}

CombiningFeedView::~CombiningFeedView() = default;

const ISimpleDocumentMetaStore *
CombiningFeedView::getDocumentMetaStorePtr() const
{
    return nullptr;
}

void
CombiningFeedView::findPrevDbdId(const document::GlobalId &gid, DocumentOperation &op)
{
    uint32_t subDbIdLim = _metaStores.size();
    uint32_t skipSubDbId = std::numeric_limits<uint32_t>::max();
    DbDocumentId newId(op.getDbDocumentId());
    if (newId.valid()) {
        skipSubDbId = newId.getSubDbId();
    }
    for (uint32_t subDbId = 0; subDbId < subDbIdLim; ++subDbId) {
        if (subDbId == skipSubDbId)
            continue;
        const documentmetastore::IStore *metaStore = _metaStores[subDbId];
        if (metaStore == nullptr)
            continue;
        documentmetastore::IStore::Result inspectRes(const_cast<documentmetastore::IStore *>(metaStore)->inspectExisting(gid, op.get_prepare_serial_num()));
        if (inspectRes._found) {
            op.setPrevDbDocumentId(DbDocumentId(subDbId, inspectRes._lid));
            op.setPrevMarkedAsRemoved(subDbId == getRemFeedViewId());
            op.setPrevTimestamp(storage::spi::Timestamp(inspectRes._timestamp));
            break;
        }
    }
}

const std::shared_ptr<const DocumentTypeRepo> &
CombiningFeedView::getDocumentTypeRepo() const
{
    return _repo;
}

/**
 * Similar to IFeedHandler and IPersistenceHandler functions.
 */
void
CombiningFeedView::preparePut(PutOperation &putOp)
{
    if (shouldBeReady(putOp.getBucketId()) == Trinary::True) {
        getReadyFeedView()->preparePut(putOp);
    } else {
        getNotReadyFeedView()->preparePut(putOp);
    }
    if (!putOp.getPrevDbDocumentId().valid()) {
        const DocumentId &docId = putOp.getDocument()->getId();
        const document::GlobalId &gid = docId.getGlobalId();
        findPrevDbdId(gid, putOp);
    }
}

void
CombiningFeedView::handlePut(FeedToken token, const PutOperation &putOp)
{
    assert(putOp.getValidDbdId());
    uint32_t subDbId = putOp.getSubDbId();
    uint32_t prevSubDbId = putOp.getPrevSubDbId();
    if (putOp.getValidPrevDbdId() && prevSubDbId != subDbId) {
        _views[subDbId]->handlePut(token, putOp);
        _views[prevSubDbId]->handlePut(std::move(token), putOp);
    } else {
        _views[subDbId]->handlePut(std::move(token), putOp);
    }
}

void
CombiningFeedView::prepareUpdate(UpdateOperation &updOp)
{
    getReadyFeedView()->prepareUpdate(updOp);
    if (!updOp.getPrevDbDocumentId().valid() && hasNotReadyFeedView()) {
        getNotReadyFeedView()->prepareUpdate(updOp);
    }
}

void
CombiningFeedView::handleUpdate(FeedToken token, const UpdateOperation &updOp)
{
    assert(updOp.getValidDbdId());
    assert(updOp.getValidPrevDbdId());
    assert(!updOp.changedDbdId());
    uint32_t subDbId(updOp.getSubDbId());
    _views[subDbId]->handleUpdate(std::move(token), updOp);
}

void
CombiningFeedView::prepareRemove(RemoveOperation &rmOp)
{
    getRemFeedView()->prepareRemove(rmOp);
    if (!rmOp.getPrevDbDocumentId().valid()) {
        findPrevDbdId(rmOp.getGlobalId(), rmOp);
    }
}

void
CombiningFeedView::handleRemove(FeedToken token, const RemoveOperation &rmOp)
{
    if (rmOp.getValidDbdId()) {
        uint32_t subDbId = rmOp.getSubDbId();
        uint32_t prevSubDbId = rmOp.getPrevSubDbId();
        if (rmOp.getValidPrevDbdId() && prevSubDbId != subDbId) {
            _views[subDbId]->handleRemove(token, rmOp);
            _views[prevSubDbId]->handleRemove(std::move(token), rmOp);
        } else {
            _views[subDbId]->handleRemove(std::move(token), rmOp);
        }
    } else {
        assert(rmOp.getValidPrevDbdId());
        uint32_t prevSubDbId = rmOp.getPrevSubDbId();
        _views[prevSubDbId]->handleRemove(token, rmOp);
    }
}

void
CombiningFeedView::prepareDeleteBucket(DeleteBucketOperation &delOp)
{
    for (const auto &view : _views) {
        view->prepareDeleteBucket(delOp);
    }
}

void
CombiningFeedView::handleDeleteBucket(const DeleteBucketOperation &delOp, DoneCallback onDone)
{
    for (const auto &view : _views) {
        view->handleDeleteBucket(delOp, onDone);
    }
}

void
CombiningFeedView::prepareMove(MoveOperation &moveOp)
{
    uint32_t subDbId = moveOp.getSubDbId();
    assert(subDbId < _views.size());
    _views[subDbId]->prepareMove(moveOp);
}

void
CombiningFeedView::handleMove(const MoveOperation &moveOp, DoneCallback moveDoneCtx)
{
    assert(moveOp.getValidDbdId());
    uint32_t subDbId = moveOp.getSubDbId();
    uint32_t prevSubDbId = moveOp.getPrevSubDbId();
    if (moveOp.getValidPrevDbdId() && prevSubDbId != subDbId) {
        _views[subDbId]->handleMove(moveOp, moveDoneCtx);
        // XXX: index executor not synced.
        _views[prevSubDbId]->handleMove(moveOp, moveDoneCtx);
    } else {
        _views[subDbId]->handleMove(moveOp, moveDoneCtx);
    }
}

void
CombiningFeedView::heartBeat(search::SerialNum serialNum, DoneCallback onDone)
{
    for (const auto &view : _views) {
        view->heartBeat(serialNum, onDone);
    }
}

void
CombiningFeedView::forceCommit(const CommitParam & param, DoneCallback onDone)
{
    for (const auto &view : _views) {
        view->forceCommit(param, onDone);
    }
}

void
CombiningFeedView::
handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp, DoneCallback onDone)
{
    getRemFeedView()->handlePruneRemovedDocuments(pruneOp, onDone);
}

void
CombiningFeedView::handleCompactLidSpace(const CompactLidSpaceOperation &op, DoneCallback onDone)
{
    uint32_t subDbId = op.getSubDbId();
    assert(subDbId < _views.size());
    _views[subDbId]->handleCompactLidSpace(op, onDone);
}

void
CombiningFeedView::setCalculator(const std::shared_ptr<IBucketStateCalculator> &newCalc)
{
    // Called by document db executor
    _calc = newCalc;
    _clusterUp = _calc && _calc->clusterUp();
    _forceReady = !_clusterUp || !hasNotReadyFeedView();
}

Trinary
CombiningFeedView::shouldBeReady(const document::BucketId &bucket) const
{
    document::Bucket dbucket(_bucketSpace, bucket);
    LOG(debug,
        "shouldBeReady(%s): forceReady(%s), clusterUp(%s), calcReady(%s)",
        bucket.toString().c_str(),
        (_forceReady ? "true" : "false"),
        (_clusterUp ? "true" : "false"),
        (_calc ? toStr(_calc->shouldBeReady(dbucket)) : "null"));
    const documentmetastore::IBucketHandler *readyMetaStore = _metaStores[getReadyFeedViewId()];
    bool isActive = readyMetaStore->getBucketDB().takeGuard()->isActiveBucket(bucket);
    return (_forceReady || isActive) ? Trinary::True : _calc->shouldBeReady(dbucket);
}

} // namespace proton
