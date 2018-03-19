// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/bucket.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <tests/persistence/filestorage/forwardingmessagesender.h>
#include <vespa/document/test/make_document_bucket.h>

LOG_SETUP(".persistencequeuetest");

using document::test::makeDocumentBucket;

namespace storage {

class PersistenceQueueTest : public FileStorTestFixture
{
public:
    void testFetchNextUnlockedMessageIfBucketLocked();

    std::shared_ptr<api::StorageMessage>
    createPut(uint64_t bucket, uint64_t docIdx);

    void setUp() override;
    void tearDown() override;

    CPPUNIT_TEST_SUITE(PersistenceQueueTest);
    CPPUNIT_TEST(testFetchNextUnlockedMessageIfBucketLocked);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PersistenceQueueTest);

void
PersistenceQueueTest::setUp()
{
    setupDisks(1);
    _node->setPersistenceProvider(
            spi::PersistenceProvider::UP(
                    new spi::dummy::DummyPersistence(_node->getTypeRepo(), 1)));
}

void
PersistenceQueueTest::tearDown()
{
    _node.reset(0);
}

std::shared_ptr<api::StorageMessage>
PersistenceQueueTest::createPut(uint64_t bucket, uint64_t docIdx)
{
    std::ostringstream id;
    id << "id:foo:testdoctype1:n=" << bucket << ":" << docIdx;
    document::Document::SP doc(
            _node->getTestDocMan().createDocument("foobar", id.str()));
    std::shared_ptr<api::PutCommand> cmd(
            new api::PutCommand(makeDocumentBucket(document::BucketId(16, bucket)), doc, 1234));
    cmd->setAddress(api::StorageMessageAddress(
            "storage", lib::NodeType::STORAGE, 0));
    return cmd;
}

void
PersistenceQueueTest::testFetchNextUnlockedMessageIfBucketLocked()
{
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                          dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(),
                            loadTypes.getMetricLoadTypes(), 1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());

    // Send 2 puts, 2 to the first bucket, 1 to the second. Calling
    // getNextMessage 2 times should then return a lock on the first bucket,
    // then subsequently on the second, skipping the already locked bucket.
    // Puts all have same pri, so order is well defined.
    filestorHandler.schedule(createPut(1234, 0), 0);
    filestorHandler.schedule(createPut(1234, 1), 0);
    filestorHandler.schedule(createPut(5432, 0), 0);

    auto lock0 = filestorHandler.getNextMessage(0);
    CPPUNIT_ASSERT(lock0.first.get());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1234),
                         dynamic_cast<api::PutCommand&>(*lock0.second).getBucketId());

    auto lock1 = filestorHandler.getNextMessage(0);
    CPPUNIT_ASSERT(lock1.first.get());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 5432),
                         dynamic_cast<api::PutCommand&>(*lock1.second).getBucketId());
}

} // namespace storage
