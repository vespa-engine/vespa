// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <tests/persistence/filestorage/forwardingmessagesender.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/log/log.h>

LOG_SETUP(".persistencequeuetest");

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

class PersistenceQueueTest : public FileStorTestFixture {
public:
    std::shared_ptr<api::StorageMessage> createPut(uint64_t bucket, uint64_t docIdx);
    std::shared_ptr<api::StorageMessage> createGet(uint64_t bucket) const;

    void SetUp() override;

    struct Fixture {
        FileStorTestFixture& parent;
        DummyStorageLink top;
        std::unique_ptr<DummyStorageLink> dummyManager;
        ForwardingMessageSender messageSender;
        FileStorMetrics metrics;
        std::unique_ptr<FileStorHandler> filestorHandler;
        uint32_t stripeId;

        explicit Fixture(FileStorTestFixture& parent);
        ~Fixture();
    };
};

PersistenceQueueTest::Fixture::Fixture(FileStorTestFixture& parent_)
    : parent(parent_),
      top(),
      dummyManager(std::make_unique<DummyStorageLink>()),
      messageSender(*dummyManager),
      metrics(),
      stripeId(0)
{
    top.push_back(std::move(dummyManager));
    top.open();

    metrics.initDiskMetrics(1, 1);

    filestorHandler = std::make_unique<FileStorHandlerImpl>(messageSender, metrics,
                                                            parent._node->getComponentRegister());
    // getNextMessage will time out if no unlocked buckets are present. Choose a timeout
    // that is large enough to fail tests with high probability if this is not the case,
    // and small enough to not slow down testing too much.
    filestorHandler->setGetNextMessageTimeout(20ms);
}

PersistenceQueueTest::Fixture::~Fixture() = default;

void PersistenceQueueTest::SetUp() {
    setupPersistenceThreads(1);
    _node->setPersistenceProvider(std::make_unique<spi::dummy::DummyPersistence>(_node->getTypeRepo()));
}

std::shared_ptr<api::StorageMessage> PersistenceQueueTest::createPut(uint64_t bucket, uint64_t docIdx) {
    std::shared_ptr<document::Document> doc = _node->getTestDocMan().createDocument(
            "foobar", vespalib::make_string("id:foo:testdoctype1:n=%" PRIu64 ":%" PRIu64, bucket, docIdx));
    auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(16, bucket)), doc, 1234);
    cmd->setAddress(makeSelfAddress());
    return cmd;
}

std::shared_ptr<api::StorageMessage> PersistenceQueueTest::createGet(uint64_t bucket) const {
    auto cmd = std::make_shared<api::GetCommand>(
            makeDocumentBucket(document::BucketId(16, bucket)),
            document::DocumentId(vespalib::make_string("id:foo:testdoctype1:n=%" PRIu64 ":0", bucket)), document::AllFields::NAME);
    cmd->setAddress(makeSelfAddress());
    return cmd;
}

TEST_F(PersistenceQueueTest, fetch_next_unlocked_message_if_bucket_locked) {
    Fixture f(*this);
    // Send 2 puts, 2 to the first bucket, 1 to the second. Calling
    // getNextMessage 2 times should then return a lock on the first bucket,
    // then subsequently on the second, skipping the already locked bucket.
    // Puts all have same pri, so order is well defined.
    f.filestorHandler->schedule(createPut(1234, 0));
    f.filestorHandler->schedule(createPut(1234, 1));
    f.filestorHandler->schedule(createPut(5432, 0));

    auto lock0 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock0.first.get());
    EXPECT_EQ(document::BucketId(16, 1234),
              dynamic_cast<api::PutCommand&>(*lock0.second).getBucketId());

    auto lock1 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock1.first.get());
    EXPECT_EQ(document::BucketId(16, 5432),
              dynamic_cast<api::PutCommand&>(*lock1.second).getBucketId());
}

TEST_F(PersistenceQueueTest, shared_locked_operations_allow_concurrent_bucket_access) {
    Fixture f(*this);

    f.filestorHandler->schedule(createGet(1234));
    f.filestorHandler->schedule(createGet(1234));

    auto lock0 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock0.first.get());
    EXPECT_EQ(api::LockingRequirements::Shared, lock0.first->lockingRequirements());

    // Even though we already have a lock on the bucket, Gets allow shared locking and we
    // should therefore be able to get another lock.
    auto lock1 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock1.first.get());
    EXPECT_EQ(api::LockingRequirements::Shared, lock1.first->lockingRequirements());
}

TEST_F(PersistenceQueueTest, exclusive_locked_operation_not_started_if_shared_op_active) {
    Fixture f(*this);

    f.filestorHandler->schedule(createGet(1234));
    f.filestorHandler->schedule(createPut(1234, 0));

    auto lock0 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock0.first.get());
    EXPECT_EQ(api::LockingRequirements::Shared, lock0.first->lockingRequirements());

    // Expected to time out
    auto lock1 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_FALSE(lock1.first.get());
}

TEST_F(PersistenceQueueTest, shared_locked_operation_not_started_if_exclusive_op_active) {
    Fixture f(*this);

    f.filestorHandler->schedule(createPut(1234, 0));
    f.filestorHandler->schedule(createGet(1234));

    auto lock0 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock0.first.get());
    EXPECT_EQ(api::LockingRequirements::Exclusive, lock0.first->lockingRequirements());

    // Expected to time out
    auto lock1 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_FALSE(lock1.first.get());
}

TEST_F(PersistenceQueueTest, exclusive_locked_operation_not_started_if_exclusive_op_active) {
    Fixture f(*this);

    f.filestorHandler->schedule(createPut(1234, 0));
    f.filestorHandler->schedule(createPut(1234, 0));

    auto lock0 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_TRUE(lock0.first.get());
    EXPECT_EQ(api::LockingRequirements::Exclusive, lock0.first->lockingRequirements());

    // Expected to time out
    auto lock1 = f.filestorHandler->getNextMessage(f.stripeId);
    ASSERT_FALSE(lock1.first.get());
}

} // namespace storage
