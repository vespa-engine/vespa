// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storeonlyfeedview.h"
#include "forcecommitcontext.h"
#include "ireplayconfig.h"
#include "operationdonecontext.h"
#include "putdonecontext.h"
#include "removedonecontext.h"
#include "updatedonecontext.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_pending_gid_to_lid_changes.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.storeonlyfeedview");

using document::BucketId;
using document::Document;
using document::DocumentId;
using document::GlobalId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using vespalib::IDestructorCallback;
using search::SerialNum;
using search::index::Schema;
using storage::spi::BucketInfoResult;
using storage::spi::Timestamp;
using vespalib::IllegalStateException;
using vespalib::makeLambdaTask;
using vespalib::make_string;
using proton::documentmetastore::LidReuseDelayer;

namespace proton {

namespace {

class PutDoneContextForMove : public PutDoneContext {
private:
    IDestructorCallback::SP _moveDoneCtx;

public:
    PutDoneContextForMove(FeedToken token, IPendingLidTracker::Token uncommitted,
                          std::shared_ptr<const Document> doc,
                          uint32_t lid,
                          IDestructorCallback::SP moveDoneCtx)
        : PutDoneContext(std::move(token), std::move(uncommitted),std::move(doc), lid),
          _moveDoneCtx(std::move(moveDoneCtx))
    {}
    ~PutDoneContextForMove() override = default;
};

std::shared_ptr<PutDoneContext>
createPutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted,
                     std::shared_ptr<const Document> doc,
                     uint32_t lid,
                     IDestructorCallback::SP moveDoneCtx)
{
    std::shared_ptr<PutDoneContext> result;
    if (moveDoneCtx) {
        result = std::make_shared<PutDoneContextForMove>(std::move(token), std::move(uncommitted),
                                                         std::move(doc), lid, std::move(moveDoneCtx));
    } else {
        result = std::make_shared<PutDoneContext>(std::move(token), std::move(uncommitted),
                                                  std::move(doc), lid);
    }
    return result;
}

std::shared_ptr<PutDoneContext>
createPutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted,
                     std::shared_ptr<const Document> doc,
                     uint32_t lid)
{
    return createPutDoneContext(std::move(token), std::move(uncommitted), std::move(doc),
                                lid, IDestructorCallback::SP());
}

std::shared_ptr<UpdateDoneContext>
createUpdateDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted, const DocumentUpdate::SP &upd)
{
    return std::make_shared<UpdateDoneContext>(std::move(token), std::move(uncommitted), upd);
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
    RemoveDoneContextForMove(FeedToken token, IPendingLidTracker::Token uncommitted, vespalib::Executor &executor,
                             IDocumentMetaStore &documentMetaStore,
                             uint32_t lid, IDestructorCallback::SP moveDoneCtx)
        : RemoveDoneContext(std::move(token), std::move(uncommitted), executor,
                            documentMetaStore, lid),
          _moveDoneCtx(std::move(moveDoneCtx))
    {}
    ~RemoveDoneContextForMove() override = default;
};

std::shared_ptr<RemoveDoneContext>
createRemoveDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted, vespalib::Executor &executor,
                        IDocumentMetaStore &documentMetaStore,
                        uint32_t lid, IDestructorCallback::SP moveDoneCtx)
{
    if (moveDoneCtx) {
        return std::make_shared<RemoveDoneContextForMove>
            (std::move(token), std::move(uncommitted), executor, documentMetaStore,
             lid, std::move(moveDoneCtx));
    } else {
        return std::make_shared<RemoveDoneContext>
            (std::move(token), std::move(uncommitted), executor, documentMetaStore, lid);
    }
}

class SummaryPutDoneContext : public OperationDoneContext
{
    IPendingLidTracker::Token    _uncommitted;
public:
    SummaryPutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted);
    ~SummaryPutDoneContext() override;
};

SummaryPutDoneContext::SummaryPutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted)
    : OperationDoneContext(std::move(token)),
      _uncommitted(std::move(uncommitted))
{}

SummaryPutDoneContext::~SummaryPutDoneContext() = default;

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

