// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <tests/persistence/filestorage/forwardingmessagesender.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/metrics/updatehook.h>

using document::test::makeDocumentBucket;

namespace storage {

class ActiveOperationsStatsTest : public FileStorTestFixture
{
protected:
    DummyStorageLink top;
    std::unique_ptr<DummyStorageLink> dummyManager;
    ForwardingMessageSender messageSender;
    FileStorMetrics metrics;
    std::unique_ptr<FileStorHandler> filestorHandler;
    uint32_t stripeId;

public:
    ActiveOperationsStatsTest();
    ~ActiveOperationsStatsTest() override;
    std::shared_ptr<api::StorageMessage> createPut(uint64_t bucket, uint64_t docIdx);
    std::shared_ptr<api::StorageMessage> createGet(uint64_t bucket) const;

    void assert_active_operations_stats(const ActiveOperationsStats &stats, uint32_t exp_active_size, uint32_t exp_size_samples, uint32_t exp_latency_samples);
    void update_metrics();
    void test_active_operations_stats();
};

ActiveOperationsStatsTest::ActiveOperationsStatsTest()
    : FileStorTestFixture(),
      top(),
      dummyManager(std::make_unique<DummyStorageLink>()),
      messageSender(*dummyManager),
      metrics(),
      stripeId(0)
{
    setupPersistenceThreads(1);
    _node->setPersistenceProvider(std::make_unique<spi::dummy::DummyPersistence>(_node->getTypeRepo()));
    top.push_back(std::move(dummyManager));
    top.open();
    metrics.initDiskMetrics(1, 1);
    filestorHandler = std::make_unique<FileStorHandlerImpl>(messageSender, metrics,
                                                            _node->getComponentRegister());
    filestorHandler->setGetNextMessageTimeout(20ms);
}

ActiveOperationsStatsTest::~ActiveOperationsStatsTest() = default;

std::shared_ptr<api::StorageMessage>
ActiveOperationsStatsTest::createPut(uint64_t bucket, uint64_t docIdx)
{
    auto doc = _node->getTestDocMan().createDocument(
            "foobar", vespalib::make_string("id:foo:testdoctype1:n=%" PRIu64 ":%" PRIu64, bucket, docIdx));
    auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(16, bucket)), std::move(doc), 1234);
    cmd->setAddress(makeSelfAddress());
    return cmd;
}

std::shared_ptr<api::StorageMessage>
ActiveOperationsStatsTest::createGet(uint64_t bucket) const
{
    auto cmd = std::make_shared<api::GetCommand>(
            makeDocumentBucket(document::BucketId(16, bucket)),
            document::DocumentId(vespalib::make_string("id:foo:testdoctype1:n=%" PRIu64 ":0", bucket)), document::AllFields::NAME);
    cmd->setAddress(makeSelfAddress());
    return cmd;
}

void
ActiveOperationsStatsTest::assert_active_operations_stats(const ActiveOperationsStats &stats, uint32_t exp_active_size, uint32_t exp_size_samples, uint32_t exp_latency_samples)
{
    EXPECT_EQ(exp_active_size, stats.get_active_size());
    EXPECT_EQ(exp_size_samples, stats.get_size_samples());
    EXPECT_EQ(exp_latency_samples, stats.get_latency_samples());
}

void
ActiveOperationsStatsTest::update_metrics()
{
    std::mutex dummy_lock;
    auto &impl = dynamic_cast<FileStorHandlerImpl&>(*filestorHandler);
    auto& hook = impl.get_metric_update_hook_for_testing();
    hook.updateMetrics(metrics::MetricLockGuard(dummy_lock));
}

void
ActiveOperationsStatsTest::test_active_operations_stats()
{
    auto lock0 = filestorHandler->getNextMessage(stripeId);
    auto lock1 = filestorHandler->getNextMessage(stripeId);
    auto lock2 = filestorHandler->getNextMessage(stripeId);
    ASSERT_TRUE(lock0.lock);
    ASSERT_TRUE(lock1.lock);
    ASSERT_FALSE(lock2.lock);
    auto stats = filestorHandler->get_active_operations_stats(false);
    {
        SCOPED_TRACE("during");
        assert_active_operations_stats(stats, 2, 2, 0);
    }
    EXPECT_EQ(3, stats.get_total_size());
    lock0.lock.reset();
    lock1.lock.reset();
    stats = filestorHandler->get_active_operations_stats(false);
    {
        SCOPED_TRACE("after");
        assert_active_operations_stats(stats, 0, 4, 2);
    }
    EXPECT_EQ(4, stats.get_total_size());
    EXPECT_LT(0.0, stats.get_total_latency());
    update_metrics();
    auto &ao_metrics = metrics.active_operations;
    EXPECT_DOUBLE_EQ(1.0, ao_metrics.size.getAverage());
    EXPECT_DOUBLE_EQ(0.0, ao_metrics.size.getMinimum());
    EXPECT_DOUBLE_EQ(2.0, ao_metrics.size.getMaximum());
    EXPECT_DOUBLE_EQ(4.0, ao_metrics.size.getCount());
    EXPECT_LT(0.0, ao_metrics.latency.getAverage());
    EXPECT_LT(0.0, ao_metrics.latency.getMinimum());
    EXPECT_LT(0.0, ao_metrics.latency.getMaximum());
    EXPECT_DOUBLE_EQ(2.0, ao_metrics.latency.getCount());
}

TEST_F(ActiveOperationsStatsTest, empty_stats)
{
    auto stats = filestorHandler->get_active_operations_stats(false);
    assert_active_operations_stats(stats, 0, 0, 0);
}

TEST_F(ActiveOperationsStatsTest, exclusive_lock_active_operations_stats)
{
    filestorHandler->schedule(createPut(1234, 0));
    filestorHandler->schedule(createPut(1234, 1));
    filestorHandler->schedule(createPut(5432, 0));
    test_active_operations_stats();
}

TEST_F(ActiveOperationsStatsTest, shared_lock_active_operations_stats)
{
    filestorHandler->schedule(createGet(1234));
    filestorHandler->schedule(createGet(1234));
    test_active_operations_stats();
}

}
