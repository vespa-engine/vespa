// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/putdonecontext.h>
#include <vespa/searchcore/proton/server/removedonecontext.h>
#include <vespa/searchcore/proton/server/storeonlyfeedview.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/reference/dummy_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("storeonlyfeedview_test");

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::GlobalId;
using namespace proton;
using search::DocumentIdT;
using vespalib::IDestructorCallback;
using search::SerialNum;
using search::index::DocBuilder;
using search::index::Schema;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace {

class MySummaryAdapter : public test::MockSummaryAdapter {
private:
    int &_rmCount;
    int &_putCount;
    int &_heartbeatCount;

public:
    MySummaryAdapter(int &removeCount, int &putCount, int &heartbeatCount) noexcept
        : _rmCount(removeCount),
          _putCount(putCount),
          _heartbeatCount(heartbeatCount) {
    }
    void put(SerialNum, DocumentIdT, const Document &) override { ++ _putCount; }
    void put(SerialNum, DocumentIdT, const vespalib::nbostream &) override { ++ _putCount; }

    void remove(SerialNum, DocumentIdT) override { ++_rmCount; }
    void heartBeat(SerialNum) override { ++_heartbeatCount; }
};

std::shared_ptr<const DocumentTypeRepo> myGetDocumentTypeRepo() {
    Schema schema;
    DocBuilder builder(schema);
    std::shared_ptr<const DocumentTypeRepo> repo = builder.getDocumentTypeRepo();
    ASSERT_TRUE(repo.get());
    return repo;
}

struct MyMinimalFeedViewBase
{
    std::shared_ptr<IGidToLidChangeHandler> gidToLidChangeHandler;

    MyMinimalFeedViewBase()
        : gidToLidChangeHandler(std::make_shared<DummyGidToLidChangeHandler>())
    {
    }
};

struct MyMinimalFeedView : public MyMinimalFeedViewBase, public StoreOnlyFeedView {
    using UP = std::unique_ptr<MyMinimalFeedView>;

    int removeMultiAttributesCount;
    int removeMultiIndexFieldsCount;
    int heartBeatAttributesCount;
    int heartBeatIndexedFieldsCount;
    int &outstandingMoveOps;

    MyMinimalFeedView(const ISummaryAdapter::SP &summaryAdapter,
                      const DocumentMetaStore::SP &metaStore,
                      searchcorespi::index::IThreadingService &writeService,
                      const PersistentParams &params,
                      std::shared_ptr<PendingLidTrackerBase> pendingLidsForCommit,
                      int &outstandingMoveOps_) :
        MyMinimalFeedViewBase(),
        StoreOnlyFeedView(StoreOnlyFeedView::Context(summaryAdapter,
                                                     search::index::Schema::SP(),
                                                     std::make_shared<DocumentMetaStoreContext>(metaStore),
                                                     myGetDocumentTypeRepo(),
                                                     std::move(pendingLidsForCommit),
                                                     *gidToLidChangeHandler,
                                                     writeService),
                          params),
        removeMultiAttributesCount(0),
        removeMultiIndexFieldsCount(0),
        heartBeatAttributesCount(0),
        heartBeatIndexedFieldsCount(0),
        outstandingMoveOps(outstandingMoveOps_)
    {
    }
    void removeAttributes(SerialNum s, const LidVector &l, OnWriteDoneType onWriteDone) override {
        StoreOnlyFeedView::removeAttributes(s, l, onWriteDone);
        ++removeMultiAttributesCount;
    }
    void removeIndexedFields(SerialNum s, const LidVector &l, OnWriteDoneType onWriteDone) override {
        StoreOnlyFeedView::removeIndexedFields(s, l, onWriteDone);
        ++removeMultiIndexFieldsCount;
    }
    void heartBeatIndexedFields(SerialNum s) override {
        StoreOnlyFeedView::heartBeatIndexedFields(s);
        ++heartBeatIndexedFieldsCount;
    }
    void heartBeatAttributes(SerialNum s) override {
        StoreOnlyFeedView::heartBeatAttributes(s);
        ++heartBeatAttributesCount;
    }
};

struct MoveOperationFeedView : public MyMinimalFeedView {
    using UP = std::unique_ptr<MoveOperationFeedView>;

    int putAttributesCount;
    int putIndexFieldsCount;
    int removeAttributesCount;
    int removeIndexFieldsCount;
    std::vector<IDestructorCallback::SP> onWriteDoneContexts;
    MoveOperationFeedView(const ISummaryAdapter::SP &summaryAdapter,
                          const DocumentMetaStore::SP &metaStore,
                          searchcorespi::index::IThreadingService &writeService,
                          const PersistentParams &params,
                          std::shared_ptr<PendingLidTrackerBase> pendingLidsForCommit,
                          int &outstandingMoveOps_) :
            MyMinimalFeedView(summaryAdapter, metaStore, writeService,
                              params, std::move(pendingLidsForCommit), outstandingMoveOps_),
            putAttributesCount(0),
            putIndexFieldsCount(0),
            removeAttributesCount(0),
            removeIndexFieldsCount(0),
            onWriteDoneContexts()
    {}
    void putAttributes(SerialNum, search::DocumentIdT, const document::Document &, OnPutDoneType onWriteDone) override {
        ++putAttributesCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
     void putIndexedFields(SerialNum, search::DocumentIdT, const document::Document::SP &,
                           OnOperationDoneType onWriteDone) override {
        ++putIndexFieldsCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    void removeAttributes(SerialNum, search::DocumentIdT, OnRemoveDoneType onWriteDone) override {
        ++removeAttributesCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    void removeIndexedFields(SerialNum, search::DocumentIdT, OnRemoveDoneType onWriteDone) override {
        ++removeIndexFieldsCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    void clearWriteDoneContexts() { onWriteDoneContexts.clear(); }
};

struct MoveOperationCallback : public IDestructorCallback {
    int &outstandingMoveOps;
    explicit MoveOperationCallback(int &outstandingMoveOps_) noexcept : outstandingMoveOps(outstandingMoveOps_) {
        ++outstandingMoveOps;
    }
    ~MoveOperationCallback() override {
        ASSERT_GREATER(outstandingMoveOps, 0);
        --outstandingMoveOps;
    }
};

const uint32_t subdb_id = 0;

template <typename FeedViewType>
struct FixtureBase {
    int removeCount;
    int putCount;
    int heartbeatCount;
    int outstandingMoveOps;
    DocumentMetaStore::SP metaStore;
    vespalib::ThreadStackExecutor sharedExecutor;
    ExecutorThreadingService writeService;
    std::shared_ptr<PendingLidTrackerBase> pendingLidsForCommit;
    typename FeedViewType::UP feedview;
    SerialNum serial_num;
 
    explicit FixtureBase(SubDbType subDbType = SubDbType::READY)
        : removeCount(0),
          putCount(0),
          heartbeatCount(0),
          outstandingMoveOps(0),
          metaStore(std::make_shared<DocumentMetaStore>(std::make_shared<bucketdb::BucketDBOwner>(),
                                                        DocumentMetaStore::getFixedName(),
                                                        search::GrowStrategy(),
                                                        subDbType)),
          sharedExecutor(1, 0x10000),
          writeService(sharedExecutor),
          pendingLidsForCommit(std::make_shared<PendingLidTracker>()),
          feedview(),
          serial_num(2u)
    {
        StoreOnlyFeedView::PersistentParams params(0, 0, DocTypeName("foo"), subdb_id, subDbType);
        metaStore->constructFreeList();
        ISummaryAdapter::SP adapter = std::make_shared<MySummaryAdapter>(removeCount, putCount, heartbeatCount);
        feedview = std::make_unique<FeedViewType>(adapter, metaStore, writeService,
                                                  params, pendingLidsForCommit, outstandingMoveOps);
    }

    ~FixtureBase() {
        this->force_commit();
    }

    void addSingleDocToMetaStore(uint32_t expected_lid) {
        using Result = DocumentMetaStore::Result;
        DocumentId id(make_string("id:test:foo:g=foo:%d", expected_lid));
        Result inspect = metaStore->inspect(id.getGlobalId(), 0u);
        uint32_t docSize = 1;
        EXPECT_EQUAL(expected_lid,
                     metaStore->put(id.getGlobalId(),
                                     id.getGlobalId().convertToBucketId(),
                                     Timestamp(10), docSize, inspect.getLid(), 0u).getLid());
    }

    void addDocsToMetaStore(int count) {
        for (int i = 1; i <= count; ++i) {
            addSingleDocToMetaStore(i);
            EXPECT_TRUE(metaStore->validLid(i));
        }
    }

    template <typename FunctionType>
    void runInMaster(FunctionType func) {
        test::runInMaster(writeService, func);
    }

    void force_commit() {
        runInMaster([this] () { static_cast<IFeedView&>(*feedview).forceCommit(serial_num); });
        writeService.sync();
    }
};

using Fixture = FixtureBase<MyMinimalFeedView>;

struct MoveFixture : public FixtureBase<MoveOperationFeedView> {

    IDestructorCallback::SP beginMoveOp() {
        return std::make_shared<MoveOperationCallback>(outstandingMoveOps);
    }

    void assertPutCount(int expCnt) {
        EXPECT_EQUAL(expCnt, putCount);
        EXPECT_EQUAL(expCnt, feedview->putAttributesCount);
        EXPECT_EQUAL(expCnt, feedview->putIndexFieldsCount);
    }

    void assertRemoveCount(int expCnt) {
        EXPECT_EQUAL(expCnt, removeCount);
        EXPECT_EQUAL(expCnt, feedview->removeAttributesCount);
        EXPECT_EQUAL(expCnt, feedview->removeIndexFieldsCount);
    }

    void assertAndClearMoveOp() {
        EXPECT_EQUAL(1, outstandingMoveOps);
        feedview->clearWriteDoneContexts();
        EXPECT_EQUAL(0, outstandingMoveOps);
    }
};

TEST_F("require that prepareMove sets target db document id", Fixture)
{
    Document::SP doc(new Document);
    MoveOperation op(BucketId(20, 42), Timestamp(10), doc, 1, subdb_id + 1);
    f.runInMaster([&] () { f.feedview->prepareMove(op); });

    DbDocumentId targetId = op.getDbDocumentId();
    EXPECT_EQUAL(subdb_id, targetId.getSubDbId());
    EXPECT_EQUAL(1u, targetId.getLid());
}

MoveOperation::UP
makeMoveOp(Document::SP doc, DbDocumentId sourceDbdId, uint32_t targetSubDbId)
{
    MoveOperation::UP result = std::make_unique<MoveOperation>(doc->getId().getGlobalId().convertToBucketId(),
                                                               Timestamp(10), doc, sourceDbdId, targetSubDbId);
    result->setSerialNum(1);
    return result;
}

MoveOperation::UP
makeMoveOp(DbDocumentId sourceDbdId, uint32_t targetSubDbId)
{
    return makeMoveOp(std::make_shared<Document>(), sourceDbdId, targetSubDbId);
}

TEST_F("require that handleMove() adds document to target and removes it from source and propagates destructor callback", MoveFixture)
{
    uint32_t lid = 0;
    { // move from (subdb_id + 1) -> this (subdb_id)
        MoveOperation::UP op = makeMoveOp(DbDocumentId(subdb_id + 1, 1), subdb_id);
        TEST_DO(f.assertPutCount(0));
        f.runInMaster([&]() { f.feedview->prepareMove(*op); });
        f.runInMaster([&]() { f.feedview->handleMove(*op, f.beginMoveOp()); });
        TEST_DO(f.assertPutCount(1));
        TEST_DO(f.assertAndClearMoveOp());
        lid = op->getDbDocumentId().getLid();
        EXPECT_EQUAL(1u, lid);
        EXPECT_TRUE(f.metaStore->validLid(lid));
    }

    { // move from this (subdb_id) -> (subdb_id + 1)
        MoveOperation::UP op = makeMoveOp(DbDocumentId(subdb_id, 1), subdb_id + 1);
        op->setDbDocumentId(DbDocumentId(subdb_id + 1, 1));
        TEST_DO(f.assertRemoveCount(0));
        f.runInMaster([&]() { f.feedview->handleMove(*op, f.beginMoveOp()); });
        EXPECT_FALSE(f.metaStore->validLid(lid));
        TEST_DO(f.assertRemoveCount(1));
        TEST_DO(f.assertAndClearMoveOp());
    }
}

TEST_F("require that handleMove() handles move within same subdb and propagates destructor callback", MoveFixture)
{
    Document::SP doc(new Document);
    DocumentId doc1id("id:test:foo:g=foo:1");
    uint32_t docSize = 1;
    f.runInMaster([&] () { f.metaStore->put(doc1id.getGlobalId(),
                      doc1id.getGlobalId().convertToBucketId(),
                      Timestamp(9), docSize, 1, 0u); });
    f.runInMaster([&] () { f.metaStore->put(doc->getId().getGlobalId(),
                      doc->getId().getGlobalId().convertToBucketId(),
                      Timestamp(10), docSize, 2, 0u); });
    f.runInMaster([&] () { f.metaStore->remove(1, 0u); });
    f.metaStore->removeComplete(1);
    MoveOperation::UP op = makeMoveOp(doc, DbDocumentId(subdb_id, 2), subdb_id);
    op->setTargetLid(1);
    TEST_DO(f.assertPutCount(0));
    TEST_DO(f.assertRemoveCount(0));
    f.runInMaster([&] () { f.feedview->handleMove(*op, f.beginMoveOp()); });
    TEST_DO(f.assertPutCount(1));
    TEST_DO(f.assertRemoveCount(1));
    TEST_DO(f.assertAndClearMoveOp());
    uint32_t lid = op->getDbDocumentId().getLid();
    EXPECT_EQUAL(1u, lid);
    EXPECT_TRUE(f.metaStore->validLid(lid));
}

TEST_F("require that prune removed documents removes documents",
       Fixture(SubDbType::REMOVED))
{
    f.addDocsToMetaStore(3);

    LidVectorContext::SP lids(new LidVectorContext(4));
    lids->addLid(1);
    lids->addLid(3);
    PruneRemovedDocumentsOperation op(lids->getDocIdLimit(), subdb_id);
    op.setLidsToRemove(lids);
    op.setSerialNum(1);  // allows use of meta store.
    f.runInMaster([&] () { f.feedview->handlePruneRemovedDocuments(op); });

    EXPECT_EQUAL(2, f.removeCount);
    EXPECT_FALSE(f.metaStore->validLid(1));
    EXPECT_TRUE(f.metaStore->validLid(2));
    EXPECT_FALSE(f.metaStore->validLid(3));
    EXPECT_EQUAL(0, f.feedview->removeMultiAttributesCount);
    EXPECT_EQUAL(0, f.feedview->removeMultiIndexFieldsCount);
}

TEST_F("require that heartbeat propagates and commits meta store", Fixture)
{
    EXPECT_EQUAL(0u, f.metaStore->getStatus().getLastSyncToken());
    EXPECT_EQUAL(0, f.feedview->heartBeatIndexedFieldsCount);
    EXPECT_EQUAL(0, f.feedview->heartBeatAttributesCount);
    EXPECT_EQUAL(0, f.heartbeatCount);
    f.runInMaster([&] () { f.feedview->heartBeat(2); });
    EXPECT_EQUAL(2u, f.metaStore->getStatus().getLastSyncToken());
    EXPECT_EQUAL(1, f.feedview->heartBeatIndexedFieldsCount);
    EXPECT_EQUAL(1, f.feedview->heartBeatAttributesCount);
    EXPECT_EQUAL(1, f.heartbeatCount);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
