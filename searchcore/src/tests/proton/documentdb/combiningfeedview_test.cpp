// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/server/combiningfeedview.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/idestructorcallback.h>

using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::test::makeBucketSpace;
using vespalib::IDestructorCallback;
using search::SerialNum;
using storage::spi::Timestamp;
using namespace proton;

using FeedViewVector = std::vector<IFeedView::SP>;

namespace {
struct MyFeedView : public test::DummyFeedView
{
    using SP = std::shared_ptr<MyFeedView>;
    DocumentMetaStore    _metaStore;
    uint32_t             _preparePut;
    uint32_t             _handlePut;
    uint32_t             _prepareRemove;
    uint32_t             _handleRemove;
    uint32_t             _prepareUpdate;
    uint32_t             _handleUpdate;
    uint32_t             _prepareMove;
    uint32_t             _handleMove;
    uint32_t             _prepareDeleteBucket;
    uint32_t             _handleDeleteBucket;
    uint32_t             _heartBeat;
    uint32_t             _handlePrune;
    uint32_t             _wantedLidLimit;
    MyFeedView(const std::shared_ptr<const DocumentTypeRepo> &repo,
               std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
               SubDbType subDbType) :
        test::DummyFeedView(repo),
        _metaStore(bucketDB,
                   DocumentMetaStore::getFixedName(),
                   search::GrowStrategy(),
                   subDbType),
        _preparePut(0),
        _handlePut(0),
        _prepareRemove(0),
        _handleRemove(0),
        _prepareUpdate(0),
        _handleUpdate(0),
        _prepareMove(0),
        _handleMove(0),
        _prepareDeleteBucket(0),
        _handleDeleteBucket(0),
        _heartBeat(0),
        _handlePrune(0),
        _wantedLidLimit(0)
    {
        _metaStore.constructFreeList();
    }

    const DocumentMetaStore *getDocumentMetaStorePtr() const override { return &_metaStore; }
    void preparePut(PutOperation &) override { ++_preparePut; }
    void handlePut(FeedToken, const PutOperation &) override { ++_handlePut; }
    void prepareUpdate(UpdateOperation &) override { ++_prepareUpdate; }
    void handleUpdate(FeedToken, const UpdateOperation &) override { ++_handleUpdate; }
    void prepareRemove(RemoveOperation &) override { ++_prepareRemove; }
    void handleRemove(FeedToken, const RemoveOperation &) override { ++_handleRemove; }
    void prepareDeleteBucket(DeleteBucketOperation &) override { ++_prepareDeleteBucket; }
    void handleDeleteBucket(const DeleteBucketOperation &, DoneCallback) override { ++_handleDeleteBucket; }
    void prepareMove(MoveOperation &) override { ++_prepareMove; }
    void handleMove(const MoveOperation &, DoneCallback) override { ++_handleMove; }
    void heartBeat(SerialNum, DoneCallback) override { ++_heartBeat; }
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &, DoneCallback) override { ++_handlePrune; }
    void handleCompactLidSpace(const CompactLidSpaceOperation &op, DoneCallback) override {
        _wantedLidLimit = op.getLidLimit();
    }
};


struct MySubDb
{
    MyFeedView::SP _view;
    MySubDb(const std::shared_ptr<const DocumentTypeRepo> &repo,
            std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
            SubDbType subDbType)
        : _view(std::make_shared<MyFeedView>(repo, std::move(bucketDB), subDbType))
    {
    }
    void insertDocs(const test::BucketDocuments &docs) {
        for (size_t i = 0; i < docs.getDocs().size(); ++i) {
            const test::Document &testDoc = docs.getDocs()[i];
            _view->_metaStore.put(testDoc.getGid(), testDoc.getBucket(),
                                  testDoc.getTimestamp(), testDoc.getDocSize(), testDoc.getLid(), 0u);
        }
    }
};


FeedViewVector
getVector(const MySubDb &ready,
          const MySubDb &removed,
          const MySubDb &notReady)
{
    FeedViewVector retval;
    retval.push_back(ready._view);
    retval.push_back(removed._view);
    retval.push_back(notReady._view);
    return retval;
}

const uint32_t READY = 0;
const uint32_t REMOVED = 1;
const uint32_t NOT_READY = 2;

