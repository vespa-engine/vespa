// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/bucket.h>
#include <vespa/persistence/spi/test.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>

using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct SanityCheckedDeleteTest : FileStorTestFixture {
    spi::BucketInfo send_put_and_get_bucket_info(TestFileStorComponents &c, const spi::Bucket &spiBucket);
};

TEST_F(SanityCheckedDeleteTest, delete_bucket_fails_when_provider_out_of_sync) {
    TestFileStorComponents c(*this);
    document::BucketId bucket(8, 123);
    document::BucketId syncBucket(8, 234);
    spi::Bucket spiBucket(makeSpiBucket(bucket));

    // Send a put to ensure bucket isn't empty.
    spi::BucketInfo infoBefore(send_put_and_get_bucket_info(c, spiBucket));

    createBucket(syncBucket);

    api::BucketInfo serviceLayerInfo(1, 2, 3, 4, 5, true, false);
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bucket, "foo",
                        StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry->info = serviceLayerInfo;
        entry.write();
    }

    auto cmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucket));
    cmd->setBucketInfo(serviceLayerInfo);

    c.top.sendDown(cmd);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    api::StorageMessage::SP reply(c.top.getReply(0));
    auto& deleteReply = dynamic_cast<api::DeleteBucketReply&>(*reply);
    // Reply happens in a filestor manager context and before the sanity
    // check kicks in, meaning it will always be OK.
    ASSERT_EQ(api::ReturnCode::OK, resultOf(deleteReply));
    // At this point we do not know if the scheduled delete has been
    // executed; it may still be in the persistence queue.
    // Send a put to another bucket to serialize the operation (guaranteed
    // since we only have 1 thread and the delete always has max priority).
    c.sendPut(syncBucket, DocumentIndex(0), PutTimestamp(1001));
    c.top.waitForMessages(2, MSG_WAIT_TIME);
    // Should still be able to get identical bucket info for bucket.
    spi::BucketInfoResult infoResult(
            _node->getPersistenceProvider().getBucketInfo(spiBucket));
    ASSERT_FALSE(infoResult.hasError()) << infoResult.getErrorMessage();
    EXPECT_TRUE(infoBefore == infoResult.getBucketInfo());
}

spi::BucketInfo SanityCheckedDeleteTest::send_put_and_get_bucket_info(
        FileStorTestFixture::TestFileStorComponents& c,
        const spi::Bucket& spiBucket) {
    createBucket(spiBucket.getBucketId());
    c.sendPut(spiBucket.getBucketId(), DocumentIndex(0), PutTimestamp(1000));
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    c.top.getRepliesOnce();
    return _node->getPersistenceProvider().getBucketInfo(spiBucket).getBucketInfo();
}

TEST_F(SanityCheckedDeleteTest, differing_document_sizes_not_considered_out_of_sync) {
    TestFileStorComponents c(*this);
    document::BucketId bucket(8, 123);
    spi::Bucket spiBucket(makeSpiBucket(bucket));

    spi::BucketInfo info_before(send_put_and_get_bucket_info(c, spiBucket));
    // Expect 1 byte of reported size, which will mismatch with the actually put document.
    api::BucketInfo info_with_size_diff(info_before.getChecksum(), info_before.getDocumentCount(), 1u);

    auto delete_cmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucket));
    delete_cmd->setBucketInfo(info_with_size_diff);

    c.top.sendDown(delete_cmd);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    auto reply = c.top.getAndRemoveMessage(api::MessageType::DELETEBUCKET_REPLY);
    auto delete_reply = std::dynamic_pointer_cast<api::DeleteBucketReply>(reply);
    ASSERT_TRUE(delete_reply);
    ASSERT_TRUE(delete_reply->getResult().success());
}

} // namespace storage
