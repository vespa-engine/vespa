// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("document_bucket_mover_test");

using namespace proton;
using namespace proton::move::test;
using document::BucketId;

struct MySubDbTwoBuckets : public MySubDb
{
    MySubDbTwoBuckets(test::UserDocumentsBuilder &builder,
                      std::shared_ptr<BucketDBOwner> bucketDB,
                      uint32_t subDbId,
                      SubDbType subDbType)
        : MySubDb(builder.getRepo(), bucketDB, subDbId, subDbType)
    {
        builder.createDocs(1, 1, 6);
        builder.createDocs(2, 6, 9);
        insertDocs(builder.getDocs());
        ASSERT_NOT_EQUAL(bucket(1), bucket(2));
        ASSERT_EQUAL(5u, docs(1).size());
        ASSERT_EQUAL(3u, docs(2).size());
        ASSERT_EQUAL(9u, _realRetriever->_docs.size());
    }
};

struct MoveFixture
{
    test::UserDocumentsBuilder _builder;
    std::shared_ptr<BucketDBOwner> _bucketDB;
    MyMoveOperationLimiter     _limiter;
    DocumentBucketMover        _mover;
    MySubDbTwoBuckets          _source;
    BucketDBOwner              _bucketDb;
    MyMoveHandler              _handler;
    PendingLidTracker          _pendingLidsForCommit;
    MoveFixture()
        : _builder(),
          _bucketDB(std::make_shared<BucketDBOwner>()),
          _limiter(),
          _mover(_limiter),
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
        _mover.setupForBucket(bucket, &_source._subDb, targetSubDbId, _handler, _bucketDb);
    }
    bool moveDocuments(size_t maxDocsToMove) {
        return _mover.moveDocuments(maxDocsToMove);
    }
};

TEST("require that initial bucket mover is done")
{
    MyMoveOperationLimiter limiter;
    DocumentBucketMover mover(limiter);
    EXPECT_TRUE(mover.bucketDone());
    mover.moveDocuments(2);
    EXPECT_TRUE(mover.bucketDone());
}

TEST_F("require that we can move all documents", MoveFixture)
{
    f.setupForBucket(f._source.bucket(1), 6, 9);
    EXPECT_TRUE(f.moveDocuments(5));
    EXPECT_TRUE(f._mover.bucketDone());
    EXPECT_EQUAL(5u, f._handler._moves.size());
    EXPECT_EQUAL(5u, f._limiter.beginOpCount);
    for (size_t i = 0; i < 5u; ++i) {
        assertEqual(f._source.bucket(1), f._source.docs(1)[0], 6, 9, f._handler._moves[0]);
    }
}

TEST_F("require that move is stalled if document is pending commit", MoveFixture)
{
    f.setupForBucket(f._source.bucket(1), 6, 9);
    {
        IPendingLidTracker::Token token = f._pendingLidsForCommit.produce(1);
        EXPECT_FALSE(f.moveDocuments(5));
        EXPECT_FALSE(f._mover.bucketDone());
    }
    EXPECT_TRUE(f.moveDocuments(5));
    EXPECT_TRUE(f._mover.bucketDone());
    EXPECT_EQUAL(5u, f._handler._moves.size());
    EXPECT_EQUAL(5u, f._limiter.beginOpCount);
    for (size_t i = 0; i < 5u; ++i) {
        assertEqual(f._source.bucket(1), f._source.docs(1)[0], 6, 9, f._handler._moves[0]);
    }
}

TEST_F("require that bucket is cached when IDocumentMoveHandler handles move operation", MoveFixture)
{
    f.setupForBucket(f._source.bucket(1), 6, 9);
    EXPECT_TRUE(f.moveDocuments(5));
    EXPECT_TRUE(f._mover.bucketDone());
    EXPECT_EQUAL(5u, f._handler._moves.size());
    EXPECT_EQUAL(5u, f._handler._numCachedBuckets);
    EXPECT_FALSE(f._bucketDb.takeGuard()->isCachedBucket(f._source.bucket(1)));
}

TEST_F("require that we can move documents in several steps", MoveFixture)
{
    f.setupForBucket(f._source.bucket(1), 6, 9);
    f.moveDocuments(2);
    EXPECT_FALSE(f._mover.bucketDone());
    EXPECT_EQUAL(2u, f._handler._moves.size());
    assertEqual(f._source.bucket(1), f._source.docs(1)[0], 6, 9, f._handler._moves[0]);
    assertEqual(f._source.bucket(1), f._source.docs(1)[1], 6, 9, f._handler._moves[1]);
    EXPECT_TRUE(f.moveDocuments(2));
    EXPECT_FALSE(f._mover.bucketDone());
    EXPECT_EQUAL(4u, f._handler._moves.size());
    assertEqual(f._source.bucket(1), f._source.docs(1)[2], 6, 9, f._handler._moves[2]);
    assertEqual(f._source.bucket(1), f._source.docs(1)[3], 6, 9, f._handler._moves[3]);
    EXPECT_TRUE(f.moveDocuments(2));
    EXPECT_TRUE(f._mover.bucketDone());
    EXPECT_EQUAL(5u, f._handler._moves.size());
    assertEqual(f._source.bucket(1), f._source.docs(1)[4], 6, 9, f._handler._moves[4]);
    EXPECT_TRUE(f.moveDocuments(2));
    EXPECT_TRUE(f._mover.bucketDone());
    EXPECT_EQUAL(5u, f._handler._moves.size());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
