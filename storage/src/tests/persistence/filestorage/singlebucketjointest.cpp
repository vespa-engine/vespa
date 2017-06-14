// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>

LOG_SETUP(".singlebucketjointest");

namespace storage {

class SingleBucketJoinTest : public FileStorTestFixture
{
public:
    void testPersistenceCanHandleSingleBucketJoin();

    CPPUNIT_TEST_SUITE(SingleBucketJoinTest);
    CPPUNIT_TEST(testPersistenceCanHandleSingleBucketJoin);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SingleBucketJoinTest);

void
SingleBucketJoinTest::testPersistenceCanHandleSingleBucketJoin()
{
    TestFileStorComponents c(*this, "testPersistenceCanHandleSingleBucketJoin");
    document::BucketId targetBucket(16, 1);
    document::BucketId sourceBucket(17, 1);

    createBucket(sourceBucket);
    // Make sure it's not empty
    c.sendPut(sourceBucket, DocumentIndex(0), PutTimestamp(1000));
    expectOkReply<api::PutReply>(c.top);
    c.top.getRepliesOnce();

    auto cmd = std::make_shared<api::JoinBucketsCommand>(targetBucket);
    cmd->getSourceBuckets().push_back(sourceBucket);
    cmd->getSourceBuckets().push_back(sourceBucket);

    c.top.sendDown(cmd);
    // If single bucket join locking is not working properly, this
    // will hang forever.
    expectOkReply<api::JoinBucketsReply>(c.top);
}

} // namespace storage
