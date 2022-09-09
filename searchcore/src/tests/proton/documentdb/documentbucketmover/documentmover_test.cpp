// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/searchcore/proton/server/documentbucketmover.h>
#include <vespa/searchcore/proton/common/pendinglidtracker.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("document_bucket_mover_test");

using namespace proton;
using namespace proton::move::test;
using document::BucketId;

struct MySubDbTwoBuckets : public MySubDb
{
    MySubDbTwoBuckets(test::UserDocumentsBuilder &builder,
                      std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                      uint32_t subDbId,
                      SubDbType subDbType)
        : MySubDb(builder.getRepo(), std::move(bucketDB), subDbId, subDbType)
    {
        builder.createDocs(1, 1, 6);
        builder.createDocs(2, 6, 9);
        insertDocs(builder.getDocs());
        assert(bucket(1) != bucket(2));
        assert(5u == docs(1).size());
        assert(3u == docs(2).size());
        assert(9u == _realRetriever->_docs.size());
    }
};

struct DocumentMoverTest : ::testing::Test
{
    test::UserDocumentsBuilder _builder;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    MyMoveOperationLimiter     _limiter;
    //TODO When we retire old bucket move job me must make rewrite this test to use the BucketMover directly.
    DocumentBucketMover        _mover;
    MySubDbTwoBuckets          _source;
    bucketdb::BucketDBOwner    _bucketDb;
    MyMoveHandler              _handler;
    PendingLidTracker          _pendingLidsForCommit;
    DocumentMoverTest()
        : _builder(),
          _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
          _limiter(),
          _mover(_limiter, _bucketDb),
          _source(_builder, _bucketDB, 0u, SubDbType::READY),
          _bucketDb(),
          _handler(_bucketDb)
    {
    }
    void setupForBucket(const BucketId &bucket,
                        uint32_t sourceSubDbId,
                        uint32_t targetSubDbId) {
        _source._subDb = MaintenanceDocumentSubDB(_source._subDb.name(),
                                                  sourceSubDbId,
                                                  _source._subDb.meta_store(),
                                                  _source._subDb.retriever(),
                                                  _source._subDb.feed_view(),
                                                  &_pendingLidsForCommit);
        _mover.setupForBucket(bucket, &_source._subDb, targetSubDbId, _handler);
    }
    bool moveDocuments(size_t maxDocsToMove) {
        return _mover.moveDocuments(maxDocsToMove);
    }
};

TEST_F(DocumentMoverTest, require_that_initial_bucket_mover_is_done)
{
    MyMoveOperationLimiter limiter;
    DocumentBucketMover mover(limiter, _bucketDb);
    EXPECT_TRUE(mover.bucketDone());
    EXPECT_FALSE(mover.needReschedule());
    mover.moveDocuments(2);
    EXPECT_TRUE(mover.bucketDone());
    EXPECT_FALSE(mover.needReschedule());
}

TEST_F(DocumentMoverTest, require_that_we_can_move_all_documents)
{
    setupForBucket(_source.bucket(1), 6, 9);
    EXPECT_TRUE(moveDocuments(5));
    EXPECT_TRUE(_mover.bucketDone());
    EXPECT_EQ(5u, _handler._moves.size());
    EXPECT_EQ(5u, _limiter.beginOpCount);
    for (size_t i = 0; i < 5u; ++i) {
        assertEqual(_source.bucket(1), _source.docs(1)[0], 6, 9, _handler._moves[0]);
    }
}

TEST_F(DocumentMoverTest, require_that_move_is_stalled_if_document_is_pending_commit)
{
    setupForBucket(_source.bucket(1), 6, 9);
    {
        IPendingLidTracker::Token token = _pendingLidsForCommit.produce(1);
        EXPECT_FALSE(moveDocuments(5));
        EXPECT_FALSE(_mover.bucketDone());
    }
    EXPECT_TRUE(moveDocuments(5));
    EXPECT_TRUE(_mover.bucketDone());
    EXPECT_EQ(5u, _handler._moves.size());
    EXPECT_EQ(5u, _limiter.beginOpCount);
    for (size_t i = 0; i < 5u; ++i) {
        assertEqual(_source.bucket(1), _source.docs(1)[0], 6, 9, _handler._moves[0]);
    }
}

TEST_F(DocumentMoverTest, require_that_bucket_is_cached_when_IDocumentMoveHandler_handles_move_operation)
{
    setupForBucket(_source.bucket(1), 6, 9);
    EXPECT_TRUE(moveDocuments(5));
    EXPECT_TRUE(_mover.bucketDone());
    EXPECT_EQ(5u, _handler._moves.size());
    EXPECT_EQ(5u, _handler._numCachedBuckets);
    EXPECT_FALSE(_bucketDb.takeGuard()->isCachedBucket(_source.bucket(1)));
}

TEST_F(DocumentMoverTest, require_that_we_can_move_documents_in_several_steps)
{
    setupForBucket(_source.bucket(1), 6, 9);
    moveDocuments(2);
    EXPECT_FALSE(_mover.bucketDone());
    EXPECT_EQ(2u, _handler._moves.size());
    assertEqual(_source.bucket(1), _source.docs(1)[0], 6, 9, _handler._moves[0]);
    assertEqual(_source.bucket(1), _source.docs(1)[1], 6, 9, _handler._moves[1]);
    EXPECT_TRUE(moveDocuments(2));
    EXPECT_FALSE(_mover.bucketDone());
    EXPECT_EQ(4u, _handler._moves.size());
    assertEqual(_source.bucket(1), _source.docs(1)[2], 6, 9, _handler._moves[2]);
    assertEqual(_source.bucket(1), _source.docs(1)[3], 6, 9, _handler._moves[3]);
    EXPECT_TRUE(moveDocuments(2));
    EXPECT_TRUE(_mover.bucketDone());
    EXPECT_EQ(5u, _handler._moves.size());
    assertEqual(_source.bucket(1), _source.docs(1)[4], 6, 9, _handler._moves[4]);
    EXPECT_TRUE(moveDocuments(2));
    EXPECT_TRUE(_mover.bucketDone());
    EXPECT_EQ(5u, _handler._moves.size());
}

TEST_F(DocumentMoverTest, require_that_cancel_signal_rescheduling_need) {
    setupForBucket(_source.bucket(1), 6, 9);
    EXPECT_FALSE(_mover.bucketDone());
    EXPECT_FALSE(_mover.needReschedule());
    EXPECT_TRUE(moveDocuments(2));
    EXPECT_FALSE(_mover.bucketDone());
    EXPECT_FALSE(_mover.needReschedule());
    _mover.cancel();
    EXPECT_TRUE(_mover.bucketDone());
    EXPECT_TRUE(_mover.needReschedule());
}

GTEST_MAIN_RUN_ALL_TESTS()
