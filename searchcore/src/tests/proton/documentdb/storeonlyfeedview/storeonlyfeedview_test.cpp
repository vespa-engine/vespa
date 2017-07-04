// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/common/commit_time_tracker.h>
#include <vespa/searchcore/proton/documentmetastore/lidreusedelayer.h>
#include <vespa/searchcore/proton/metrics/feed_metrics.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/putdonecontext.h>
#include <vespa/searchcore/proton/server/removedonecontext.h>
#include <vespa/searchcore/proton/server/storeonlyfeedview.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/common/serialnum.h>
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
using search::DocumentIdT;
using search::IDestructorCallback;
using search::SerialNum;
using search::index::DocBuilder;
using search::index::Schema;
using storage::spi::Timestamp;
using vespalib::make_string;
using namespace proton;

namespace {

class MySummaryAdapter : public test::MockSummaryAdapter {
    int &_rm_count;
    int &_put_count;
    int &_heartbeat_count;

public:
    MySummaryAdapter(int &remove_count, int &put_count, int &heartbeat_count)
        : _rm_count(remove_count),
          _put_count(put_count),
          _heartbeat_count(heartbeat_count) {
    }
    virtual void put(SerialNum, const Document &, DocumentIdT) override { ++ _put_count; }
    virtual void remove(SerialNum, DocumentIdT) override { ++_rm_count; }
    virtual void heartBeat(SerialNum) override { ++_heartbeat_count; }
};

DocumentTypeRepo::SP myGetDocumentTypeRepo() {
    Schema schema;
    DocBuilder builder(schema);
    DocumentTypeRepo::SP repo = builder.getDocumentTypeRepo();
    ASSERT_TRUE(repo.get());
    return repo;
}

struct MyMinimalFeedView : public StoreOnlyFeedView {
    typedef std::unique_ptr<MyMinimalFeedView> UP;

    int removeMultiAttributesCount;
    int removeMultiIndexFieldsCount;
    int heartBeatAttributes_count;
    int heartBeatIndexedFields_count;
    int &outstandingMoveOps;

    MyMinimalFeedView(const ISummaryAdapter::SP &summary_adapter,
                      const DocumentMetaStore::SP &meta_store,
                      searchcorespi::index::IThreadingService &writeService,
                      documentmetastore::ILidReuseDelayer &lidReuseDelayer,
                      CommitTimeTracker &commitTimeTracker,
                      const PersistentParams &params,
                      int &outstandingMoveOps_) :
        StoreOnlyFeedView(StoreOnlyFeedView::Context(summary_adapter,
                        search::index::Schema::SP(),
                        DocumentMetaStoreContext::SP(
                                new DocumentMetaStoreContext(meta_store)),
                        myGetDocumentTypeRepo(),
                        writeService,
                        lidReuseDelayer,
                        commitTimeTracker),
                        params),
        removeMultiAttributesCount(0),
        removeMultiIndexFieldsCount(0),
        heartBeatAttributes_count(0),
        heartBeatIndexedFields_count(0),
        outstandingMoveOps(outstandingMoveOps_)
    {
    }
    virtual void removeAttributes(SerialNum s, const LidVector &l,
                                  bool immediateCommit, OnWriteDoneType onWriteDone) override {
        StoreOnlyFeedView::removeAttributes(s, l, immediateCommit, onWriteDone);
        ++removeMultiAttributesCount;
    }
    virtual void removeIndexedFields(SerialNum s, const LidVector &l,
                                     bool immediateCommit,
                                     OnWriteDoneType onWriteDone) override {
        StoreOnlyFeedView::removeIndexedFields(s, l,
                                               immediateCommit, onWriteDone);
        ++removeMultiIndexFieldsCount;
    }
    virtual void heartBeatIndexedFields(SerialNum s) override {
        StoreOnlyFeedView::heartBeatIndexedFields(s);
        ++heartBeatIndexedFields_count;
    }
    virtual void heartBeatAttributes(SerialNum s) override {
        StoreOnlyFeedView::heartBeatAttributes(s);
        ++heartBeatAttributes_count;
    }
};

struct MoveOperationFeedView : public MyMinimalFeedView {
    typedef std::unique_ptr<MoveOperationFeedView> UP;

