// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fileconfigmanager.h"
#include "ifeedview.h"
#include "isummaryadapter.h"
#include "replaypacketdispatcher.h"
#include "searchcontext.h"
#include "pendinglidtracker.h"
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/common/feeddebugger.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/feedoperation/lidvectorcontext.h>
#include <vespa/searchcore/proton/persistenceengine/resulthandler.h>
#include <vespa/searchcore/proton/reference/pending_notify_remove_done.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>
#include <future>

namespace search { class IDestructorCallback; }


namespace proton {

class IReplayConfig;
class ForceCommitContext;
class OperationDoneContext;
class PutDoneContext;
class RemoveDoneContext;
class CommitTimeTracker;
class IGidToLidChangeHandler;
class IFieldUpdateCallback;
class RemoveDocumentsOperation;
class DocumentOperation;

namespace documentmetastore { class ILidReuseDelayer; }

/**
 * The feed view used by the store-only sub database.
 *
 * Handles inserting/updating/removing of documents to the underlying document store.
 */
class StoreOnlyFeedView : public IFeedView,
                          protected FeedDebugger
{
protected:
    typedef search::transactionlog::Packet Packet;
public:
    using UP = std::unique_ptr<StoreOnlyFeedView>;
    using SP = std::shared_ptr<StoreOnlyFeedView>;
    using SerialNum = search::SerialNum;
    using LidVector = LidVectorContext::LidVector;
    using Document = document::Document;
    using DocumentUpdate = document::DocumentUpdate;
    using OnWriteDoneType =const std::shared_ptr<search::IDestructorCallback> &;
    using OnForceCommitDoneType =const std::shared_ptr<ForceCommitContext> &;
    using OnOperationDoneType = const std::shared_ptr<OperationDoneContext> &;
    using OnPutDoneType = const std::shared_ptr<PutDoneContext> &;
    using OnRemoveDoneType = const std::shared_ptr<RemoveDoneContext> &;
    using FeedTokenUP = std::unique_ptr<FeedToken>;
    using FutureDoc = std::shared_future<std::unique_ptr<const Document>>;
    using PromisedDoc = std::promise<std::unique_ptr<const Document>>;
    using FutureStream = std::future<vespalib::nbostream>;
    using PromisedStream = std::promise<vespalib::nbostream>;
    using DocumentSP = std::shared_ptr<Document>;
    using DocumentUpdateSP = std::shared_ptr<DocumentUpdate>;

    using Lid = search::DocumentIdT;

    struct Context
    {
        const ISummaryAdapter::SP               &_summaryAdapter;
        const search::index::Schema::SP         &_schema;
        const IDocumentMetaStoreContext::SP     &_documentMetaStoreContext;
        IGidToLidChangeHandler                  &_gidToLidChangeHandler;
        const std::shared_ptr<const document::DocumentTypeRepo>    &_repo;
        searchcorespi::index::IThreadingService &_writeService;
        documentmetastore::ILidReuseDelayer     &_lidReuseDelayer;
        CommitTimeTracker                       &_commitTimeTracker;

        Context(const ISummaryAdapter::SP &summaryAdapter,
                const search::index::Schema::SP &schema,
                const IDocumentMetaStoreContext::SP &documentMetaStoreContext,
                IGidToLidChangeHandler &gidToLidChangeHandler,
                const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                searchcorespi::index::IThreadingService &writeService,
                documentmetastore::ILidReuseDelayer &lidReuseDelayer,
                CommitTimeTracker &commitTimeTracker)
            : _summaryAdapter(summaryAdapter),
              _schema(schema),
              _documentMetaStoreContext(documentMetaStoreContext),
              _gidToLidChangeHandler(gidToLidChangeHandler),
              _repo(repo),
              _writeService(writeService),
              _lidReuseDelayer(lidReuseDelayer),
              _commitTimeTracker(commitTimeTracker)
        {}
    };

    struct PersistentParams
    {
        const SerialNum        _flushedDocumentMetaStoreSerialNum;
        const SerialNum        _flushedDocumentStoreSerialNum;
        const DocTypeName      _docTypeName;
        const uint32_t         _subDbId;
        const SubDbType        _subDbType;

        PersistentParams(SerialNum flushedDocumentMetaStoreSerialNum,
                         SerialNum flushedDocumentStoreSerialNum,
                         const DocTypeName &docTypeName,
                         uint32_t subDbId,
                         SubDbType subDbType)
            : _flushedDocumentMetaStoreSerialNum(flushedDocumentMetaStoreSerialNum),
              _flushedDocumentStoreSerialNum(flushedDocumentStoreSerialNum),
              _docTypeName(docTypeName),
              _subDbId(subDbId),
              _subDbType(subDbType)
        {}
    };

protected:
    class UpdateScope : public IFieldUpdateCallback
    {
    private:
        const search::index::Schema *_schema;
    public:
        bool _indexedFields;
        bool _nonAttributeFields;

        UpdateScope(const search::index::Schema & schema, const DocumentUpdate & upd);
        bool hasIndexOrNonAttributeFields() const {
            return _indexedFields || _nonAttributeFields;
        }
        void onUpdateField(vespalib::stringref fieldName, const search::AttributeVector * attr) override;
    };

private:
    const ISummaryAdapter::SP                _summaryAdapter;
    const IDocumentMetaStoreContext::SP      _documentMetaStoreContext;
    const std::shared_ptr<const document::DocumentTypeRepo>     _repo;
    const document::DocumentType            *_docType;
    documentmetastore::ILidReuseDelayer     &_lidReuseDelayer;
    CommitTimeTracker                       &_commitTimeTracker;
    PendingLidTracker                        _pendingLidTracker;

protected:
    const search::index::Schema::SP          _schema;
    searchcorespi::index::IThreadingService &_writeService;
    PersistentParams                         _params;
    IDocumentMetaStore                      &_metaStore;
    IGidToLidChangeHandler                  &_gidToLidChangeHandler;

private:
    searchcorespi::index::IThreadService & summaryExecutor() {
        return _writeService.summary();
    }
    void putSummary(SerialNum serialNum,  Lid lid, FutureStream doc, OnOperationDoneType onDone);
    void putSummary(SerialNum serialNum,  Lid lid, DocumentSP doc, OnOperationDoneType onDone);
    void removeSummary(SerialNum serialNum,  Lid lid, OnWriteDoneType onDone);
    void heartBeatSummary(SerialNum serialNum);


    bool useDocumentStore(SerialNum replaySerialNum) const {
        return replaySerialNum > _params._flushedDocumentStoreSerialNum;
    }
    bool useDocumentMetaStore(SerialNum replaySerialNum) const {
        return replaySerialNum > _params._flushedDocumentMetaStoreSerialNum;
    }

    PendingNotifyRemoveDone adjustMetaStore(const DocumentOperation &op, const document::DocumentId &docId);
    void internalPut(FeedToken token, const PutOperation &putOp);
    void internalUpdate(FeedToken token, const UpdateOperation &updOp);

    bool lookupDocId(const document::DocumentId &docId, Lid & lid) const;
    void internalRemove(FeedToken token, const RemoveOperation &rmOp);

    // Removes documents from meta store and document store.
    // returns the number of documents removed.
    size_t removeDocuments(const RemoveDocumentsOperation &op, bool remove_index_and_attribute_fields,
                           bool immediateCommit);

    void internalRemove(FeedToken token, SerialNum serialNum, PendingNotifyRemoveDone &&pendingNotifyRemoveDone,
                        Lid lid, std::shared_ptr<search::IDestructorCallback> moveDoneCtx);

    // Ack token early if visibility delay is nonzero
    void considerEarlyAck(FeedToken &token);

    void makeUpdatedDocument(SerialNum serialNum, Lid lid, DocumentUpdateSP upd, OnOperationDoneType onWriteDone,
                             PromisedDoc promisedDoc, PromisedStream promisedStream);

protected:
    virtual void internalDeleteBucket(const DeleteBucketOperation &delOp);
    virtual void heartBeatIndexedFields(SerialNum serialNum);
    virtual void heartBeatAttributes(SerialNum serialNum);

private:
    virtual void putAttributes(SerialNum serialNum, Lid lid, const Document &doc,
                               bool immediateCommit, OnPutDoneType onWriteDone);

    virtual void putIndexedFields(SerialNum serialNum, Lid lid, const DocumentSP &newDoc,
                                  bool immediateCommit, OnOperationDoneType onWriteDone);

    virtual void updateAttributes(SerialNum serialNum, Lid lid, const DocumentUpdate &upd,
                                  bool immediateCommit, OnOperationDoneType onWriteDone, IFieldUpdateCallback & onUpdate);

    virtual void updateAttributes(SerialNum serialNum, Lid lid, FutureDoc doc,
                                  bool immediateCommit, OnOperationDoneType onWriteDone);

    virtual void updateIndexedFields(SerialNum serialNum, Lid lid, FutureDoc doc,
                                     bool immediateCommit, OnOperationDoneType onWriteDone);

    virtual void removeAttributes(SerialNum serialNum, Lid lid, bool immediateCommit, OnRemoveDoneType onWriteDone);
    virtual void removeIndexedFields(SerialNum serialNum, Lid lid, bool immediateCommit, OnRemoveDoneType onWriteDone);

protected:
    virtual void removeAttributes(SerialNum serialNum, const LidVector &lidsToRemove,
                                  bool immediateCommit, OnWriteDoneType onWriteDone);

    virtual void removeIndexedFields(SerialNum serialNum, const LidVector &lidsToRemove,
                                     bool immediateCommit, OnWriteDoneType onWriteDone);

public:
    StoreOnlyFeedView(const Context &ctx, const PersistentParams &params);
    ~StoreOnlyFeedView() override;

    const ISummaryAdapter::SP &getSummaryAdapter() const { return _summaryAdapter; }
    const search::index::Schema::SP &getSchema() const { return _schema; }
    const PersistentParams &getPersistentParams() const { return _params; }
    const search::IDocumentStore &getDocumentStore() const { return _summaryAdapter->getDocumentStore(); }
    const IDocumentMetaStoreContext::SP &getDocumentMetaStore() const { return _documentMetaStoreContext; }
    searchcorespi::index::IThreadingService &getWriteService() { return _writeService; }
    documentmetastore::ILidReuseDelayer &getLidReuseDelayer() { return _lidReuseDelayer; }
    CommitTimeTracker &getCommitTimeTracker() { return _commitTimeTracker; }
    IGidToLidChangeHandler &getGidToLidChangeHandler() const { return _gidToLidChangeHandler; }

    const std::shared_ptr<const document::DocumentTypeRepo> &getDocumentTypeRepo() const override { return _repo; }
    const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const override;

    void preparePut(PutOperation &putOp) override;
    void handlePut(FeedToken token, const PutOperation &putOp) override;
    void prepareUpdate(UpdateOperation &updOp) override;
    void handleUpdate(FeedToken token, const UpdateOperation &updOp) override;
    void prepareRemove(RemoveOperation &rmOp) override;
    void handleRemove(FeedToken token, const RemoveOperation &rmOp) override;
    void prepareDeleteBucket(DeleteBucketOperation &delOp) override;
    void handleDeleteBucket(const DeleteBucketOperation &delOp) override;
    void prepareMove(MoveOperation &putOp) override;
    void handleMove(const MoveOperation &putOp, std::shared_ptr<search::IDestructorCallback> doneCtx) override;
    void heartBeat(search::SerialNum serialNum) override;
    void sync() override;
    void forceCommit(SerialNum serialNum) override;
    virtual void forceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone);

    /**
     * Prune lids present in operation.  Caller must call doneSegment()
     * on prune operation after this call.
     *
     * Called by writer thread.
     */
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp) override;
    void handleCompactLidSpace(const CompactLidSpaceOperation &op) override;
};

}