void putMetaData(documentmetastore::IStore &meta_store, const DocumentId & doc_id,
                 const DocumentOperation &op, bool is_removed_doc)
{
    documentmetastore::IStore::Result putRes(
            meta_store.put(doc_id.getGlobalId(),
                           op.getBucketId(), op.getTimestamp(), op.getSerializedDocSize(), op.getLid(), op.get_prepare_serial_num()));
    if (!putRes.ok()) {
        throw IllegalStateException(
                make_string("Could not put <lid, gid> pair for %sdocument with id '%s' and gid '%s'",
                            is_removed_doc ? "removed " : "", doc_id.toString().c_str(),
                            doc_id.getGlobalId().toString().c_str()));
    }
    assert(op.getLid() == putRes._lid);
}

void removeMetaData(documentmetastore::IStore &meta_store, const GlobalId & gid, const DocumentId &doc_id,
                    const DocumentOperation &op, bool is_removed_doc)
{
    assert(meta_store.validLid(op.getPrevLid()));
    assert(is_removed_doc == op.getPrevMarkedAsRemoved());
    const RawDocumentMetaData &meta(meta_store.getRawMetaData(op.getPrevLid()));
    assert(meta.getGid() == gid);
    (void) meta;
    if (!meta_store.remove(op.getPrevLid(), op.get_prepare_serial_num())) {
        throw IllegalStateException(
                make_string("Could not remove <lid, gid> pair for %sdocument with id '%s' and gid '%s'",
                            is_removed_doc ? "removed " : "", doc_id.toString().c_str(),
                            gid.toString().c_str()));
    }
}

void
moveMetaData(documentmetastore::IStore &meta_store, const DocumentId & doc_id, const DocumentOperation &op)
{
    (void) doc_id;
    assert(op.getLid() != op.getPrevLid());
    assert(meta_store.validLid(op.getPrevLid()));
    assert(!meta_store.validLid(op.getLid()));
    const RawDocumentMetaData &meta(meta_store.getRawMetaData(op.getPrevLid()));
    (void) meta;
    assert(meta.getGid() == doc_id.getGlobalId());
    assert(meta.getTimestamp() == op.getTimestamp());
    meta_store.move(op.getPrevLid(), op.getLid(), op.get_prepare_serial_num());
}

}  // namespace

StoreOnlyFeedView::StoreOnlyFeedView(Context ctx, const PersistentParams &params)
    : IFeedView(),
      FeedDebugger(),
      _summaryAdapter(std::move(ctx._summaryAdapter)),
      _documentMetaStoreContext(std::move(ctx._documentMetaStoreContext)),
      _repo(ctx._repo),
      _docType(nullptr),
      _lidReuseDelayer(ctx._writeService, _documentMetaStoreContext->get()),
      _pendingLidsForDocStore(),
      _pendingLidsForCommit(std::move(ctx._pendingLidsForCommit)),
      _schema(std::move(ctx._schema)),
      _writeService(ctx._writeService),
      _params(params),
      _metaStore(_documentMetaStoreContext->get()),
      _gidToLidChangeHandler(ctx._gidToLidChangeHandler)
{
    _docType = _repo->getDocumentType(_params._docTypeName.getName());
}

StoreOnlyFeedView::~StoreOnlyFeedView() = default;
StoreOnlyFeedView::Context::Context(Context &&) noexcept = default;
StoreOnlyFeedView::Context::~Context() = default;

void
StoreOnlyFeedView::sync()
{
    _writeService.summary().sync();
}

void
StoreOnlyFeedView::forceCommit(const CommitParam & param, DoneCallback onDone)
{
    internalForceCommit(param, std::make_shared<ForceCommitContext>(_writeService.master(), _metaStore,
                                                                    _pendingLidsForCommit->produceSnapshot(),
                                                                    _gidToLidChangeHandler.grab_pending_changes(),
                                                                    std::move(onDone)));
}

void
StoreOnlyFeedView::internalForceCommit(const CommitParam & param, OnForceCommitDoneType onCommitDone)
{
    LOG(debug, "internalForceCommit: serial=%" PRIu64 ".", param.lastSerialNum());
    _writeService.summary().execute(makeLambdaTask([onDone=onCommitDone]() {(void) onDone;}));
    _writeService.summary().wakeup();
    std::vector<uint32_t> lidsToReuse;
    lidsToReuse = _lidReuseDelayer.getReuseLids();
    if (!lidsToReuse.empty()) {
        onCommitDone->reuseLids(std::move(lidsToReuse));
    }
}

IPendingLidTracker::Token
StoreOnlyFeedView::get_pending_lid_token(const DocumentOperation &op)
{
    return (op.getValidDbdId(_params._subDbId) ? _pendingLidsForCommit->produce(op.getLid()) : IPendingLidTracker::Token());
}