struct CombiningFeedViewTest : public ::testing::Test
{
protected:
    test::UserDocumentsBuilder      _builder;
    std::shared_ptr<bucketdb::BucketDBOwner>  _bucketDB;
    MySubDb                         _ready;
    MySubDb                         _removed;
    MySubDb                         _notReady;
    test::BucketStateCalculator::SP _calc;
    CombiningFeedView               _view;
    CombiningFeedViewTest()  __attribute__((noinline));
    ~CombiningFeedViewTest() override __attribute__((noinline));
    const test::UserDocuments &userDocs() const { return _builder.getDocs(); }
    const test::BucketDocuments &userDocs(uint32_t userId) const { return userDocs().getUserDocs(userId); }
    PutOperation put(uint32_t userId) {
        const test::Document &doc = userDocs().getDocs(userId)[0];
        return PutOperation(doc.getBucket(), doc.getTimestamp(), doc.getDoc());
    }
    RemoveOperationWithDocId remove(uint32_t userId) {
        const test::Document &doc = userDocs().getDocs(userId)[0];
        return RemoveOperationWithDocId(doc.getBucket(), doc.getTimestamp(), doc.getDoc()->getId());
    }
    UpdateOperation update(uint32_t userId) {
        const test::Document &doc = userDocs().getDocs(userId)[0];
        return UpdateOperation(doc.getBucket(), doc.getTimestamp(), DocumentUpdate::SP());
    }
    MoveOperation move(uint32_t userId, DbDocumentId sourceDbdId, DbDocumentId targetDbdId) {
        const test::Document &doc = userDocs().getDocs(userId)[0];
        MoveOperation retval(doc.getBucket(), doc.getTimestamp(), doc.getDoc(),
                             sourceDbdId, targetDbdId.getSubDbId());
        retval.setTargetLid(targetDbdId.getLid());
        return retval;
    }
};

CombiningFeedViewTest::CombiningFeedViewTest()
    : _builder(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _ready(_builder.getRepo(), _bucketDB, SubDbType::READY),
      _removed(_builder.getRepo(), _bucketDB, SubDbType::REMOVED),
      _notReady(_builder.getRepo(), _bucketDB, SubDbType::NOTREADY),
      _calc(new test::BucketStateCalculator()),
      _view(getVector(_ready, _removed, _notReady), makeBucketSpace(), _calc)
{
    _builder.createDoc(1, 1);
    _builder.createDoc(2, 2);
}
CombiningFeedViewTest::~CombiningFeedViewTest() = default;

}


TEST_F(CombiningFeedViewTest, require_that_preparePut_sends_to_ready_view)
{
    PutOperation op = put(1);
    _calc->addReady(userDocs().getBucket(1));
    _view.preparePut(op);
    EXPECT_EQ(1u, _ready._view->_preparePut);
    EXPECT_EQ(0u, _removed._view->_preparePut);
    EXPECT_EQ(0u, _notReady._view->_preparePut);
    EXPECT_FALSE(op.getValidPrevDbdId());
}


TEST_F(CombiningFeedViewTest, require_that_preparePut_sends_to_not_ready_view)
{
    PutOperation op = put(1);
    _view.preparePut(op);
    EXPECT_EQ(0u, _ready._view->_preparePut);
    EXPECT_EQ(0u, _removed._view->_preparePut);
    EXPECT_EQ(1u, _notReady._view->_preparePut);
    EXPECT_FALSE(op.getValidPrevDbdId());
}


TEST_F(CombiningFeedViewTest, require_that_preparePut_can_fill_previous_dbdId)
{
    // insert bucket 1 in removed view
    _removed.insertDocs(userDocs(1));
    PutOperation op = put(1);
    _view.preparePut(op);
    EXPECT_EQ(1u, op.getPrevLid());
    EXPECT_EQ(REMOVED, op.getPrevSubDbId());
    EXPECT_EQ(Timestamp(1), op.getPrevTimestamp());
    EXPECT_TRUE(op.getPrevMarkedAsRemoved());
}


TEST_F(CombiningFeedViewTest, require_that_handlePut_sends_to_1_feed_view)
{
    PutOperation op = put(2);
    op.setDbDocumentId(DbDocumentId(READY, 2));
    _view.handlePut(FeedToken(), op);
    EXPECT_EQ(1u, _ready._view->_handlePut);
    EXPECT_EQ(0u, _removed._view->_handlePut);
    EXPECT_EQ(0u, _notReady._view->_handlePut);
}


TEST_F(CombiningFeedViewTest, require_that_handlePut_sends_to_2_feed_views)
{
    PutOperation op = put(2);
    op.setDbDocumentId(DbDocumentId(NOT_READY, 2));
    op.setPrevDbDocumentId(DbDocumentId(REMOVED, 2));
    _view.handlePut(FeedToken(), op);
    EXPECT_EQ(0u, _ready._view->_handlePut);
    EXPECT_EQ(1u, _removed._view->_handlePut);
    EXPECT_EQ(1u, _notReady._view->_handlePut);
}