    int putAttributesCount;
    int putIndexFieldsCount;
    int removeAttributesCount;
    int removeIndexFieldsCount;
    std::vector<IDestructorCallback::SP> onWriteDoneContexts;
    MoveOperationFeedView(const ISummaryAdapter::SP &summary_adapter,
                          const DocumentMetaStore::SP &meta_store,
                          searchcorespi::index::IThreadingService &writeService,
                          documentmetastore::ILidReuseDelayer &lidReuseDelayer,
                          CommitTimeTracker &commitTimeTracker,
                          const PersistentParams &params,
                          int &outstandingMoveOps_) :
            MyMinimalFeedView(summary_adapter, meta_store, writeService, lidReuseDelayer,
                              commitTimeTracker, params, outstandingMoveOps_),
            putAttributesCount(0),
            putIndexFieldsCount(0),
            removeAttributesCount(0),
            removeIndexFieldsCount(0),
            onWriteDoneContexts()
    {}
    virtual void putAttributes(SerialNum, search::DocumentIdT, const document::Document &,
                               bool, OnPutDoneType onWriteDone) override {
        ++putAttributesCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    virtual void putIndexedFields(SerialNum, search::DocumentIdT, const document::Document::SP &,
                                  bool, OnOperationDoneType onWriteDone) override {
        ++putIndexFieldsCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    virtual void removeAttributes(SerialNum, search::DocumentIdT,
                                  bool, OnRemoveDoneType onWriteDone) override {
        ++removeAttributesCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    virtual void removeIndexedFields(SerialNum, search::DocumentIdT,
                                     bool, OnRemoveDoneType onWriteDone) override {
        ++removeIndexFieldsCount;
        EXPECT_EQUAL(1, outstandingMoveOps);
        onWriteDoneContexts.push_back(onWriteDone);
    }
    void clearWriteDoneContexts() { onWriteDoneContexts.clear(); }
};

struct MoveOperationCallback : public IDestructorCallback {
    int &outstandingMoveOps;
    MoveOperationCallback(int &outstandingMoveOps_) : outstandingMoveOps(outstandingMoveOps_) {
        ++outstandingMoveOps;
    }
    virtual ~MoveOperationCallback() {
        ASSERT_GREATER(outstandingMoveOps, 0);
        --outstandingMoveOps;
    }
};

const uint32_t subdb_id = 0;

template <typename FeedViewType>
struct FixtureBase {
    int remove_count;
    int put_count;
    int heartbeat_count;
    int outstandingMoveOps;
    DocumentMetaStore::SP meta_store;
    ExecutorThreadingService writeService;
    documentmetastore::LidReuseDelayer _lidReuseDelayer;
    CommitTimeTracker _commitTimeTracker;
    typename FeedViewType::UP feedview;
 
    FixtureBase(SubDbType subDbType = SubDbType::READY)
        : remove_count(0),
          put_count(0),
          heartbeat_count(0),
          outstandingMoveOps(0),
          meta_store(new DocumentMetaStore(std::make_shared<BucketDBOwner>(),
                                           DocumentMetaStore::getFixedName(),
                                           search::GrowStrategy(),
                                           DocumentMetaStore::IGidCompare::SP(
                                                   new DocumentMetaStore::
                                                   DefaultGidCompare),
                                           subDbType)),
          writeService(),
          _lidReuseDelayer(writeService, *meta_store),
          _commitTimeTracker(fastos::TimeStamp()),
          feedview()
    {
        PerDocTypeFeedMetrics metrics(0);
        StoreOnlyFeedView::PersistentParams params(0, 0, DocTypeName("foo"), metrics, subdb_id, subDbType);
        meta_store->constructFreeList();
        ISummaryAdapter::SP adapter = std::make_unique<MySummaryAdapter>(remove_count, put_count, heartbeat_count);
        feedview = std::make_unique<FeedViewType>(adapter, meta_store, writeService, _lidReuseDelayer,
                                                  _commitTimeTracker, params, outstandingMoveOps);
    }

    ~FixtureBase() {
        writeService.sync();
    }

    void addSingleDocToMetaStore(uint32_t expected_lid) {
        typedef DocumentMetaStore::Result Result;
        DocumentId id(make_string("groupdoc:test:foo:%d", expected_lid));
        Result inspect = meta_store->inspect(id.getGlobalId());
        uint32_t docSize = 1;
        EXPECT_EQUAL(expected_lid,
                     meta_store->put(id.getGlobalId(),
                                     id.getGlobalId().convertToBucketId(),
                                     Timestamp(10), docSize, inspect.getLid()).getLid());
    }

    void addDocsToMetaStore(int count) {
        for (int i = 1; i <= count; ++i) {
            addSingleDocToMetaStore(i);
            EXPECT_TRUE(meta_store->validLid(i));
        }
    }

    template <typename FunctionType>
    void runInMaster(FunctionType func) {
        test::runInMaster(writeService, func);
    }

};

using Fixture = FixtureBase<MyMinimalFeedView>;

struct MoveFixture : public FixtureBase<MoveOperationFeedView> {

    IDestructorCallback::SP beginMoveOp() {
        return std::make_shared<MoveOperationCallback>(outstandingMoveOps);
    }

    void assertPutCount(int expCnt) {
        EXPECT_EQUAL(expCnt, put_count);
        EXPECT_EQUAL(expCnt, feedview->putAttributesCount);
        EXPECT_EQUAL(expCnt, feedview->putIndexFieldsCount);
    }

    void assertRemoveCount(int expCnt) {
        EXPECT_EQUAL(expCnt, remove_count);
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

    DbDocumentId target_id = op.getDbDocumentId();
    EXPECT_EQUAL(subdb_id, target_id.getSubDbId());
    EXPECT_EQUAL(1u, target_id.getLid());
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
        EXPECT_TRUE(f.meta_store->validLid(lid));
    }

    { // move from this (subdb_id) -> (subdb_id + 1)
        MoveOperation::UP op = makeMoveOp(DbDocumentId(subdb_id, 1), subdb_id + 1);
        op->setDbDocumentId(DbDocumentId(subdb_id + 1, 1));
        TEST_DO(f.assertRemoveCount(0));
        f.runInMaster([&]() { f.feedview->handleMove(*op, f.beginMoveOp()); });
        EXPECT_FALSE(f.meta_store->validLid(lid));
        TEST_DO(f.assertRemoveCount(1));
        TEST_DO(f.assertAndClearMoveOp());
    }
}

TEST_F("require that handleMove() handles move within same subdb and propagates destructor callback", MoveFixture)
{
    Document::SP doc(new Document);
    DocumentId doc1id("groupdoc:test:foo:1");
    uint32_t docSize = 1;
    f.runInMaster([&] () { f.meta_store->put(doc1id.getGlobalId(),
                      doc1id.getGlobalId().convertToBucketId(),
                      Timestamp(9), docSize, 1); });
    f.runInMaster([&] () { f.meta_store->put(doc->getId().getGlobalId(),
                      doc->getId().getGlobalId().convertToBucketId(),
                      Timestamp(10), docSize, 2); });
    f.runInMaster([&] () { f.meta_store->remove(1); });
    f.meta_store->removeComplete(1);
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
    EXPECT_TRUE(f.meta_store->validLid(lid));
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

    EXPECT_EQUAL(2, f.remove_count);
    EXPECT_FALSE(f.meta_store->validLid(1));
    EXPECT_TRUE(f.meta_store->validLid(2));
    EXPECT_FALSE(f.meta_store->validLid(3));
    EXPECT_EQUAL(0, f.feedview->removeMultiAttributesCount);
    EXPECT_EQUAL(0, f.feedview->removeMultiIndexFieldsCount);
}

TEST_F("require that heartbeat propagates and commits meta store", Fixture)
{
    EXPECT_EQUAL(0u, f.meta_store->getStatus().getLastSyncToken());
    EXPECT_EQUAL(0, f.feedview->heartBeatIndexedFields_count);
    EXPECT_EQUAL(0, f.feedview->heartBeatAttributes_count);
    EXPECT_EQUAL(0, f.heartbeat_count);
    f.runInMaster([&] () { f.feedview->heartBeat(2); });
    EXPECT_EQUAL(2u, f.meta_store->getStatus().getLastSyncToken());
    EXPECT_EQUAL(1, f.feedview->heartBeatIndexedFields_count);
    EXPECT_EQUAL(1, f.feedview->heartBeatAttributes_count);
    EXPECT_EQUAL(1, f.heartbeat_count);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