void
StoreOnlyFeedView::putAttributes(SerialNum, Lid, const Document &, OnPutDoneType) {}

void
StoreOnlyFeedView::putIndexedFields(SerialNum, Lid, const Document::SP &, OnOperationDoneType) {}

void
StoreOnlyFeedView::preparePut(PutOperation &putOp)
{
    const DocumentId &docId = putOp.getDocument()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    documentmetastore::IStore::Result inspectResult = _metaStore.inspect(gid, putOp.get_prepare_serial_num());
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

    adjustMetaStore(putOp, docId.getGlobalId(), docId);
    auto uncommitted = get_pending_lid_token(putOp);

    bool docAlreadyExists = putOp.getValidPrevDbdId(_params._subDbId);

    if (putOp.getValidDbdId(_params._subDbId)) {
        if (putOp.changedDbdId() && useDocumentMetaStore(serialNum)) {
            _gidToLidChangeHandler.notifyPut(token, docId.getGlobalId(), putOp.getLid(), serialNum);
        }
        std::shared_ptr<PutDoneContext> onWriteDone =
            createPutDoneContext(std::move(token), std::move(uncommitted),
                                 doc, putOp.getLid());
        putSummary(serialNum, putOp.getLid(), doc, onWriteDone);
        putAttributes(serialNum, putOp.getLid(), *doc, onWriteDone);
        putIndexedFields(serialNum, putOp.getLid(), doc, onWriteDone);
    }
    if (docAlreadyExists && putOp.changedDbdId()) {
        assert(!putOp.getValidDbdId(_params._subDbId));
        internalRemove(std::move(token), _pendingLidsForCommit->produce(putOp.getPrevLid()), serialNum,
                       putOp.getPrevLid(), IDestructorCallback::SP());
    }
}

void
StoreOnlyFeedView::heartBeatIndexedFields(SerialNum ) {}


void
StoreOnlyFeedView::heartBeatAttributes(SerialNum ) {}

void
StoreOnlyFeedView::updateAttributes(SerialNum, Lid, const DocumentUpdate & upd,
                                    OnOperationDoneType, IFieldUpdateCallback & onUpdate)
{
    for (const auto & fieldUpdate : upd.getUpdates()) {
        onUpdate.onUpdateField(fieldUpdate.getField().getName(), nullptr);
    }
}

void
StoreOnlyFeedView::updateAttributes(SerialNum, Lid, FutureDoc, OnOperationDoneType)
{
}

void
StoreOnlyFeedView::updateIndexedFields(SerialNum, Lid, FutureDoc, OnOperationDoneType)
{
}

void
StoreOnlyFeedView::prepareUpdate(UpdateOperation &updOp)
{
    const DocumentId &docId = updOp.getUpdate()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    documentmetastore::IStore::Result inspectResult = _metaStore.inspect(gid, updOp.get_prepare_serial_num());
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
    summaryExecutor().execute(
            makeLambdaTask([serialNum, lid, futureStream = std::move(futureStream), trackerToken = _pendingLidsForDocStore.produce(lid), onDone, this] () mutable {
                (void) onDone;
                (void) trackerToken;
                vespalib::nbostream os = futureStream.get();
                if (!os.empty()) {
                    _summaryAdapter->put(serialNum, lid, os);
                }
            }));
}

void StoreOnlyFeedView::putSummary(SerialNum serialNum, Lid lid, Document::SP doc, OnOperationDoneType onDone)
{
    summaryExecutor().execute(
            makeLambdaTask([serialNum, doc = std::move(doc), trackerToken = _pendingLidsForDocStore.produce(lid), onDone, lid, this] {
                (void) onDone;
                (void) trackerToken;
                _summaryAdapter->put(serialNum, lid, *doc);
            }));
}
void StoreOnlyFeedView::removeSummary(SerialNum serialNum, Lid lid, OnWriteDoneType onDone) {
    summaryExecutor().execute(
            makeLambdaTask([serialNum, lid, onDone, trackerToken = _pendingLidsForDocStore.produce(lid), this] {
                (void) onDone;
                (void) trackerToken;
                _summaryAdapter->remove(serialNum, lid);
            }));
}
void StoreOnlyFeedView::heartBeatSummary(SerialNum serialNum) {
    summaryExecutor().execute(
            makeLambdaTask([serialNum, this] {
                _summaryAdapter->heartBeat(serialNum);
            }));
}

