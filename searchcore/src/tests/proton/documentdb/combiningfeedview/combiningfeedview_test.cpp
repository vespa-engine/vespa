// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_bucket_space.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/server/combiningfeedview.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("combiningfeedview_test");

using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::test::makeBucketSpace;
using search::IDestructorCallback;
using search::SerialNum;
using storage::spi::Timestamp;
using namespace proton;

typedef std::vector<IFeedView::SP> FeedViewVector;

struct MyStreamHandler : public NewConfigOperation::IStreamHandler
{
    void serializeConfig(SerialNum, vespalib::nbostream &) override {}
    void deserializeConfig(SerialNum, vespalib::nbostream &) override {}
};


struct MyFeedView : public test::DummyFeedView
{
    typedef std::shared_ptr<MyFeedView> SP;
    DocumentMetaStore    _metaStore;
    MyStreamHandler      _streamHandler;
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
               std::shared_ptr<BucketDBOwner> bucketDB,
               SubDbType subDbType) :
        test::DummyFeedView(repo),
        _metaStore(bucketDB,
                   DocumentMetaStore::getFixedName(),
                   search::GrowStrategy(),
                   documentmetastore::IGidCompare::SP(
                           new documentmetastore::DefaultGidCompare),
                   subDbType),
        _streamHandler(),
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
    void handleDeleteBucket(const DeleteBucketOperation &) override { ++_handleDeleteBucket; }
    void prepareMove(MoveOperation &) override { ++_prepareMove; }
    void handleMove(const MoveOperation &, IDestructorCallback::SP) override { ++_handleMove; }
    void heartBeat(SerialNum) override { ++_heartBeat; }
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &) override { ++_handlePrune; }
    void handleCompactLidSpace(const CompactLidSpaceOperation &op) override {
        _wantedLidLimit = op.getLidLimit();
    }
};