TEST_F(CombiningFeedViewTest, require_that_prepareRemove_sends_to_removed_view)
{
    RemoveOperationWithDocId op = remove(1);
    _view.prepareRemove(op);
    EXPECT_EQ(0u, _ready._view->_prepareRemove);
    EXPECT_EQ(1u, _removed._view->_prepareRemove);
    EXPECT_EQ(0u, _notReady._view->_prepareRemove);
    EXPECT_FALSE(op.getValidPrevDbdId());
}


TEST_F(CombiningFeedViewTest, require_that_prepareRemove_can_fill_previous_dbdId)
{
    _ready.insertDocs(userDocs(1));
    RemoveOperationWithDocId op = remove(1);
    _view.prepareRemove(op);
    EXPECT_EQ(1u, op.getPrevLid());
    EXPECT_EQ(READY, op.getPrevSubDbId());
    EXPECT_EQ(Timestamp(1), op.getPrevTimestamp());
    EXPECT_FALSE(op.getPrevMarkedAsRemoved());
}


TEST_F(CombiningFeedViewTest, require_that_handleRemove_sends_op_with_valid_dbdId_to_1_feed_view)
{
    RemoveOperationWithDocId op = remove(1);
    op.setDbDocumentId(DbDocumentId(REMOVED, 1));
    _view.handleRemove(FeedToken(), op);
    EXPECT_EQ(0u, _ready._view->_handleRemove);
    EXPECT_EQ(1u, _removed._view->_handleRemove);
    EXPECT_EQ(0u, _notReady._view->_handleRemove);
}


TEST_F(CombiningFeedViewTest, require_that_handleRemove_sends_op_with_valid_dbdId_to_2_feed_views)
{
    RemoveOperationWithDocId op = remove(1);
    op.setDbDocumentId(DbDocumentId(REMOVED, 1));
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    _view.handleRemove(FeedToken(), op);
    EXPECT_EQ(1u, _ready._view->_handleRemove);
    EXPECT_EQ(1u, _removed._view->_handleRemove);
    EXPECT_EQ(0u, _notReady._view->_handleRemove);
}


TEST_F(CombiningFeedViewTest, require_that_handleRemove_sends_op_with_invalid_dbdId_to_prev_view)
{
    RemoveOperationWithDocId op = remove(1);
    // can be used in the case where removed feed view does not remember removes.
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    _view.handleRemove(FeedToken(), op);
    EXPECT_EQ(1u, _ready._view->_handleRemove);
    EXPECT_EQ(0u, _removed._view->_handleRemove);
    EXPECT_EQ(0u, _notReady._view->_handleRemove);
}


TEST_F(CombiningFeedViewTest, require_that_prepareUpdate_sends_to_ready_view_first)
{
    UpdateOperation op = update(1);
    // indicate that doc is in ready view
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    _view.prepareUpdate(op);
    EXPECT_EQ(1u, _ready._view->_prepareUpdate);
    EXPECT_EQ(0u, _removed._view->_prepareUpdate);
    EXPECT_EQ(0u, _notReady._view->_prepareUpdate);
}


TEST_F(CombiningFeedViewTest, require_that_prepareUpdate_sends_to_not_ready_view_if_not_found_in_ready_view)
{
    UpdateOperation op = update(1);
    _view.prepareUpdate(op);
    EXPECT_EQ(1u, _ready._view->_prepareUpdate);
    EXPECT_EQ(0u, _removed._view->_prepareUpdate);
    EXPECT_EQ(1u, _notReady._view->_prepareUpdate);
}


TEST_F(CombiningFeedViewTest, require_that_handleUpdate_sends_op_to_correct_view)
{
    UpdateOperation op = update(1);
    op.setDbDocumentId(DbDocumentId(READY, 1));
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    _view.handleUpdate(FeedToken(), op);
    EXPECT_EQ(1u, _ready._view->_handleUpdate);
    EXPECT_EQ(0u, _removed._view->_handleUpdate);
    EXPECT_EQ(0u, _notReady._view->_handleUpdate);
}


TEST_F(CombiningFeedViewTest, require_that_prepareMove_sends_op_to_correct_feed_view)
{
    MoveOperation op = move(1, DbDocumentId(READY, 1), DbDocumentId(NOT_READY, 1));
    _view.prepareMove(op);
    EXPECT_EQ(0u, _ready._view->_prepareMove);
    EXPECT_EQ(0u, _removed._view->_prepareMove);
    EXPECT_EQ(1u, _notReady._view->_prepareMove);
}


