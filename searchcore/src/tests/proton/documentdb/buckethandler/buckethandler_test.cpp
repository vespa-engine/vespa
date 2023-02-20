// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/server/buckethandler.h>
#include <vespa/searchcore/proton/server/ibucketstatechangedhandler.h>
#include <vespa/searchcore/proton/server/ibucketmodifiedhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("buckethandler_test");

using namespace proton;
using document::BucketId;
using document::GlobalId;
using storage::spi::Bucket;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using storage::spi::test::makeSpiBucket;
using vespalib::ThreadStackExecutor;
using proton::test::BucketStateCalculator;

const GlobalId GID_1("111111111111");
const BucketId BUCKET_1(8, GID_1.convertToBucketId().getRawId());
const Timestamp TIME_1(1u);
const uint32_t DOCSIZE_1(4096u);

struct MySubDb
{
    DocumentMetaStore   _metaStore;
    test::UserDocuments _docs;
    MySubDb(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, SubDbType subDbType);
    ~MySubDb();
    void insertDocs(const test::UserDocuments &docs_) {
        _docs = docs_;
        for (const auto & _doc : _docs) {
            const test::BucketDocuments &bucketDocs = _doc.second;
            for (const auto & testDoc : bucketDocs.getDocs()) {
                _metaStore.put(testDoc.getGid(), testDoc.getBucket(),
                               testDoc.getTimestamp(), testDoc.getDocSize(), testDoc.getLid(), 0u);
            }
        }
    }
    BucketId bucket(uint32_t userId) const {
        return _docs.getBucket(userId);
    }
    test::DocumentVector docs(uint32_t userId) const {
        return _docs.getGidOrderDocs(userId);
    }
};

MySubDb::MySubDb(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, SubDbType subDbType)
    : _metaStore(std::move(bucketDB), DocumentMetaStore::getFixedName(), search::GrowStrategy(), subDbType),
      _docs()
{ }
MySubDb::~MySubDb() = default;

struct MyChangedHandler : public IBucketStateChangedHandler
{
    BucketId _bucket;
    BucketInfo::ActiveState _state;
    MyChangedHandler() : _bucket(), _state(BucketInfo::NOT_ACTIVE) {}

    void notifyBucketStateChanged(const document::BucketId &bucketId,
                                  storage::spi::BucketInfo::ActiveState newState) override {
        _bucket = bucketId;
        _state = newState;
    }
};

bool
expectEqual(uint32_t docCount, uint32_t metaCount, size_t docSizes, size_t entrySizes, const BucketInfo &info)
{
    if (!EXPECT_EQUAL(docCount, info.getDocumentCount())) return false;
    if (!EXPECT_EQUAL(metaCount, info.getEntryCount())) return false;
    if (!EXPECT_EQUAL(docSizes, info.getDocumentSize())) return false;
    if (!EXPECT_EQUAL(entrySizes, info.getUsedSize())) return false;
    return true;
}


struct Fixture
{
    test::UserDocumentsBuilder      _builder;
    std::shared_ptr<bucketdb::BucketDBOwner>  _bucketDB;
    MySubDb                         _ready;
    MySubDb                         _removed;
    MySubDb                         _notReady;
    ThreadStackExecutor             _exec;
    BucketHandler                   _handler;
    MyChangedHandler                _changedHandler;
    BucketStateCalculator::SP       _calc;
    test::BucketIdListResultHandler _bucketList;
    test::BucketInfoResultHandler   _bucketInfo;
    std::shared_ptr<test::GenericResultHandler>      _genResult;
    Fixture()
        : _builder(),
          _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
          _ready(_bucketDB, SubDbType::READY),
          _removed(_bucketDB, SubDbType::REMOVED),
          _notReady(_bucketDB, SubDbType::NOTREADY),
          _exec(1),
          _handler(_exec),
          _changedHandler(),
          _calc(new BucketStateCalculator()),
          _bucketList(), _bucketInfo(),
          _genResult(std::make_shared<test::GenericResultHandler>())
    {
        // bucket 2 & 3 & 4 & 7 in ready
        _ready.insertDocs(_builder.createDocs(2, 1, 3).  // 2 docs
                                   createDocs(3, 3, 6).  // 3 docs
                                   createDocs(4, 6, 10). // 4 docs
                                   createDocs(7, 10, 11). // 1 doc
                                   getDocs());
        // bucket 2 in removed
        _removed.insertDocs(_builder.clearDocs().
                                     createDocs(2, 16, 20). // 4 docs
                                     getDocs());
        // bucket 4 in not ready
        _notReady.insertDocs(_builder.clearDocs().
                                      createDocs(4, 22, 24). // 2 docs
                                      getDocs());
        _handler.setReadyBucketHandler(_ready._metaStore);
        _handler.addBucketStateChangedHandler(&_changedHandler);
        _handler.notifyClusterStateChanged(_calc);
    }
    ~Fixture()
    {
        _handler.removeBucketStateChangedHandler(&_changedHandler);
    }
    void sync() { _exec.sync(); }
    void handleGetBucketInfo(const BucketId &bucket) {
        _handler.handleGetBucketInfo(makeSpiBucket(bucket), _bucketInfo);
    }
    void
    setNodeUp(bool value)
    {
        _calc->setNodeUp(value);
        _calc->setNodeMaintenance(false);
        _handler.notifyClusterStateChanged(_calc);
    }
    void setNodeMaintenance(bool value) {
        _calc->setNodeMaintenance(value);
        _handler.notifyClusterStateChanged(_calc);
    }
};