StoreOnlyFeedView::UpdateScope::UpdateScope(const search::index::Schema & schema, const DocumentUpdate & upd)
    : _schema(&schema),
      _indexedFields(false),
      _nonAttributeFields(!upd.getFieldPathUpdates().empty())
{}

void
StoreOnlyFeedView::UpdateScope::onUpdateField(vespalib::stringref fieldName, const search::AttributeVector * attr) {
    if (!_nonAttributeFields && (attr == nullptr || !attr->isUpdateableInMemoryOnly())) {
        _nonAttributeFields = true;
    }
    if (!_indexedFields && _schema->isIndexField(fieldName)) {
        _indexedFields = true;
    }
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
         "database(%s): internalUpdate: serialNum(%" PRIu64 "), docId(%s), lid(%d)",
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
        _metaStore.commit(CommitParam(serialNum));
    }
    auto uncommitted = get_pending_lid_token(updOp);

    auto onWriteDone = createUpdateDoneContext(std::move(token), std::move(uncommitted), updOp.getUpdate());
    UpdateScope updateScope(*_schema, upd);
    updateAttributes(serialNum, lid, upd, onWriteDone, updateScope);

    if (updateScope.hasIndexOrNonAttributeFields()) {
        PromisedDoc promisedDoc;
        FutureDoc futureDoc = promisedDoc.get_future().share();
        onWriteDone->setDocument(futureDoc);
        _pendingLidsForDocStore.waitComplete(lid);
        if (updateScope._indexedFields) {
            updateIndexedFields(serialNum, lid, futureDoc, onWriteDone);
        }
        PromisedStream promisedStream;
        FutureStream futureStream = promisedStream.get_future();
        if (useDocumentStore(serialNum)) {
            putSummary(serialNum, lid, std::move(futureStream), onWriteDone);
        }
        _writeService.shared().execute(makeLambdaTask(
                         [upd = updOp.getUpdate(), serialNum, lid, onWriteDone, promisedDoc = std::move(promisedDoc),
                          promisedStream = std::move(promisedStream), this]() mutable
                          {
                             makeUpdatedDocument(serialNum, lid, *upd, onWriteDone,
                                                 std::move(promisedDoc), std::move(promisedStream));
                          }));
        updateAttributes(serialNum, lid, std::move(futureDoc), onWriteDone);
    }
}

