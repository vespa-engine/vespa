// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/bucket.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>

LOG_SETUP(".deletebuckettest");

using document::test::makeDocumentBucket;

namespace storage {

class DeleteBucketTest : public FileStorTestFixture
{
public:
    void testDeleteAbortsOperationsForBucket();

    CPPUNIT_TEST_SUITE(DeleteBucketTest);
    CPPUNIT_TEST(testDeleteAbortsOperationsForBucket);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DeleteBucketTest);

void
DeleteBucketTest::testDeleteAbortsOperationsForBucket()
{
    TestFileStorComponents c(*this, "testDeleteAbortsOperationsForBucket");
    document::BucketId bucket(16, 1);

    createBucket(bucket);
    LOG(info, "TEST STAGE: taking resume guard");
    {
        ResumeGuard rg(c.manager->getFileStorHandler().pause());
        // First put may or may not be queued, since pausing might race with
        // an existing getNextMessage iteration (ugh...).
        c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
        // Put will be queued since thread now must know it's paused.
        c.sendPut(bucket, DocumentIndex(1), PutTimestamp(1000));

        auto deleteMsg = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucket));
        c.top.sendDown(deleteMsg);
        // We should now have two put replies. The first one will either be OK
        // or BUCKET_DELETED depending on whether it raced. The second (which is
        // the one we care about since it's deterministic) must be BUCKET_DELETED.
        // Problem is, their returned ordering is not deterministic so we're left
        // with having to check that _at least_ 1 reply had BUCKET_DELETED. Joy!
        c.top.waitForMessages(2, 60 * 2);
        std::vector <api::StorageMessage::SP> msgs(c.top.getRepliesOnce());
        CPPUNIT_ASSERT_EQUAL(size_t(2), msgs.size());
        int numDeleted = 0;
        for (uint32_t i = 0; i < 2; ++i) {
            api::StorageReply& reply(dynamic_cast<api::StorageReply&>(*msgs[i]));
            if (reply.getResult().getResult() == api::ReturnCode::BUCKET_DELETED) {
                ++numDeleted;
            }
        }
        CPPUNIT_ASSERT(numDeleted >= 1);
        LOG(info, "TEST STAGE: done, releasing resume guard");
    }
    // Ensure we don't shut down persistence threads before DeleteBucket op has completed
    c.top.waitForMessages(1, 60*2);
}

} // namespace storage