TEST_F(CombiningFeedViewTest, require_that_handleMove_sends_op_to_2_feed_views)
{
    MoveOperation op = move(1, DbDocumentId(READY, 1), DbDocumentId(NOT_READY, 1));
    _view.handleMove(op, IDestructorCallback::SP());
    EXPECT_EQ(1u, _ready._view->_handleMove);
    EXPECT_EQ(0u, _removed._view->_handleMove);
    EXPECT_EQ(1u, _notReady._view->_handleMove);
}


TEST_F(CombiningFeedViewTest, require_that_handleMove_sends_op_to_1_feed_view)
{
    // same source and target
    MoveOperation op = move(1, DbDocumentId(READY, 1), DbDocumentId(READY, 1));
    _view.handleMove(op, IDestructorCallback::SP());
    EXPECT_EQ(1u, _ready._view->_handleMove);
    EXPECT_EQ(0u, _removed._view->_handleMove);
    EXPECT_EQ(0u, _notReady._view->_handleMove);
}


TEST_F(CombiningFeedViewTest, require_that_delete_bucket_is_sent_to_all_feed_views)
{
    DeleteBucketOperation op;
    _view.prepareDeleteBucket(op);
    EXPECT_EQ(1u, _ready._view->_prepareDeleteBucket);
    EXPECT_EQ(1u, _removed._view->_prepareDeleteBucket);
    EXPECT_EQ(1u, _notReady._view->_prepareDeleteBucket);
    _view.handleDeleteBucket(op, IDestructorCallback::SP());
    EXPECT_EQ(1u, _ready._view->_handleDeleteBucket);
    EXPECT_EQ(1u, _removed._view->_handleDeleteBucket);
    EXPECT_EQ(1u, _notReady._view->_handleDeleteBucket);
}


TEST_F(CombiningFeedViewTest, require_that_heart_beat_is_sent_to_all_feed_views)
{
    _view.heartBeat(5, IDestructorCallback::SP());
    EXPECT_EQ(1u, _ready._view->_heartBeat);
    EXPECT_EQ(1u, _removed._view->_heartBeat);
    EXPECT_EQ(1u, _notReady._view->_heartBeat);
}


TEST_F(CombiningFeedViewTest, require_that_prune_removed_documents_is_sent_to_removed_view)
{
    PruneRemovedDocumentsOperation op;
    _view.handlePruneRemovedDocuments(op, IDestructorCallback::SP());
    EXPECT_EQ(0u, _ready._view->_handlePrune);
    EXPECT_EQ(1u, _removed._view->_handlePrune);
    EXPECT_EQ(0u, _notReady._view->_handlePrune);
}


TEST_F(CombiningFeedViewTest, require_that_calculator_can_be_updated)
{
    _calc->addReady(userDocs().getBucket(1));
    PutOperation op1 = put(1);
    PutOperation op2 = put(2);
    {
        test::BucketStateCalculator::SP calc;
        _view.setCalculator(calc);
        _view.preparePut(op1);
        EXPECT_EQ(1u, _ready._view->_preparePut);
        EXPECT_EQ(0u, _notReady._view->_preparePut);
        _view.preparePut(op2);
        EXPECT_EQ(2u, _ready._view->_preparePut);
        EXPECT_EQ(0u, _notReady._view->_preparePut);
    }
    {
        test::BucketStateCalculator::SP calc(new test::BucketStateCalculator());
        calc->addReady(userDocs().getBucket(2));
        _view.setCalculator(calc);
        _view.preparePut(op1);
        EXPECT_EQ(2u, _ready._view->_preparePut);
        EXPECT_EQ(1u, _notReady._view->_preparePut);
        _view.preparePut(op2);
        EXPECT_EQ(3u, _ready._view->_preparePut);
        EXPECT_EQ(1u, _notReady._view->_preparePut);
    }
    {
        test::BucketStateCalculator::SP calc(new test::BucketStateCalculator());
        calc->setClusterUp(false);
        _view.setCalculator(calc);
        _view.preparePut(op1);
        EXPECT_EQ(4u, _ready._view->_preparePut);
        EXPECT_EQ(1u, _notReady._view->_preparePut);
        _view.preparePut(op2);
        EXPECT_EQ(5u, _ready._view->_preparePut);
        EXPECT_EQ(1u, _notReady._view->_preparePut);
    }
}

TEST_F(CombiningFeedViewTest, require_that_compactLidSpace_is_sent_to_correct_feed_view)
{
    _view.handleCompactLidSpace(CompactLidSpaceOperation(1, 99), IDestructorCallback::SP());
    EXPECT_EQ(0u, _ready._view->_wantedLidLimit);
    EXPECT_EQ(99u, _removed._view->_wantedLidLimit);
    EXPECT_EQ(0u, _notReady._view->_wantedLidLimit);
}
