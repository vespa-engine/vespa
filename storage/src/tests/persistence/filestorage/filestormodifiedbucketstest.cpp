// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <memory>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>

namespace storage {

/**
 * Effectively an integration test between the ModifiedBucketChecker storage
 * link and the behavior of the filestor component.
 */
class FileStorModifiedBucketsTest : public FileStorTestFixture
{
public:
    void modifiedBucketsSendNotifyBucketChange();
    void fileStorRepliesToRecheckBucketCommands();
    
    void modifyBuckets(uint32_t first, uint32_t count);

    spi::dummy::DummyPersistence& getDummyPersistence() {
        return dynamic_cast<spi::dummy::DummyPersistence&>(_node->getPersistenceProvider());
    }

    CPPUNIT_TEST_SUITE(FileStorModifiedBucketsTest);
    CPPUNIT_TEST(modifiedBucketsSendNotifyBucketChange);
    CPPUNIT_TEST(fileStorRepliesToRecheckBucketCommands);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(FileStorModifiedBucketsTest);

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
       link.push_back(std::unique_ptr<ModifiedBucketChecker>(
            new ModifiedBucketChecker(_node.getComponentRegister(),
                                      _node.getPersistenceProvider(),
                                      _fixture._config->getConfigId())));
    }
};

void
assertIsNotifyCommandWithActiveBucket(api::StorageMessage& msg)
{
    api::NotifyBucketChangeCommand& cmd(
            dynamic_cast<api::NotifyBucketChangeCommand&>(msg));
    CPPUNIT_ASSERT(cmd.getBucketInfo().isActive());
    CPPUNIT_ASSERT_EQUAL(
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
                spi::Bucket(buckets[i], spi::PartitionId(0)),
                spi::BucketInfo::ACTIVE);
    }

    getDummyPersistence().setModifiedBuckets(buckets); 
}

void
FileStorModifiedBucketsTest::modifiedBucketsSendNotifyBucketChange()
{
    BucketCheckerInjector bcj(*_node, *this);
    TestFileStorComponents c(*this, "modifiedBucketsSendNotifyBucketChange", bcj);
    setClusterState("storage:1 distributor:1");

    uint32_t numBuckets = 10;

    for (uint32_t i = 0; i < numBuckets; ++i) {
        document::BucketId bucket(16, i);
        createBucket(spi::Bucket(bucket, spi::PartitionId(0)));
        c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    }
    c.top.waitForMessages(numBuckets, MSG_WAIT_TIME);
    c.top.reset();

    modifyBuckets(0, numBuckets);
    c.top.waitForMessages(numBuckets, MSG_WAIT_TIME);

    for (uint32_t i = 0; i < 10; ++i) {
        assertIsNotifyCommandWithActiveBucket(*c.top.getReply(i));

        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        document::BucketId(16, i), "foo"));

        CPPUNIT_ASSERT(entry->info.isActive());
    }
}

void
FileStorModifiedBucketsTest::fileStorRepliesToRecheckBucketCommands()
{
    BucketCheckerInjector bcj(*_node, *this);
    TestFileStorComponents c(*this, "fileStorRepliesToRecheckBucketCommands", bcj);
    setClusterState("storage:1 distributor:1");

    document::BucketId bucket(16, 0);
    createBucket(spi::Bucket(bucket, spi::PartitionId(0)));
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    c.top.reset();

    modifyBuckets(0, 1);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    assertIsNotifyCommandWithActiveBucket(*c.top.getReply(0));

    // If we don't reply to the recheck bucket commands, we won't trigger
    // a new round of getModifiedBuckets and recheck commands.
    c.top.reset();
    createBucket(spi::Bucket(document::BucketId(16, 1), spi::PartitionId(0)));
    modifyBuckets(1, 1);
    c.top.waitForMessages(1, MSG_WAIT_TIME);
    assertIsNotifyCommandWithActiveBucket(*c.top.getReply(0));
}

} // storage

