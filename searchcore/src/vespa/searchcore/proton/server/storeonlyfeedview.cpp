// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storeonlyfeedview.h"
#include "forcecommitcontext.h"
#include "ireplayconfig.h"
#include "operationdonecontext.h"
#include "putdonecontext.h"
#include "removedonecontext.h"
#include "updatedonecontext.h"
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_pending_gid_to_lid_changes.h>
#include <vespa/searchlib/index/uri_field.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.storeonlyfeedview");

using document::BucketId;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::GlobalId;
using proton::documentmetastore::LidReuseDelayer;
using search::SerialNum;
using search::index::Schema;
using storage::spi::BucketInfoResult;
using storage::spi::Timestamp;
using vespalib::CpuUsage;
using vespalib::IDestructorCallback;
using vespalib::IllegalStateException;
using vespalib::makeLambdaTask;
using vespalib::make_string_short::fmt;

namespace proton {

namespace {

std::shared_ptr<PutDoneContext>
createPutDoneContext(FeedToken token, std::shared_ptr<IDestructorCallback> done_callback, IPendingLidTracker::Token uncommitted,
                     std::shared_ptr<const Document> doc, uint32_t lid)
{
    return std::make_shared<PutDoneContext>(std::move(token), std::move(done_callback), std::move(uncommitted), std::move(doc), lid);
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

std::shared_ptr<RemoveDoneContext>
createRemoveDoneContext(FeedToken token, std::shared_ptr<IDestructorCallback> done_callback, IPendingLidTracker::Token uncommitted)
{
    return std::make_shared<RemoveDoneContext>(std::move(token), std::move(done_callback), std::move(uncommitted));
}

class SummaryPutDoneContext : public OperationDoneContext
{
    IPendingLidTracker::Token    _uncommitted;
public:
    SummaryPutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted);
    ~SummaryPutDoneContext() override;
};

SummaryPutDoneContext::SummaryPutDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted)
    : OperationDoneContext(std::move(token), {}),
      _uncommitted(std::move(uncommitted))
{}

SummaryPutDoneContext::~SummaryPutDoneContext() = default;

std::vector<document::GlobalId>
getGidsToRemove(const IDocumentMetaStore &metaStore, const LidVectorContext::LidVector &lidsToRemove)
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

void
putMetaData(documentmetastore::IStore &meta_store, const DocumentId & doc_id,
            const DocumentOperation &op, bool is_removed_doc)
{
    documentmetastore::IStore::Result putRes(
            meta_store.put(doc_id.getGlobalId(), op.getBucketId(), op.getTimestamp(),
                           op.getSerializedDocSize(), op.getLid(), op.get_prepare_serial_num()));
    if (!putRes.ok()) {
        throw IllegalStateException(fmt("Could not put <lid, gid> pair for %sdocument with id '%s' and gid '%s'",
                                        is_removed_doc ? "removed " : "", doc_id.toString().c_str(),
                                        doc_id.getGlobalId().toString().c_str()));
    }
    assert(op.getLid() == putRes._lid);
}

void
removeMetaData(documentmetastore::IStore &meta_store, const GlobalId & gid, const DocumentId &doc_id,
               const DocumentOperation &op, bool is_removed_doc)
{
    assert(meta_store.validLid(op.getPrevLid()));
    assert(is_removed_doc == op.getPrevMarkedAsRemoved());
    const RawDocumentMetaData &meta(meta_store.getRawMetaData(op.getPrevLid()));
    assert(meta.getGid() == gid);
    (void) meta;
    if (!meta_store.remove(op.getPrevLid(), op.get_prepare_serial_num())) {
        throw IllegalStateException(
                fmt("Could not remove <lid, gid> pair for %sdocument with id '%s' and gid '%s'",
                    is_removed_doc ? "removed " : "", doc_id.toString().c_str(), gid.toString().c_str()));
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

class UpdateScope final : public IFieldUpdateCallback
{
private:
    const vespalib::hash_set<int32_t> & _indexedFields;
    bool _nonAttributeFields;
public:
    bool _hasIndexedFields;

    UpdateScope(const vespalib::hash_set<int32_t> & indexedFields, const DocumentUpdate & upd);
    bool hasIndexOrNonAttributeFields() const {
        return _hasIndexedFields || _nonAttributeFields;
    }
    void onUpdateField(const document::Field & field, const search::AttributeVector * attr) override;
};

UpdateScope::UpdateScope(const vespalib::hash_set<int32_t> & indexedFields, const DocumentUpdate & upd)
    : _indexedFields(indexedFields),
      _nonAttributeFields(!upd.getFieldPathUpdates().empty()),
      _hasIndexedFields(false)
{}

void
UpdateScope::onUpdateField(const document::Field & field, const search::AttributeVector * attr) {
    if (!_nonAttributeFields && (attr == nullptr || !attr->isUpdateableInMemoryOnly())) {
        _nonAttributeFields = true;
    }
    if (!_hasIndexedFields && (_indexedFields.find(field.getId()) != _indexedFields.end())) {
        _hasIndexedFields = true;
    }
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
      _indexedFields(),
      _writeService(ctx._writeService),
      _params(params),
      _metaStore(_documentMetaStoreContext->get()),
      _gidToLidChangeHandler(ctx._gidToLidChangeHandler)
{
    _docType = _repo->getDocumentType(_params._docTypeName.getName());
    if (_schema && _docType) {
        for (const auto &indexField : _schema->getIndexFields()) {
            size_t dotPos = indexField.getName().find('.');
            if ((dotPos == vespalib::string::npos) || search::index::UriField::mightBePartofUri(indexField.getName())) {
                document::FieldPath fieldPath;
                _docType->buildFieldPath(fieldPath, indexField.getName().substr(0, dotPos));
                _indexedFields.insert(fieldPath.back().getFieldRef().getId());
            } else {
                throw IllegalStateException("Field '%s' is not a valid index name", indexField.getName().c_str());
            }

        }
    }
}

StoreOnlyFeedView::~StoreOnlyFeedView() = default;
StoreOnlyFeedView::Context::Context(Context &&) noexcept = default;
StoreOnlyFeedView::Context::~Context() = default;

void
StoreOnlyFeedView::forceCommit(const CommitParam & param, DoneCallback onDone)
{
    if (useDocumentMetaStore(param.lastSerialNum())) {
        _metaStore.commit(param);
    }
    internalForceCommit(param, std::make_shared<ForceCommitContext>(_writeService.master(), _metaStore,
                                                                    _pendingLidsForCommit->produceSnapshot(),
                                                                    _gidToLidChangeHandler.grab_pending_changes(),
                                                                    onDone));
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
StoreOnlyFeedView::putIndexedFields(SerialNum, Lid, const std::shared_ptr<Document> &, OnOperationDoneType) {}

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

    bool docAlreadyExists = putOp.getValidPrevDbdId(_params._subDbId);

    if (putOp.getValidDbdId(_params._subDbId)) {
        if (putOp.changedDbdId() && useDocumentMetaStore(serialNum)) {
            /*
             * Don't pass replay feed token to GidToLidChangeHandler.
             *
             * The passed feed token is kept until the ForceCommitDoneTask scheduled by the next
             * force commit has completed. If a replay feed token containing an active throttler
             * token is passed to GidToLidChangeHandler then
             * TransactionLogReplayFeedHandler::make_replay_feed_token() might deadlock, waiting for
             * active throttler tokens to be destroyed.
             */
            FeedToken token_copy = (token && !token->is_replay()) ? token : FeedToken();
            _gidToLidChangeHandler.notifyPut(std::move(token_copy), docId.getGlobalId(), putOp.getLid(), serialNum);
        }
        auto onWriteDone = createPutDoneContext(std::move(token), {}, get_pending_lid_token(putOp), doc, putOp.getLid());
        putSummary(serialNum, putOp.getLid(), doc, onWriteDone);
        putAttributes(serialNum, putOp.getLid(), *doc, onWriteDone);
        putIndexedFields(serialNum, putOp.getLid(), doc, onWriteDone);
    }
    if (docAlreadyExists && putOp.changedDbdId()) {
        //TODO, better to have an else than an assert ?
        assert(!putOp.getValidDbdId(_params._subDbId));
        internalRemove(std::move(token), {}, _pendingLidsForCommit->produce(putOp.getPrevLid()), serialNum, putOp.getPrevLid());
    }
}

void
StoreOnlyFeedView::heartBeatIndexedFields(SerialNum, DoneCallback ) {}


void
StoreOnlyFeedView::heartBeatAttributes(SerialNum, DoneCallback ) {}

void
StoreOnlyFeedView::updateAttributes(SerialNum, Lid, const DocumentUpdate & upd,
                                    OnOperationDoneType, IFieldUpdateCallback & onUpdate)
{
    for (const auto & fieldUpdate : upd.getUpdates()) {
        onUpdate.onUpdateField(fieldUpdate.getField(), nullptr);
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

void
StoreOnlyFeedView::putSummary(SerialNum serialNum, Lid lid,
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

void
StoreOnlyFeedView::putSummaryNoop(FutureStream futureStream, OnOperationDoneType onDone)
{
    summaryExecutor().execute(
            makeLambdaTask([futureStream = std::move(futureStream), onDone] () mutable {
                (void) onDone;
                vespalib::nbostream os = futureStream.get();
                (void) os;
            }));
}

void
StoreOnlyFeedView::putSummary(SerialNum serialNum, Lid lid, Document::SP doc, OnOperationDoneType onDone)
{
    summaryExecutor().execute(
            makeLambdaTask([serialNum, doc = std::move(doc), trackerToken = _pendingLidsForDocStore.produce(lid), onDone, lid, this] {
                (void) onDone;
                (void) trackerToken;
                _summaryAdapter->put(serialNum, lid, *doc);
            }));
}
void
StoreOnlyFeedView::removeSummary(SerialNum serialNum, Lid lid, OnWriteDoneType onDone) {
    summaryExecutor().execute(
            makeLambdaTask([serialNum, lid, onDone, trackerToken = _pendingLidsForDocStore.produce(lid), this] {
                (void) onDone;
                (void) trackerToken;
                _summaryAdapter->remove(serialNum, lid);
            }));
}
void
StoreOnlyFeedView::removeSummaries(SerialNum serialNum, const LidVector & lids, OnWriteDoneType onDone) {
    std::vector<IPendingLidTracker::Token> trackerTokens;
    trackerTokens.reserve(lids.size());
    std::for_each(lids.begin(), lids.end(), [this, &trackerTokens](Lid lid) {
        trackerTokens.emplace_back(_pendingLidsForDocStore.produce(lid));
    });
    summaryExecutor().execute(
            makeLambdaTask([serialNum, lids, onDone, trackerTokens = std::move(trackerTokens), this] {
                (void) onDone;
                (void) trackerTokens;
                std::for_each(lids.begin(), lids.end(), [this, serialNum](Lid lid) {
                    _summaryAdapter->remove(serialNum, lid);
                });
            }));
}

void
StoreOnlyFeedView::heartBeatSummary(SerialNum serialNum, DoneCallback onDone) {
    summaryExecutor().execute(
            makeLambdaTask([serialNum, this, onDone] {
                (void) onDone;
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
    }

    auto onWriteDone = createUpdateDoneContext(std::move(token), get_pending_lid_token(updOp), updOp.getUpdate());
    UpdateScope updateScope(_indexedFields, upd);
    updateAttributes(serialNum, lid, upd, onWriteDone, updateScope);

    if (updateScope.hasIndexOrNonAttributeFields()) {
        PromisedDoc promisedDoc;
        FutureDoc futureDoc = promisedDoc.get_future().share();
        onWriteDone->setDocument(futureDoc);
        _pendingLidsForDocStore.waitComplete(lid);
        if (updateScope._hasIndexedFields) {
            updateIndexedFields(serialNum, lid, futureDoc, onWriteDone);
        }
        PromisedStream promisedStream;
        FutureStream futureStream = promisedStream.get_future();
        bool useDocStore = useDocumentStore(serialNum);
        if (useDocStore) {
            putSummary(serialNum, lid, std::move(futureStream), onWriteDone);
        } else {
            putSummaryNoop(std::move(futureStream), onWriteDone);
        }
        auto task = makeLambdaTask([upd = updOp.getUpdate(), useDocStore, lid, is_replay = onWriteDone->is_replay(),
                                           promisedDoc = std::move(promisedDoc),
                                           promisedStream = std::move(promisedStream), this]() mutable
        {
            makeUpdatedDocument(useDocStore, lid, *upd, is_replay,
                                std::move(promisedDoc), std::move(promisedStream));
        });
        _writeService.shared().execute(CpuUsage::wrap(std::move(task), CpuUsage::Category::WRITE));
        updateAttributes(serialNum, lid, std::move(futureDoc), onWriteDone);
    }
}

void
StoreOnlyFeedView::makeUpdatedDocument(bool useDocStore, Lid lid, const DocumentUpdate & update,
                                       bool is_replay, PromisedDoc promisedDoc,
                                       PromisedStream promisedStream)
{
    Document::UP prevDoc = _summaryAdapter->get(lid, *_repo);
    Document::UP newDoc;
    vespalib::nbostream newStream(12345);
    assert(is_replay || useDocStore);
    if (useDocStore) {
        assert(prevDoc);
    }
    if (!prevDoc) {
        // Replaying, document removed later before summary was flushed.
        assert(is_replay);
        // If we've passed serial number for flushed index then we could
        // also check that this operation is marked for ignore by index
        // proxy.
    } else {
        if (update.getId() == prevDoc->getId()) {
            newDoc = std::move(prevDoc);
            if (useDocStore) {
                update.applyTo(*newDoc);
                newDoc->serialize(newStream);
            }
        } else {
            // Replaying, document removed and lid reused before summary
            // was flushed.
            assert(is_replay && !useDocStore);
        }
    }
    promisedDoc.set_value(std::move(newDoc));
    promisedStream.set_value(std::move(newStream));
}

bool
StoreOnlyFeedView::lookupDocId(const DocumentId &docId, Lid &lid) const
{
    // This function should only be called by the document db main thread.
    auto result = _metaStore.inspectExisting(docId.getGlobalId(), 0);
    if (!result.ok()) {
        return false;
    }
    lid = result.getLid();
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

    if (rmOp.getValidDbdId(_params._subDbId)) {
        auto clearDoc = std::make_unique<Document>(*_repo, *_docType, docId);

        putSummary(serialNum, rmOp.getLid(), std::move(clearDoc), std::make_shared<SummaryPutDoneContext>(std::move(token), get_pending_lid_token(rmOp)));
    }
    if (rmOp.getValidPrevDbdId(_params._subDbId)) {
        if (rmOp.changedDbdId()) {
            //TODO Prefer else over assert ?
            assert(!rmOp.getValidDbdId(_params._subDbId));
            internalRemove(std::move(token), {}, _pendingLidsForCommit->produce(rmOp.getPrevLid()), serialNum, rmOp.getPrevLid());
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

    if (rmOp.getValidPrevDbdId(_params._subDbId)) {
        if (rmOp.changedDbdId()) {
            assert(!rmOp.getValidDbdId(_params._subDbId));
            internalRemove(std::move(token), {}, _pendingLidsForCommit->produce(rmOp.getPrevLid()), serialNum, rmOp.getPrevLid());
        }
    }
}

void
StoreOnlyFeedView::internalRemove(FeedToken token, std::shared_ptr<IDestructorCallback> done_callback, IPendingLidTracker::Token uncommitted, SerialNum serialNum, Lid lid)
{
    _lidReuseDelayer.delayReuse(lid);
    auto onWriteDone = createRemoveDoneContext(std::move(token), std::move(done_callback), std::move(uncommitted));
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
    }
}

void
StoreOnlyFeedView::removeAttributes(SerialNum, const LidVector &, OnWriteDoneType ) {}

void
StoreOnlyFeedView::removeIndexedFields(SerialNum , const LidVector &, OnWriteDoneType ) {}

size_t
StoreOnlyFeedView::removeDocuments(const RemoveDocumentsOperation &op, bool remove_index_and_attributes, DoneCallback onWriteDone)
{
    const SerialNum serialNum = op.getSerialNum();
    const LidVectorContext::SP &ctx = op.getLidsToRemove(_params._subDbId);
    if (!ctx) {
        return 0;
    }
    const LidVector &lidsToRemove(ctx->getLidVector());
    bool useDMS = useDocumentMetaStore(serialNum);
    if (useDMS) {
        vespalib::Gate gate;
        std::vector<document::GlobalId> gidsToRemove = getGidsToRemove(_metaStore, lidsToRemove);
        _gidToLidChangeHandler.notifyRemoves(std::make_shared<vespalib::GateCallback>(gate), gidsToRemove, serialNum);
        gate.await();
        _metaStore.removeBatch(lidsToRemove, ctx->getDocIdLimit());
        _lidReuseDelayer.delayReuse(lidsToRemove);
    }

    if (remove_index_and_attributes) {
        removeIndexedFields(serialNum, lidsToRemove, onWriteDone);
        removeAttributes(serialNum, lidsToRemove, onWriteDone);
    }
    if (useDocumentStore(serialNum + 1)) {
        removeSummaries(serialNum, lidsToRemove, onWriteDone);
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
StoreOnlyFeedView::handleDeleteBucket(const DeleteBucketOperation &delOp, DoneCallback onDone)
{
    internalDeleteBucket(delOp, onDone);
}

void
StoreOnlyFeedView::internalDeleteBucket(const DeleteBucketOperation &delOp, DoneCallback onDone)
{
    size_t rm_count = removeDocuments(delOp, true, onDone);
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
StoreOnlyFeedView::handleMove(const MoveOperation &moveOp, DoneCallback doneCtx)
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
        auto onWriteDone = createPutDoneContext({}, doneCtx, _pendingLidsForCommit->produce(moveOp.getLid()), doc, moveOp.getLid());
        putSummary(serialNum, moveOp.getLid(), doc, onWriteDone);
        putAttributes(serialNum, moveOp.getLid(), *doc, onWriteDone);
        putIndexedFields(serialNum, moveOp.getLid(), doc, onWriteDone);
    }
    if (docAlreadyExists && moveOp.changedDbdId()) {
        internalRemove({}, doneCtx, _pendingLidsForCommit->produce(moveOp.getPrevLid()), serialNum, moveOp.getPrevLid());
    }
}

void
StoreOnlyFeedView::heartBeat(SerialNum serialNum, DoneCallback onDone)
{
    assert(_writeService.master().isCurrentThread());
    _metaStore.reclaim_unused_memory();
    _metaStore.commit(CommitParam(serialNum));
    heartBeatSummary(serialNum, onDone);
    heartBeatIndexedFields(serialNum, onDone);
    heartBeatAttributes(serialNum, onDone);
}

// CombiningFeedView calls this only for the removed subdb.
void
StoreOnlyFeedView::
handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp, DoneCallback onDone)
{
    assert(_params._subDbType == SubDbType::REMOVED);
    assert(pruneOp.getSubDbId() == _params._subDbId);
    uint32_t rm_count = removeDocuments(pruneOp, false, onDone);

    LOG(debug, "MinimalFeedView::handlePruneRemovedDocuments called, doctype(%s) %u lids pruned, limit %u",
        _params._docTypeName.toString().c_str(), rm_count,
        static_cast<uint32_t>(pruneOp.getLidsToRemove()->getDocIdLimit()));
}

void
StoreOnlyFeedView::handleCompactLidSpace(const CompactLidSpaceOperation &op, DoneCallback onDone)
{
    assert(_params._subDbId == op.getSubDbId());
    const SerialNum serialNum = op.getSerialNum();
    if (useDocumentMetaStore(serialNum)) {
        getDocumentMetaStore()->get().compactLidSpace(op.getLidLimit());
        auto commitContext(std::make_shared<ForceCommitContext>(_writeService.master(), _metaStore,
                                                                _pendingLidsForCommit->produceSnapshot(),
                                                                _gidToLidChangeHandler.grab_pending_changes(),
                                                                onDone));
        commitContext->holdUnblockShrinkLidSpace();
        internalForceCommit(CommitParam(serialNum), commitContext);
    }
    if (useDocumentStore(serialNum)) {
        vespalib::Gate gate;
        _writeService.summary().execute(makeLambdaTask([this, &op, &gate]() {
            _summaryAdapter->compactLidSpace(op.getLidLimit());
            gate.countDown();
        }));
        gate.await();
    }
}

const ISimpleDocumentMetaStore *
StoreOnlyFeedView::getDocumentMetaStorePtr() const
{
    return &_documentMetaStoreContext->get();
}

} // namespace proton
