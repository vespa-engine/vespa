// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_common.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/vespalib/gtest/gtest.h>

struct HandlerTest : public ::testing::Test {
    DocBuilder _docBuilder;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    MyDocumentStore _docStore;
    MySubDb _subDb;
    LidSpaceCompactionHandler _handler;
    HandlerTest();
    ~HandlerTest();
};

HandlerTest::HandlerTest()
    : _docBuilder(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _docStore(),
      _subDb(_bucketDB, _docStore, _docBuilder.get_repo_sp()),
      _handler(_subDb.maintenance_sub_db, "test")
{
    _docStore._readDoc = _docBuilder.make_document(DOC_ID);
}

HandlerTest::~HandlerTest() = default;

TEST_F(HandlerTest, handler_uses_doctype_and_subdb_name)
{
    EXPECT_EQ("test.dummysubdb", _handler.getName());
}

TEST_F(HandlerTest, createMoveOperation_works_as_expected)
{
    const uint32_t moveToLid = 5;
    const uint32_t moveFromLid = 10;
    const BucketId bucketId(100);
    const Timestamp timestamp(200);
    DocumentMetaData document(moveFromLid, timestamp, bucketId, GlobalId());
    {
        EXPECT_FALSE(_subDb.maintenance_sub_db.lidNeedsCommit(moveFromLid));
        IPendingLidTracker::Token token = _subDb._pendingLidsForCommit.produce(moveFromLid);
        EXPECT_TRUE(_subDb.maintenance_sub_db.lidNeedsCommit(moveFromLid));
        MoveOperation::UP op = _handler.createMoveOperation(document, moveToLid);
        ASSERT_FALSE(op);
    }
    EXPECT_FALSE(_subDb.maintenance_sub_db.lidNeedsCommit(moveFromLid));
    MoveOperation::UP op = _handler.createMoveOperation(document, moveToLid);
    ASSERT_TRUE(op);
    EXPECT_EQ(10u, _docStore._readLid);
    EXPECT_EQ(DbDocumentId(SUBDB_ID, moveFromLid).toString(),
              op->getPrevDbDocumentId().toString()); // source
    EXPECT_EQ(DbDocumentId(SUBDB_ID, moveToLid).toString(),
              op->getDbDocumentId().toString()); // target
    EXPECT_EQ(DocumentId(DOC_ID), op->getDocument()->getId());
    EXPECT_EQ(bucketId, op->getBucketId());
    EXPECT_EQ(timestamp, op->getTimestamp());
}

GTEST_MAIN_RUN_ALL_TESTS()
