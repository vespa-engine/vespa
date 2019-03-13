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

class PersistenceQueueTest : public FileStorTestFixture {
public:
    void testFetchNextUnlockedMessageIfBucketLocked();
    void shared_locked_operations_allow_concurrent_bucket_access();
    void exclusive_locked_operation_not_started_if_shared_op_active();
    void shared_locked_operation_not_started_if_exclusive_op_active();
    void exclusive_locked_operation_not_started_if_exclusive_op_active();
    void operation_batching_not_allowed_across_different_lock_modes();

    std::shared_ptr<api::StorageMessage> createPut(uint64_t bucket, uint64_t docIdx);
    std::shared_ptr<api::StorageMessage> createGet(uint64_t bucket) const;

    void setUp() override;

    CPPUNIT_TEST_SUITE(PersistenceQueueTest);
    CPPUNIT_TEST(testFetchNextUnlockedMessageIfBucketLocked);
    CPPUNIT_TEST(shared_locked_operations_allow_concurrent_bucket_access);
    CPPUNIT_TEST(exclusive_locked_operation_not_started_if_shared_op_active);
    CPPUNIT_TEST(shared_locked_operation_not_started_if_exclusive_op_active);
    CPPUNIT_TEST(exclusive_locked_operation_not_started_if_exclusive_op_active);
    CPPUNIT_TEST(operation_batching_not_allowed_across_different_lock_modes);
    CPPUNIT_TEST_SUITE_END();

    struct Fixture {
        FileStorTestFixture& parent;
        DummyStorageLink top;
        std::unique_ptr<DummyStorageLink> dummyManager;
        ForwardingMessageSender messageSender;
        documentapi::LoadTypeSet loadTypes;
        FileStorMetrics metrics;
        std::unique_ptr<FileStorHandler> filestorHandler;
        uint32_t stripeId;

        explicit Fixture(FileStorTestFixture& parent);
        ~Fixture();
    };

    static constexpr uint16_t _disk = 0;
};

CPPUNIT_TEST_SUITE_REGISTRATION(PersistenceQueueTest);

PersistenceQueueTest::Fixture::Fixture(FileStorTestFixture& parent_)
    : parent(parent_),
      top(),
      dummyManager(std::make_unique<DummyStorageLink>()),
      messageSender(*dummyManager),
      loadTypes("raw:"),
      metrics(loadTypes.getMetricLoadTypes())
{
    top.push_back(std::move(dummyManager));
    top.open();

    metrics.initDiskMetrics(parent._node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);

    filestorHandler = std::make_unique<FileStorHandler>(messageSender, metrics, parent._node->getPartitions(),
                                                        parent._node->getComponentRegister());
    // getNextMessage will time out if no unlocked buckets are present. Choose a timeout
    // that is large enough to fail tests with high probability if this is not the case,
    // and small enough to not slow down testing too much.
    filestorHandler->setGetNextMessageTimeout(20);

    stripeId = filestorHandler->getNextStripeId(0);
}

PersistenceQueueTest::Fixture::~Fixture() = default;

void PersistenceQueueTest::setUp() {
    setupPersistenceThreads(1);
    _node->setPersistenceProvider(std::make_unique<spi::dummy::DummyPersistence>(_node->getTypeRepo(), 1));
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
            document::DocumentId(vespalib::make_string("id:foo:testdoctype1:n=%" PRIu64 ":0", bucket)), "[all]");
    cmd->setAddress(makeSelfAddress());
    return cmd;
}

void PersistenceQueueTest::testFetchNextUnlockedMessageIfBucketLocked() {
    Fixture f(*this);
    // Send 2 puts, 2 to the first bucket, 1 to the second. Calling
    // getNextMessage 2 times should then return a lock on the first bucket,
    // then subsequently on the second, skipping the already locked bucket.
    // Puts all have same pri, so order is well defined.
    f.filestorHandler->schedule(createPut(1234, 0), _disk);
    f.filestorHandler->schedule(createPut(1234, 1), _disk);
    f.filestorHandler->schedule(createPut(5432, 0), _disk);

    auto lock0 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock0.first.get());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1234),
                         dynamic_cast<api::PutCommand&>(*lock0.second).getBucketId());

    auto lock1 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock1.first.get());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 5432),
                         dynamic_cast<api::PutCommand&>(*lock1.second).getBucketId());
}

void PersistenceQueueTest::shared_locked_operations_allow_concurrent_bucket_access() {
    Fixture f(*this);

    f.filestorHandler->schedule(createGet(1234), _disk);
    f.filestorHandler->schedule(createGet(1234), _disk);

    auto lock0 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock0.first.get());
    CPPUNIT_ASSERT_EQUAL(api::LockingRequirements::Shared, lock0.first->lockingRequirements());

    // Even though we already have a lock on the bucket, Gets allow shared locking and we
    // should therefore be able to get another lock.
    auto lock1 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock1.first.get());
    CPPUNIT_ASSERT_EQUAL(api::LockingRequirements::Shared, lock1.first->lockingRequirements());
}

void PersistenceQueueTest::exclusive_locked_operation_not_started_if_shared_op_active() {
    Fixture f(*this);

    f.filestorHandler->schedule(createGet(1234), _disk);
    f.filestorHandler->schedule(createPut(1234, 0), _disk);

    auto lock0 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock0.first.get());
    CPPUNIT_ASSERT_EQUAL(api::LockingRequirements::Shared, lock0.first->lockingRequirements());

    // Expected to time out
    auto lock1 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(!lock1.first.get());
}

void PersistenceQueueTest::shared_locked_operation_not_started_if_exclusive_op_active() {
    Fixture f(*this);

    f.filestorHandler->schedule(createPut(1234, 0), _disk);
    f.filestorHandler->schedule(createGet(1234), _disk);

    auto lock0 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock0.first.get());
    CPPUNIT_ASSERT_EQUAL(api::LockingRequirements::Exclusive, lock0.first->lockingRequirements());

    // Expected to time out
    auto lock1 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(!lock1.first.get());
}

void PersistenceQueueTest::exclusive_locked_operation_not_started_if_exclusive_op_active() {
    Fixture f(*this);

    f.filestorHandler->schedule(createPut(1234, 0), _disk);
    f.filestorHandler->schedule(createPut(1234, 0), _disk);

    auto lock0 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock0.first.get());
    CPPUNIT_ASSERT_EQUAL(api::LockingRequirements::Exclusive, lock0.first->lockingRequirements());

    // Expected to time out
    auto lock1 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(!lock1.first.get());
}

void PersistenceQueueTest::operation_batching_not_allowed_across_different_lock_modes() {
    Fixture f(*this);

    f.filestorHandler->schedule(createPut(1234, 0), _disk);
    f.filestorHandler->schedule(createGet(1234), _disk);

    auto lock0 = f.filestorHandler->getNextMessage(_disk, f.stripeId);
    CPPUNIT_ASSERT(lock0.first);
    CPPUNIT_ASSERT(lock0.second);
    CPPUNIT_ASSERT_EQUAL(api::LockingRequirements::Exclusive, lock0.first->lockingRequirements());

    f.filestorHandler->getNextMessage(_disk, f.stripeId, lock0);
    CPPUNIT_ASSERT(!lock0.second);
}

} // namespace storage
