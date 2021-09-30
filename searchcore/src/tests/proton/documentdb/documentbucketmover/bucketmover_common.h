// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_create_notifier.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/server/i_move_operation_limiter.h>
#include <vespa/searchcore/proton/server/idocumentmovehandler.h>
#include <vespa/searchcore/proton/server/imaintenancejobrunner.h>
#include <vespa/searchcore/proton/server/maintenancedocumentsubdb.h>
#include <vespa/searchcore/proton/server/ibucketmodifiedhandler.h>
#include <vespa/searchcore/proton/server/i_maintenance_job.h>
#include <vespa/searchcore/proton/test/buckethandler.h>
#include <vespa/searchcore/proton/test/clusterstatehandler.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/document/test/make_bucket_space.h>

namespace proton {
    class DocumentMetaStore;
}
namespace proton::move::test {

struct MyMoveOperationLimiter : public IMoveOperationLimiter {
    uint32_t beginOpCount;

    MyMoveOperationLimiter() : beginOpCount(0) {}

    vespalib::IDestructorCallback::SP beginOperation() override {
        ++beginOpCount;
        return {};
    }
    size_t numPending() const override { return beginOpCount; }
};

struct MyMoveHandler : public IDocumentMoveHandler {
    using MoveOperationVector = std::vector<MoveOperation>;
    bucketdb::BucketDBOwner &_bucketDb;
    MoveOperationVector _moves;
    size_t _numCachedBuckets;
    bool _storeMoveDoneContexts;
    std::vector<vespalib::IDestructorCallback::SP> _moveDoneContexts;

    MyMoveHandler(bucketdb::BucketDBOwner &bucketDb, bool storeMoveDoneContext = false);
    ~MyMoveHandler() override;
    void handleMove(MoveOperation &op, vespalib::IDestructorCallback::SP moveDoneCtx) override;

    void reset() {
        _moves.clear();
        _numCachedBuckets = 0;
    }

    void clearMoveDoneContexts() {
        _moveDoneContexts.clear();
    }
};

struct MyDocumentRetriever : public DocumentRetrieverBaseForTest {
    using DocumentTypeRepo = document::DocumentTypeRepo;
    using DocumentMetaData = search::DocumentMetaData;
    using Document = document::Document;
    using DocumentId = document::DocumentId;
    using DocumentIdT = search::DocumentIdT;
    using DocumentVector = std::vector<Document::SP>;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    DocumentVector _docs;
    uint32_t _lid2Fail;

    MyDocumentRetriever(std::shared_ptr<const DocumentTypeRepo> repo)
        : _repo(std::move(repo)),
          _docs(),
          _lid2Fail(0)
    {
        _docs.push_back(Document::UP()); // lid 0 invalid
    }

    const DocumentTypeRepo &getDocumentTypeRepo() const override { return *_repo; }

    void getBucketMetaData(const storage::spi::Bucket &, DocumentMetaData::Vector &) const override {}

    DocumentMetaData getDocumentMetaData(const DocumentId &) const override { return DocumentMetaData(); }

    Document::UP getFullDocument(DocumentIdT lid) const override {
        return (lid != _lid2Fail) ? Document::UP(_docs[lid]->clone()) : Document::UP();
    }

    void failRetrieveForLid(uint32_t lid) { _lid2Fail = lid; }

    CachedSelect::SP parseSelect(const vespalib::string &) const override {
        return {};
    }
};

struct MyBucketModifiedHandler : public IBucketModifiedHandler {
    using BucketId = document::BucketId;
    std::vector<BucketId> _modified;

    ~MyBucketModifiedHandler() override;
    void notifyBucketModified(const BucketId &bucket) override;

    void reset() { _modified.clear(); }
};

struct MySubDb {
    using BucketId = document::BucketId;
    using Document = document::Document;
    using DocumentTypeRepo = document::DocumentTypeRepo;
    using DocumentVector = proton::test::DocumentVector;
    using UserDocuments = proton::test::UserDocuments;
    std::shared_ptr<DocumentMetaStore>    _metaStoreSP;
    DocumentMetaStore                    &_metaStore;
    std::shared_ptr<MyDocumentRetriever>  _realRetriever;
    std::shared_ptr<IDocumentRetriever>   _retriever;
    MaintenanceDocumentSubDB              _subDb;
    UserDocuments                         _docs;
    bucketdb::BucketDBHandler             _bucketDBHandler;

    MySubDb(const std::shared_ptr<const DocumentTypeRepo> &repo, std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
            uint32_t subDbId, SubDbType subDbType);

    ~MySubDb();

    void insertDocs(const UserDocuments &docs_);

    void failRetrieveForLid(uint32_t lid) {
        _realRetriever->failRetrieveForLid(lid);
    }

    BucketId bucket(uint32_t userId) const {
        return _docs.getBucket(userId);
    }

    DocumentVector docs(uint32_t userId) {
        return _docs.getGidOrderDocs(userId);
    }

    void setBucketState(const BucketId &bucketId, bool active);
};

struct MyCountJobRunner : public IMaintenanceJobRunner {
    uint32_t runCount;
    explicit MyCountJobRunner(IMaintenanceJob &job) : runCount(0) {
        job.registerRunner(this);
    }
    void run() override { ++runCount; }
};

bool
assertEqual(const document::BucketId &bucket, const proton::test::Document &doc,
            uint32_t sourceSubDbId, uint32_t targetSubDbId, const MoveOperation &op);

}