struct MySubDb
{
    MyFeedView::SP _view;
    MySubDb(const std::shared_ptr<const DocumentTypeRepo> &repo,
            std::shared_ptr<BucketDBOwner> bucketDB,
            SubDbType subDbType)
        : _view(new MyFeedView(repo, bucketDB, subDbType))
    {
    }
    void insertDocs(const test::BucketDocuments &docs) {
        for (size_t i = 0; i < docs.getDocs().size(); ++i) {
            const test::Document &testDoc = docs.getDocs()[i];
            _view->_metaStore.put(testDoc.getGid(), testDoc.getBucket(),
                                  testDoc.getTimestamp(), testDoc.getDocSize(), testDoc.getLid());
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

struct Fixture
{
    test::UserDocumentsBuilder      _builder;
    std::shared_ptr<BucketDBOwner>  _bucketDB;
    MySubDb                         _ready;
    MySubDb                         _removed;
    MySubDb                         _notReady;
    test::BucketStateCalculator::SP _calc;
    CombiningFeedView               _view;
    Fixture() :
        _builder(),
        _bucketDB(std::make_shared<BucketDBOwner>()),
        _ready(_builder.getRepo(), _bucketDB, SubDbType::READY),
        _removed(_builder.getRepo(), _bucketDB, SubDbType::REMOVED),
        _notReady(_builder.getRepo(), _bucketDB, SubDbType::NOTREADY),
        _calc(new test::BucketStateCalculator()),
        _view(getVector(_ready, _removed, _notReady), makeBucketSpace(), _calc)
    {
        _builder.createDoc(1, 1);
        _builder.createDoc(2, 2);
    }
    const test::UserDocuments &userDocs() const { return _builder.getDocs(); }
    const test::BucketDocuments &userDocs(uint32_t userId) const { return userDocs().getUserDocs(userId); }
    PutOperation put(uint32_t userId) {
        const test::Document &doc = userDocs().getDocs(userId)[0];
        return PutOperation(doc.getBucket(), doc.getTimestamp(), doc.getDoc());
    }
    RemoveOperation remove(uint32_t userId) {
        const test::Document &doc = userDocs().getDocs(userId)[0];
        return RemoveOperation(doc.getBucket(), doc.getTimestamp(), doc.getDoc()->getId());
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


TEST_F("require that preparePut() sends to ready view", Fixture)
{
    PutOperation op = f.put(1);
    f._calc->addReady(f.userDocs().getBucket(1));
    f._view.preparePut(op);
    EXPECT_EQUAL(1u, f._ready._view->_preparePut);
    EXPECT_EQUAL(0u, f._removed._view->_preparePut);
    EXPECT_EQUAL(0u, f._notReady._view->_preparePut);
    EXPECT_FALSE(op.getValidPrevDbdId());
}


TEST_F("require that preparePut() sends to not ready view", Fixture)
{
    PutOperation op = f.put(1);
    f._view.preparePut(op);
    EXPECT_EQUAL(0u, f._ready._view->_preparePut);
    EXPECT_EQUAL(0u, f._removed._view->_preparePut);
    EXPECT_EQUAL(1u, f._notReady._view->_preparePut);
    EXPECT_FALSE(op.getValidPrevDbdId());
}


TEST_F("require that preparePut() can fill previous dbdId", Fixture)
{
    // insert bucket 1 in removed view
    f._removed.insertDocs(f.userDocs(1));
    PutOperation op = f.put(1);
    f._view.preparePut(op);
    EXPECT_EQUAL(1u, op.getPrevLid());
    EXPECT_EQUAL(REMOVED, op.getPrevSubDbId());
    EXPECT_EQUAL(Timestamp(1), op.getPrevTimestamp());
    EXPECT_TRUE(op.getPrevMarkedAsRemoved());
}


TEST_F("require that handlePut() sends to 1 feed view", Fixture)
{
    PutOperation op = f.put(2);
    op.setDbDocumentId(DbDocumentId(READY, 2));
    f._view.handlePut(FeedToken(), op);
    EXPECT_EQUAL(1u, f._ready._view->_handlePut);
    EXPECT_EQUAL(0u, f._removed._view->_handlePut);
    EXPECT_EQUAL(0u, f._notReady._view->_handlePut);
}


TEST_F("require that handlePut() sends to 2 feed views", Fixture)
{
    PutOperation op = f.put(2);
    op.setDbDocumentId(DbDocumentId(NOT_READY, 2));
    op.setPrevDbDocumentId(DbDocumentId(REMOVED, 2));
    f._view.handlePut(FeedToken(), op);
    EXPECT_EQUAL(0u, f._ready._view->_handlePut);
    EXPECT_EQUAL(1u, f._removed._view->_handlePut);
    EXPECT_EQUAL(1u, f._notReady._view->_handlePut);
}


TEST_F("require that prepareRemove() sends to removed view", Fixture)
{
    RemoveOperation op = f.remove(1);
    f._view.prepareRemove(op);
    EXPECT_EQUAL(0u, f._ready._view->_prepareRemove);
    EXPECT_EQUAL(1u, f._removed._view->_prepareRemove);
    EXPECT_EQUAL(0u, f._notReady._view->_prepareRemove);
    EXPECT_FALSE(op.getValidPrevDbdId());
}


TEST_F("require that prepareRemove() can fill previous dbdId", Fixture)
{
    f._ready.insertDocs(f.userDocs(1));
    RemoveOperation op = f.remove(1);
    f._view.prepareRemove(op);
    EXPECT_EQUAL(1u, op.getPrevLid());
    EXPECT_EQUAL(READY, op.getPrevSubDbId());
    EXPECT_EQUAL(Timestamp(1), op.getPrevTimestamp());
    EXPECT_FALSE(op.getPrevMarkedAsRemoved());
}


TEST_F("require that handleRemove() sends op with valid dbdId to 1 feed view", Fixture)
{
    RemoveOperation op = f.remove(1);
    op.setDbDocumentId(DbDocumentId(REMOVED, 1));
    f._view.handleRemove(FeedToken(), op);
    EXPECT_EQUAL(0u, f._ready._view->_handleRemove);
    EXPECT_EQUAL(1u, f._removed._view->_handleRemove);
    EXPECT_EQUAL(0u, f._notReady._view->_handleRemove);
}


TEST_F("require that handleRemove() sends op with valid dbdId to 2 feed views", Fixture)
{
    RemoveOperation op = f.remove(1);
    op.setDbDocumentId(DbDocumentId(REMOVED, 1));
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    f._view.handleRemove(FeedToken(), op);
    EXPECT_EQUAL(1u, f._ready._view->_handleRemove);
    EXPECT_EQUAL(1u, f._removed._view->_handleRemove);
    EXPECT_EQUAL(0u, f._notReady._view->_handleRemove);
}


TEST_F("require that handleRemove() sends op with invalid dbdId to prev view", Fixture)
{
    RemoveOperation op = f.remove(1);
    // can be used in the case where removed feed view does not remember removes.
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    f._view.handleRemove(FeedToken(), op);
    EXPECT_EQUAL(1u, f._ready._view->_handleRemove);
    EXPECT_EQUAL(0u, f._removed._view->_handleRemove);
    EXPECT_EQUAL(0u, f._notReady._view->_handleRemove);
}


TEST_F("require that prepareUpdate() sends to ready view first", Fixture)
{
    UpdateOperation op = f.update(1);
    // indicate that doc is in ready view
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    f._view.prepareUpdate(op);
    EXPECT_EQUAL(1u, f._ready._view->_prepareUpdate);
    EXPECT_EQUAL(0u, f._removed._view->_prepareUpdate);
    EXPECT_EQUAL(0u, f._notReady._view->_prepareUpdate);
}


TEST_F("require that prepareUpdate() sends to not ready view if not found in ready view", Fixture)
{
    UpdateOperation op = f.update(1);
    f._view.prepareUpdate(op);
    EXPECT_EQUAL(1u, f._ready._view->_prepareUpdate);
    EXPECT_EQUAL(0u, f._removed._view->_prepareUpdate);
    EXPECT_EQUAL(1u, f._notReady._view->_prepareUpdate);
}


TEST_F("require that handleUpdate() sends op to correct view", Fixture)
{
    UpdateOperation op = f.update(1);
    op.setDbDocumentId(DbDocumentId(READY, 1));
    op.setPrevDbDocumentId(DbDocumentId(READY, 1));
    f._view.handleUpdate(FeedToken(), op);
    EXPECT_EQUAL(1u, f._ready._view->_handleUpdate);
    EXPECT_EQUAL(0u, f._removed._view->_handleUpdate);
    EXPECT_EQUAL(0u, f._notReady._view->_handleUpdate);
}


TEST_F("require that prepareMove() sends op to correct feed view", Fixture)
{
    MoveOperation op = f.move(1, DbDocumentId(READY, 1), DbDocumentId(NOT_READY, 1));
    f._view.prepareMove(op);
    EXPECT_EQUAL(0u, f._ready._view->_prepareMove);
    EXPECT_EQUAL(0u, f._removed._view->_prepareMove);
    EXPECT_EQUAL(1u, f._notReady._view->_prepareMove);
}


TEST_F("require that handleMove() sends op to 2 feed views", Fixture)
{
    MoveOperation op = f.move(1, DbDocumentId(READY, 1), DbDocumentId(NOT_READY, 1));
    f._view.handleMove(op, IDestructorCallback::SP());
    EXPECT_EQUAL(1u, f._ready._view->_handleMove);
    EXPECT_EQUAL(0u, f._removed._view->_handleMove);
    EXPECT_EQUAL(1u, f._notReady._view->_handleMove);
}


TEST_F("require that handleMove() sends op to 1 feed view", Fixture)
{
    // same source and target
    MoveOperation op = f.move(1, DbDocumentId(READY, 1), DbDocumentId(READY, 1));
    f._view.handleMove(op, IDestructorCallback::SP());
    EXPECT_EQUAL(1u, f._ready._view->_handleMove);
    EXPECT_EQUAL(0u, f._removed._view->_handleMove);
    EXPECT_EQUAL(0u, f._notReady._view->_handleMove);
}


TEST_F("require that delete bucket is sent to all feed views", Fixture)
{
    DeleteBucketOperation op;
    f._view.prepareDeleteBucket(op);
    EXPECT_EQUAL(1u, f._ready._view->_prepareDeleteBucket);
    EXPECT_EQUAL(1u, f._removed._view->_prepareDeleteBucket);
    EXPECT_EQUAL(1u, f._notReady._view->_prepareDeleteBucket);
    f._view.handleDeleteBucket(op);
    EXPECT_EQUAL(1u, f._ready._view->_handleDeleteBucket);
    EXPECT_EQUAL(1u, f._removed._view->_handleDeleteBucket);
    EXPECT_EQUAL(1u, f._notReady._view->_handleDeleteBucket);
}


TEST_F("require that heart beat is sent to all feed views", Fixture)
{
    f._view.heartBeat(5);
    EXPECT_EQUAL(1u, f._ready._view->_heartBeat);
    EXPECT_EQUAL(1u, f._removed._view->_heartBeat);
    EXPECT_EQUAL(1u, f._notReady._view->_heartBeat);
}


TEST_F("require that prune removed documents is sent to removed view", Fixture)
{
    PruneRemovedDocumentsOperation op;
    f._view.handlePruneRemovedDocuments(op);
    EXPECT_EQUAL(0u, f._ready._view->_handlePrune);
    EXPECT_EQUAL(1u, f._removed._view->_handlePrune);
    EXPECT_EQUAL(0u, f._notReady._view->_handlePrune);
}


TEST_F("require that calculator can be updated", Fixture)
{
    f._calc->addReady(f.userDocs().getBucket(1));
    PutOperation op1 = f.put(1);
    PutOperation op2 = f.put(2);
    {
        test::BucketStateCalculator::SP calc;
        f._view.setCalculator(calc);
        f._view.preparePut(op1);
        EXPECT_EQUAL(1u, f._ready._view->_preparePut);
        EXPECT_EQUAL(0u, f._notReady._view->_preparePut);
        f._view.preparePut(op2);
        EXPECT_EQUAL(2u, f._ready._view->_preparePut);
        EXPECT_EQUAL(0u, f._notReady._view->_preparePut);
    }
    {
        test::BucketStateCalculator::SP calc(new test::BucketStateCalculator());
        calc->addReady(f.userDocs().getBucket(2));
        f._view.setCalculator(calc);
        f._view.preparePut(op1);
        EXPECT_EQUAL(2u, f._ready._view->_preparePut);
        EXPECT_EQUAL(1u, f._notReady._view->_preparePut);
        f._view.preparePut(op2);
        EXPECT_EQUAL(3u, f._ready._view->_preparePut);
        EXPECT_EQUAL(1u, f._notReady._view->_preparePut);
    }
    {
        test::BucketStateCalculator::SP calc(new test::BucketStateCalculator());
        calc->setClusterUp(false);
        f._view.setCalculator(calc);
        f._view.preparePut(op1);
        EXPECT_EQUAL(4u, f._ready._view->_preparePut);
        EXPECT_EQUAL(1u, f._notReady._view->_preparePut);
        f._view.preparePut(op2);
        EXPECT_EQUAL(5u, f._ready._view->_preparePut);
        EXPECT_EQUAL(1u, f._notReady._view->_preparePut);
    }
}

TEST_F("require that compactLidSpace() is sent to correct feed view", Fixture)
{
    f._view.handleCompactLidSpace(CompactLidSpaceOperation(1, 99));
    EXPECT_EQUAL(0u, f._ready._view->_wantedLidLimit);
    EXPECT_EQUAL(99u, f._removed._view->_wantedLidLimit);
    EXPECT_EQUAL(0u, f._notReady._view->_wantedLidLimit);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}