void
StoreOnlyFeedView::makeUpdatedDocument(SerialNum serialNum, Lid lid, const DocumentUpdate & update,
                                       OnOperationDoneType onWriteDone, PromisedDoc promisedDoc,
                                       PromisedStream promisedStream)
{
    Document::UP prevDoc = _summaryAdapter->get(lid, *_repo);
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
        if (update.getId() == prevDoc->getId()) {
            newDoc = std::move(prevDoc);
            if (useDocumentStore(serialNum)) {
                update.applyTo(*newDoc);
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
StoreOnlyFeedView::removeAttributes(SerialNum, Lid, OnRemoveDoneType) {}

void
StoreOnlyFeedView::removeIndexedFields(SerialNum, Lid, OnRemoveDoneType) {}

void
StoreOnlyFeedView::prepareRemove(RemoveOperation &rmOp)
{
    documentmetastore::IStore::Result inspectRes = _metaStore.inspect(rmOp.getGlobalId(), rmOp.get_prepare_serial_num());
    if ((_params._subDbType == SubDbType::REMOVED) && (rmOp.getType() == FeedOperation::REMOVE)) {
        rmOp.setDbDocumentId(DbDocumentId(_params._subDbId, inspectRes._lid));
    }
    setPrev(rmOp, inspectRes, _params._subDbId, _params._subDbType == SubDbType::REMOVED);
}

void
StoreOnlyFeedView::handleRemove(FeedToken token, const RemoveOperation &rmOp) {
    if (rmOp.getType() == FeedOperation::REMOVE) {
        internalRemove(std::move(token), dynamic_cast<const RemoveOperationWithDocId &>(rmOp));
    } else if (rmOp.getType() == FeedOperation::REMOVE_GID) {
        internalRemove(std::move(token), dynamic_cast<const RemoveOperationWithGid &>(rmOp));
    } else {
        assert(rmOp.getType() == FeedOperation::REMOVE);
    }

}

void
StoreOnlyFeedView::internalRemove(FeedToken token, const RemoveOperationWithDocId &rmOp)
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

    adjustMetaStore(rmOp, docId.getGlobalId(), docId);
    auto uncommitted = get_pending_lid_token(rmOp);

    if (rmOp.getValidDbdId(_params._subDbId)) {
        auto clearDoc = std::make_unique<Document>(*_docType, docId);
        clearDoc->setRepo(*_repo);

        putSummary(serialNum, rmOp.getLid(), std::move(clearDoc), std::make_shared<SummaryPutDoneContext>(std::move(token), std::move(uncommitted)));
    }
    if (rmOp.getValidPrevDbdId(_params._subDbId)) {
        if (rmOp.changedDbdId()) {
            assert(!rmOp.getValidDbdId(_params._subDbId));
            internalRemove(std::move(token), _pendingLidsForCommit->produce(rmOp.getPrevLid()), serialNum,
                           rmOp.getPrevLid(), IDestructorCallback::SP());
        }
    }
}

void
StoreOnlyFeedView::internalRemove(FeedToken token, const RemoveOperationWithGid &rmOp)
{
    assert(rmOp.getValidNewOrPrevDbdId());
    assert(rmOp.notMovingLidInSameSubDb());
    const SerialNum serialNum = rmOp.getSerialNum();
    DocumentId dummy;
    adjustMetaStore(rmOp, rmOp.getGlobalId(), dummy);
    auto uncommitted = _pendingLidsForCommit->produce(rmOp.getLid());

    if (rmOp.getValidPrevDbdId(_params._subDbId)) {
        if (rmOp.changedDbdId()) {
            assert(!rmOp.getValidDbdId(_params._subDbId));
            internalRemove(std::move(token), _pendingLidsForCommit->produce(rmOp.getPrevLid()), serialNum,
                           rmOp.getPrevLid(), IDestructorCallback::SP());
        }
    }
}

void
StoreOnlyFeedView::internalRemove(FeedToken token, IPendingLidTracker::Token uncommitted, SerialNum serialNum,
                                  Lid lid,
                                  IDestructorCallback::SP moveDoneCtx)
{
    bool explicitReuseLid = _lidReuseDelayer.delayReuse(lid);
    std::shared_ptr<RemoveDoneContext> onWriteDone;
    onWriteDone = createRemoveDoneContext(std::move(token), std::move(uncommitted),_writeService.master(), _metaStore,
                                          (explicitReuseLid ? lid : 0u),
                                          std::move(moveDoneCtx));
    removeSummary(serialNum, lid, onWriteDone);
    removeAttributes(serialNum, lid, onWriteDone);
    removeIndexedFields(serialNum, lid, onWriteDone);
}

void
StoreOnlyFeedView::adjustMetaStore(const DocumentOperation &op, const GlobalId & gid, const DocumentId &docId)
{
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
            vespalib::Gate gate;
            _gidToLidChangeHandler.notifyRemove(std::make_shared<vespalib::GateCallback>(gate), gid, serialNum);
            gate.await();
            removeMetaData(_metaStore, gid, docId, op, _params._subDbType == SubDbType::REMOVED);
        }
        _metaStore.commit(CommitParam(serialNum));
    }
}

void
StoreOnlyFeedView::removeAttributes(SerialNum, const LidVector &, OnWriteDoneType ) {}

void
StoreOnlyFeedView::removeIndexedFields(SerialNum , const LidVector &, OnWriteDoneType ) {}

