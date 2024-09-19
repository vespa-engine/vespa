// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/server/buckethandler.h>
#include <vespa/searchcore/proton/server/ibucketstatechangedhandler.h>
#include <vespa/searchcore/proton/server/ibucketmodifiedhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace proton;
using document::BucketId;
using document::GlobalId;
using storage::spi::Bucket;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using storage::spi::test::makeSpiBucket;
using vespalib::ThreadStackExecutor;
using proton::test::BucketStateCalculator;

namespace {
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

struct BucketInfoStats {
    uint32_t _doc_count;
    uint32_t _meta_count;
    size_t _doc_sizes;
    size_t _entry_sizes;

    BucketInfoStats(uint32_t doc_count, uint32_t meta_count, size_t doc_sizes, size_t entry_sizes)
        : _doc_count(doc_count),
          _meta_count(meta_count),
          _doc_sizes(doc_sizes),
          _entry_sizes(entry_sizes)
    {
    }
    BucketInfoStats(const BucketInfo& info)
        : BucketInfoStats(info.getDocumentCount(), info.getEntryCount(), info.getDocumentSize(), info.getUsedSize())
    {
    }

    bool operator==(const BucketInfoStats& rhs) const noexcept {
        return _doc_count == rhs._doc_count && _meta_count == rhs._meta_count &&
        _doc_sizes == rhs._doc_sizes && _entry_sizes == rhs._entry_sizes;
    }
};

void PrintTo(const BucketInfoStats& stats, std::ostream* os) {
    *os << "{" << stats._doc_count << "," << stats._meta_count << "," <<
    stats._doc_sizes << "," << stats._entry_sizes << "}";
}

class BucketHandlerTest : public ::testing::Test
{
protected:
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
    BucketHandlerTest() __attribute__((noinline));
    ~BucketHandlerTest() override __attribute__((noinline));
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

BucketHandlerTest::BucketHandlerTest()
    : ::testing::Test(),
      _builder(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _ready(_bucketDB, SubDbType::READY),
      _removed(_bucketDB, SubDbType::REMOVED),
      _notReady(_bucketDB, SubDbType::NOTREADY),
      _exec(1),
      _handler(_exec),
      _changedHandler(),
      _calc(std::make_shared<BucketStateCalculator>()),
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

BucketHandlerTest::~BucketHandlerTest()
{
    _handler.removeBucketStateChangedHandler(&_changedHandler);
}

}


TEST_F(BucketHandlerTest, require_that_handleListBuckets_returns_buckets_from_all_sub_dbs)
{
    _handler.handleListBuckets(_bucketList);
    EXPECT_EQ(4u, _bucketList.getList().size());
    EXPECT_EQ(_ready.bucket(2), _bucketList.getList()[0]);
    EXPECT_EQ(_ready.bucket(3), _bucketList.getList()[1]);
    EXPECT_EQ(_ready.bucket(4), _bucketList.getList()[2]);
    EXPECT_EQ(_ready.bucket(7), _bucketList.getList()[3]);
    EXPECT_EQ(_removed.bucket(2), _bucketList.getList()[0]);
    EXPECT_EQ(_notReady.bucket(4), _bucketList.getList()[2]);
}

TEST_F(BucketHandlerTest, test_hasBucket) {
    EXPECT_FALSE(_handler.hasBucket(makeSpiBucket(BUCKET_1)));
    EXPECT_TRUE(_handler.hasBucket(makeSpiBucket(_ready.bucket(2))));
}


TEST_F(BucketHandlerTest, require_that_bucket_is_reported_in_handleGetBucketInfo)
{
    handleGetBucketInfo(_ready.bucket(3));
    EXPECT_EQ(BucketInfoStats(3, 3, 3000, 3000), BucketInfoStats(_bucketInfo.getInfo()));

    handleGetBucketInfo(_ready.bucket(2)); // bucket 2 also in removed sub db
    EXPECT_EQ(BucketInfoStats(2, 6, 2000, 6000), BucketInfoStats(_bucketInfo.getInfo()));
}


TEST_F(BucketHandlerTest, require_that_handleGetBucketInfo_can_get_cached_bucket)
{
    {
        bucketdb::Guard db = _bucketDB->takeGuard();
        db->add(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::READY);
        db->cacheBucket(BUCKET_1);
        db->add(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::NOTREADY);
    }
    handleGetBucketInfo(BUCKET_1);
    EXPECT_EQ(BucketInfoStats(1, 1, DOCSIZE_1, DOCSIZE_1), BucketInfoStats(_bucketInfo.getInfo()));

    _bucketDB->takeGuard()->uncacheBucket();

    handleGetBucketInfo(BUCKET_1);
    EXPECT_EQ(BucketInfoStats(2, 2, 2 * DOCSIZE_1, 2 * DOCSIZE_1), BucketInfoStats(_bucketInfo.getInfo()));
    {
        // Must ensure empty bucket db before destruction.
        bucketdb::Guard db = _bucketDB->takeGuard();
        db->remove(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::READY);
        db->remove(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SubDbType::NOTREADY);
    }
}


TEST_F(BucketHandlerTest, require_that_changed_handlers_are_notified_when_bucket_state_changes)
{
    _handler.handleSetCurrentState(_ready.bucket(2), BucketInfo::ACTIVE, _genResult);
    sync();
    EXPECT_EQ(_ready.bucket(2), _changedHandler._bucket);
    EXPECT_EQ(BucketInfo::ACTIVE, _changedHandler._state);
    _handler.handleSetCurrentState(_ready.bucket(3), BucketInfo::NOT_ACTIVE, _genResult);
    sync();
    EXPECT_EQ(_ready.bucket(3), _changedHandler._bucket);
    EXPECT_EQ(BucketInfo::NOT_ACTIVE, _changedHandler._state);
}


TEST_F(BucketHandlerTest, require_that_unready_bucket_can_be_reported_as_active)
{
    _handler.handleSetCurrentState(_ready.bucket(4),
                                   BucketInfo::ACTIVE, _genResult);
    sync();
    EXPECT_EQ(_ready.bucket(4), _changedHandler._bucket);
    EXPECT_EQ(BucketInfo::ACTIVE, _changedHandler._state);
    handleGetBucketInfo(_ready.bucket(4));
    EXPECT_EQ(true, _bucketInfo.getInfo().isActive());
    EXPECT_EQ(false, _bucketInfo.getInfo().isReady());
}


TEST_F(BucketHandlerTest, node_going_down_but_not_into_maintenance_state_deactivates_all_buckets)
{
    _handler.handleSetCurrentState(_ready.bucket(2),
                                     BucketInfo::ACTIVE, _genResult);
    sync();
    EXPECT_EQ(_ready.bucket(2), _changedHandler._bucket);
    EXPECT_EQ(BucketInfo::ACTIVE, _changedHandler._state);
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_EQ(true, _bucketInfo.getInfo().isActive());
    setNodeUp(false);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_EQ(false, _bucketInfo.getInfo().isActive());
    _handler.handleSetCurrentState(_ready.bucket(2),
                                     BucketInfo::ACTIVE, _genResult);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_EQ(false, _bucketInfo.getInfo().isActive());
    setNodeUp(true);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_EQ(false, _bucketInfo.getInfo().isActive());
    _handler.handleSetCurrentState(_ready.bucket(2),
                                   BucketInfo::ACTIVE, _genResult);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_EQ(true, _bucketInfo.getInfo().isActive());
}

TEST_F(BucketHandlerTest, node_going_into_maintenance_state_does__not__deactivate_any_buckets)
{
    _handler.handleSetCurrentState(_ready.bucket(2),
                                   BucketInfo::ACTIVE, _genResult);
    sync();
    setNodeMaintenance(true);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_TRUE(_bucketInfo.getInfo().isActive());
}

TEST_F(BucketHandlerTest, node_going_from_maintenance_to_up_state_deactivates_all_buckets)
{
    _handler.handleSetCurrentState(_ready.bucket(2),
                                   BucketInfo::ACTIVE, _genResult);
    sync();
    setNodeMaintenance(true);
    sync();
    setNodeUp(true);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_FALSE(_bucketInfo.getInfo().isActive());
}

TEST_F(BucketHandlerTest, node_going_from_maintenance_to_down_state_deactivates_all_buckets)
{
    _handler.handleSetCurrentState(_ready.bucket(2),
                                   BucketInfo::ACTIVE, _genResult);
    sync();
    setNodeMaintenance(true);
    sync();
    setNodeUp(false);
    sync();
    handleGetBucketInfo(_ready.bucket(2));
    EXPECT_FALSE(_bucketInfo.getInfo().isActive());
}