TEST_F("require that handleListBuckets() returns buckets from all sub dbs", Fixture)
{
    f._handler.handleListBuckets(f._bucketList);
    EXPECT_EQUAL(4u, f._bucketList.getList().size());
    EXPECT_EQUAL(f._ready.bucket(2), f._bucketList.getList()[0]);
    EXPECT_EQUAL(f._ready.bucket(3), f._bucketList.getList()[1]);
    EXPECT_EQUAL(f._ready.bucket(4), f._bucketList.getList()[2]);
    EXPECT_EQUAL(f._ready.bucket(7), f._bucketList.getList()[3]);
    EXPECT_EQUAL(f._removed.bucket(2), f._bucketList.getList()[0]);
    EXPECT_EQUAL(f._notReady.bucket(4), f._bucketList.getList()[2]);
}

TEST_F("test hasBucket", Fixture) {
    EXPECT_FALSE(f._handler.hasBucket(makeSpiBucket(BUCKET_1)));
    EXPECT_TRUE(f._handler.hasBucket(makeSpiBucket(f._ready.bucket(2))));
}


TEST_F("require that bucket is reported in handleGetBucketInfo()", Fixture)
{
    f.handleGetBucketInfo(f._ready.bucket(3));
    EXPECT_TRUE(expectEqual(3, 3, 3000, 3000, f._bucketInfo.getInfo()));

    f.handleGetBucketInfo(f._ready.bucket(2)); // bucket 2 also in removed sub db
    EXPECT_TRUE(expectEqual(2, 6, 2000, 6000, f._bucketInfo.getInfo()));
}


TEST_F("require that handleGetBucketInfo() can get cached bucket", Fixture)
{
    {
        bucketdb::Guard db = f._bucketDB->takeGuard();
        db->add(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::READY);
        db->cacheBucket(BUCKET_1);
        db->add(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::NOTREADY);
    }
    f.handleGetBucketInfo(BUCKET_1);
    EXPECT_TRUE(expectEqual(1, 1, DOCSIZE_1, DOCSIZE_1, f._bucketInfo.getInfo()));

    f._bucketDB->takeGuard()->uncacheBucket();

    f.handleGetBucketInfo(BUCKET_1);
    EXPECT_TRUE(expectEqual(2, 2, 2 * DOCSIZE_1, 2 * DOCSIZE_1, f._bucketInfo.getInfo()));
    {
        // Must ensure empty bucket db before destruction.
        bucketdb::Guard db = f._bucketDB->takeGuard();
        db->remove(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::READY);
        db->remove(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::NOTREADY);
    }
}


TEST_F("require that changed handlers are notified when bucket state changes", Fixture)
{
    f._handler.handleSetCurrentState(f._ready.bucket(2), BucketInfo::ACTIVE, f._genResult);
    f.sync();
    EXPECT_EQUAL(f._ready.bucket(2), f._changedHandler._bucket);
    EXPECT_EQUAL(BucketInfo::ACTIVE, f._changedHandler._state);
    f._handler.handleSetCurrentState(f._ready.bucket(3), BucketInfo::NOT_ACTIVE, f._genResult);
    f.sync();
    EXPECT_EQUAL(f._ready.bucket(3), f._changedHandler._bucket);
    EXPECT_EQUAL(BucketInfo::NOT_ACTIVE, f._changedHandler._state);
}


TEST_F("require that unready bucket can be reported as active", Fixture)
{
    f._handler.handleSetCurrentState(f._ready.bucket(4),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    EXPECT_EQUAL(f._ready.bucket(4), f._changedHandler._bucket);
    EXPECT_EQUAL(BucketInfo::ACTIVE, f._changedHandler._state);
    f.handleGetBucketInfo(f._ready.bucket(4));
    EXPECT_EQUAL(true, f._bucketInfo.getInfo().isActive());
    EXPECT_EQUAL(false, f._bucketInfo.getInfo().isReady());
}


TEST_F("node going down (but not into maintenance state) deactivates all buckets", Fixture)
{
    f._handler.handleSetCurrentState(f._ready.bucket(2),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    EXPECT_EQUAL(f._ready.bucket(2), f._changedHandler._bucket);
    EXPECT_EQUAL(BucketInfo::ACTIVE, f._changedHandler._state);
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_EQUAL(true, f._bucketInfo.getInfo().isActive());
    f.setNodeUp(false);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_EQUAL(false, f._bucketInfo.getInfo().isActive());
    f._handler.handleSetCurrentState(f._ready.bucket(2),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_EQUAL(false, f._bucketInfo.getInfo().isActive());
    f.setNodeUp(true);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_EQUAL(false, f._bucketInfo.getInfo().isActive());
    f._handler.handleSetCurrentState(f._ready.bucket(2),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_EQUAL(true, f._bucketInfo.getInfo().isActive());
}

TEST_F("node going into maintenance state does _not_ deactivate any buckets", Fixture)
{
    f._handler.handleSetCurrentState(f._ready.bucket(2),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    f.setNodeMaintenance(true);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_TRUE(f._bucketInfo.getInfo().isActive());
}

TEST_F("node going from maintenance to up state deactivates all buckets", Fixture)
{
    f._handler.handleSetCurrentState(f._ready.bucket(2),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    f.setNodeMaintenance(true);
    f.sync();
    f.setNodeUp(true);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_FALSE(f._bucketInfo.getInfo().isActive());
}

TEST_F("node going from maintenance to down state deactivates all buckets", Fixture)
{
    f._handler.handleSetCurrentState(f._ready.bucket(2),
                                     BucketInfo::ACTIVE, f._genResult);
    f.sync();
    f.setNodeMaintenance(true);
    f.sync();
    f.setNodeUp(false);
    f.sync();
    f.handleGetBucketInfo(f._ready.bucket(2));
    EXPECT_FALSE(f._bucketInfo.getInfo().isActive());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}