size_t
StoreOnlyFeedView::removeDocuments(const RemoveDocumentsOperation &op, bool remove_index_and_attributes)
{
    const SerialNum serialNum = op.getSerialNum();
    const LidVectorContext::SP &ctx = op.getLidsToRemove(_params._subDbId);
    if (!ctx) {
        if (useDocumentMetaStore(serialNum)) {
            _metaStore.commit(CommitParam(serialNum));
        }
        return 0;
    }
    const LidVector &lidsToRemove(ctx->getLidVector());
    bool useDMS = useDocumentMetaStore(serialNum);
    bool explicitReuseLids = false;
    std::vector<document::GlobalId> gidsToRemove;
    if (useDMS) {
        vespalib::Gate gate;
        gidsToRemove = getGidsToRemove(_metaStore, lidsToRemove);
        {
            IGidToLidChangeHandler::IDestructorCallbackSP context = std::make_shared<vespalib::GateCallback>(gate);
            for (const auto &gid : gidsToRemove) {
                _gidToLidChangeHandler.notifyRemove(context, gid, serialNum);
            }
        }
        gate.await();
        _metaStore.removeBatch(lidsToRemove, ctx->getDocIdLimit());
        _metaStore.commit(CommitParam(serialNum));
        explicitReuseLids = _lidReuseDelayer.delayReuse(lidsToRemove);
    }
    std::shared_ptr<vespalib::IDestructorCallback> onWriteDone;
    vespalib::Executor::Task::UP removeBatchDoneTask;
    if (explicitReuseLids) {
        removeBatchDoneTask = makeLambdaTask([this, lidsToRemove]() { _metaStore.removeBatchComplete(lidsToRemove); });
    } else {
        removeBatchDoneTask = makeLambdaTask([]() {});
    }
    onWriteDone = std::make_shared<search::ScheduleTaskCallback>(_writeService.master(), std::move(removeBatchDoneTask));
    if (remove_index_and_attributes) {
        removeIndexedFields(serialNum, lidsToRemove, onWriteDone);
        removeAttributes(serialNum, lidsToRemove, onWriteDone);
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
        delOp.setLidsToRemove(_params._subDbId, std::make_shared<LidVectorContext>(_metaStore.getCommittedDocIdLimit(), lidsToRemove));
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
    size_t rm_count = removeDocuments(delOp, true);
    LOG(debug, "internalDeleteBucket(): docType(%s), bucket(%s), lidsToRemove(%zu)",
        _params._docTypeName.toString().c_str(), delOp.getBucketId().toString().c_str(), rm_count);
}

// CombiningFeedView calls this only for the subdb we're moving to.
void
StoreOnlyFeedView::prepareMove(MoveOperation &moveOp)
{
    const DocumentId &docId = moveOp.getDocument()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    documentmetastore::IStore::Result inspectResult = _metaStore.inspect(gid, moveOp.get_prepare_serial_num());
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

    adjustMetaStore(moveOp, docId.getGlobalId(), docId);
    bool docAlreadyExists = moveOp.getValidPrevDbdId(_params._subDbId);
    if (moveOp.getValidDbdId(_params._subDbId)) {
        if (moveOp.changedDbdId() && useDocumentMetaStore(serialNum)) {
            _gidToLidChangeHandler.notifyPut(FeedToken(), docId.getGlobalId(), moveOp.getLid(), serialNum);
        }
        std::shared_ptr<PutDoneContext> onWriteDone =
            createPutDoneContext(FeedToken(), _pendingLidsForCommit->produce(moveOp.getLid()),
                                 doc, moveOp.getLid(), doneCtx);
        putSummary(serialNum, moveOp.getLid(), doc, onWriteDone);
        putAttributes(serialNum, moveOp.getLid(), *doc, onWriteDone);
        putIndexedFields(serialNum, moveOp.getLid(), doc, onWriteDone);
    }
    if (docAlreadyExists && moveOp.changedDbdId()) {
        internalRemove(FeedToken(), _pendingLidsForCommit->produce(moveOp.getPrevLid()), serialNum, moveOp.getPrevLid(), doneCtx);
    }
}

void
StoreOnlyFeedView::heartBeat(SerialNum serialNum)
{
    assert(_writeService.master().isCurrentThread());
    _metaStore.removeAllOldGenerations();
    if (serialNum > _metaStore.getLastSerialNum()) {
        _metaStore.commit(CommitParam(serialNum));
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
    uint32_t rm_count = removeDocuments(pruneOp, false);

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
        auto commitContext(std::make_shared<ForceCommitContext>(_writeService.master(), _metaStore,
                                                                _pendingLidsForCommit->produceSnapshot(),
                                                                _gidToLidChangeHandler.grab_pending_changes(),
                                                                DoneCallback()));
        commitContext->holdUnblockShrinkLidSpace();
        internalForceCommit(CommitParam(serialNum), commitContext);
    }
    if (useDocumentStore(serialNum)) {
        _writeService.summary().execute(makeLambdaTask([this, &op]() {
            _summaryAdapter->compactLidSpace(op.getLidLimit());
        }));
        _writeService.summary().sync();
    }
}

const ISimpleDocumentMetaStore *
StoreOnlyFeedView::getDocumentMetaStorePtr() const
{
    return &_documentMetaStoreContext->get();
}

} // namespace proton
