// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/bucket.h>
#include <tests/persistence/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/filestorage/filestortestfixture.h>

namespace storage {

class SanityCheckedDeleteTest : public FileStorTestFixture
{
public:
    void testDeleteBucketFailsWhenProviderOutOfSync();

    CPPUNIT_TEST_SUITE(SanityCheckedDeleteTest);
    CPPUNIT_TEST(testDeleteBucketFailsWhenProviderOutOfSync);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SanityCheckedDeleteTest);

void
SanityCheckedDeleteTest::testDeleteBucketFailsWhenProviderOutOfSync()
{
    TestFileStorComponents c(*this, "testDeleteBucketFailsWhenProviderOutOfSync");
    document::BucketId bucket(8, 123);
    document::BucketId syncBucket(8, 234);
    spi::Bucket spiBucket(bucket, spi::PartitionId(0));

    createBucket(bucket);
    // Send a put to ensure bucket isn't empty.
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    c.top.getRepliesOnce();
    spi::BucketInfo infoBefore(
            _node->getPersistenceProvider()
            .getBucketInfo(spiBucket).getBucketInfo());

    createBucket(syncBucket);

    api::BucketInfo serviceLayerInfo(1, 2, 3, 4, 5, true, false);
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bucket, "foo",
                        StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry->disk = 0;
        entry->info = serviceLayerInfo;
        entry.write();
    }

    std::shared_ptr<api::DeleteBucketCommand> cmd(
            new api::DeleteBucketCommand(bucket));
    cmd->setBucketInfo(serviceLayerInfo);

    c.top.sendDown(cmd);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    api::StorageMessage::SP reply(c.top.getReply(0));
    api::DeleteBucketReply& deleteReply(
            dynamic_cast<api::DeleteBucketReply&>(*reply));
    // Reply happens in a filestor manager context and before the sanity
    // check kicks in, meaning it will always be OK.
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, resultOf(deleteReply));
    // At this point we do not know if the scheduled delete has been
    // executed; it may still be in the persistence queue.
    // Send a put to another bucket to serialize the operation (guaranteed
    // since we only have 1 thread and the delete always has max priority).
    c.sendPut(syncBucket, DocumentIndex(0), PutTimestamp(1001));
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    // Should still be able to get identical bucket info for bucket.
    spi::BucketInfoResult infoResult(
            _node->getPersistenceProvider().getBucketInfo(spiBucket));
    CPPUNIT_ASSERT_MSG(infoResult.getErrorMessage(), !infoResult.hasError());
    CPPUNIT_ASSERT(infoBefore == infoResult.getBucketInfo());
}

} // namespace storage
