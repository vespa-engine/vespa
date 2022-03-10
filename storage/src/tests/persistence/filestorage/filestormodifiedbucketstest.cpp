// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <vespa/persistence/spi/test.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>

using storage::spi::test::makeSpiBucket;
using namespace ::testing;

namespace storage {

/**
 * Effectively an integration test between the ModifiedBucketChecker storage
 * link and the behavior of the filestor component.
 */
struct FileStorModifiedBucketsTest : FileStorTestFixture {
    void modifyBuckets(uint32_t first, uint32_t count);

    spi::dummy::DummyPersistence& getDummyPersistence() {
        return dynamic_cast<spi::dummy::DummyPersistence&>(_node->getPersistenceProvider());
    }
};

namespace {

struct BucketCheckerInjector : FileStorTestFixture::StorageLinkInjector
{
    TestServiceLayerApp& _node;
    FileStorTestFixture& _fixture;
    BucketCheckerInjector(TestServiceLayerApp& node,
                          FileStorTestFixture& fixture)
        : _node(node),
          _fixture(fixture)
    {}
    void inject(DummyStorageLink& link) const override {
       link.push_back(std::make_unique<ModifiedBucketChecker>(
               _node.getComponentRegister(),
               _node.getPersistenceProvider(),
               config::ConfigUri(_fixture._config->getConfigId())));
    }
};

void
assertIsNotifyCommandWithActiveBucket(api::StorageMessage& msg)
{
    auto& cmd = dynamic_cast<api::NotifyBucketChangeCommand&>(msg);
    ASSERT_TRUE(cmd.getBucketInfo().isActive());
    ASSERT_EQ(
            vespalib::string("StorageMessageAddress(Storage protocol, "
                             "cluster storage, nodetype distributor, index 0)"),
            cmd.getAddress()->toString());
}

}

void
FileStorModifiedBucketsTest::modifyBuckets(uint32_t first, uint32_t count)
{
    spi::BucketIdListResult::List buckets;
    for (uint32_t i = 0; i < count; ++i) {
        buckets.push_back(document::BucketId(16, first + i));
        _node->getPersistenceProvider().setActiveState(
                makeSpiBucket(buckets[i]),
                spi::BucketInfo::ACTIVE);
    }

    getDummyPersistence().setModifiedBuckets(std::move(buckets));
}

TEST_F(FileStorModifiedBucketsTest, modified_buckets_send_notify_bucket_change) {
    BucketCheckerInjector bcj(*_node, *this);
    TestFileStorComponents c(*this, bcj);
    setClusterState("storage:1 distributor:1");

    uint32_t numBuckets = 10;

    for (uint32_t i = 0; i < numBuckets; ++i) {
        document::BucketId bucket(16, i);
        createBucket(makeSpiBucket(bucket));
        c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    }
    c.top.waitForMessages(numBuckets, MSG_WAIT_TIME);
    c.top.reset();

    modifyBuckets(0, numBuckets);
    c.top.waitForMessages(numBuckets, MSG_WAIT_TIME);

    for (uint32_t i = 0; i < 10; ++i) {
        ASSERT_NO_FATAL_FAILURE(assertIsNotifyCommandWithActiveBucket(*c.top.getReply(i)));

        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        document::BucketId(16, i), "foo"));

        EXPECT_TRUE(entry->info.isActive());
    }
}

TEST_F(FileStorModifiedBucketsTest, file_stor_replies_to_recheck_bucket_commands) {
    BucketCheckerInjector bcj(*_node, *this);
    TestFileStorComponents c(*this, bcj);
    setClusterState("storage:1 distributor:1");

    document::BucketId bucket(16, 0);
    createBucket(makeSpiBucket(bucket));
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    c.top.reset();

    modifyBuckets(0, 1);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    ASSERT_NO_FATAL_FAILURE(assertIsNotifyCommandWithActiveBucket(*c.top.getReply(0)));

    // If we don't reply to the recheck bucket commands, we won't trigger
    // a new round of getModifiedBuckets and recheck commands.
    c.top.reset();
    createBucket(makeSpiBucket(document::BucketId(16, 1)));
    modifyBuckets(1, 1);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    ASSERT_NO_FATAL_FAILURE(assertIsNotifyCommandWithActiveBucket(*c.top.getReply(0)));
}

} // storage

