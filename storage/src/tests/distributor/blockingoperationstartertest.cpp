// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storage/distributor/blockingoperationstarter.h>
#include <vespa/storage/distributor/distributor_stripe_operation_context.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <tests/distributor/maintenancemocks.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using document::BucketId;
using namespace ::testing;

namespace storage::distributor {

namespace {

const MockOperation& as_mock_operation(const Operation& operation) {
    return dynamic_cast<const MockOperation&>(operation);
}

}

struct FakeDistributorStripeOperationContext : public DistributorStripeOperationContext {

    PendingMessageTracker& _message_tracker;

    explicit FakeDistributorStripeOperationContext(PendingMessageTracker& message_tracker)
        : _message_tracker(message_tracker)
    {}

    ~FakeDistributorStripeOperationContext() override = default;

    // From DistributorOperationContext:
    api::Timestamp generate_unique_timestamp() override {
        abort();
    }
    const DistributorBucketSpaceRepo& bucket_space_repo() const noexcept override {
        abort();
    }
    DistributorBucketSpaceRepo& bucket_space_repo() noexcept override {
        abort();
    }
    const DistributorBucketSpaceRepo& read_only_bucket_space_repo() const noexcept override {
        abort();
    }
    DistributorBucketSpaceRepo& read_only_bucket_space_repo() noexcept override {
        abort();
    }
    const DistributorConfiguration& distributor_config() const noexcept override {
        abort();
    }
    // From DistributorStripeOperationContext:
    void update_bucket_database(const document::Bucket&, const BucketCopy&, uint32_t) override {
        abort();
    }
    void update_bucket_database(const document::Bucket&, const std::vector<BucketCopy>&, uint32_t) override {
        abort();
    }
    void remove_node_from_bucket_database(const document::Bucket&, uint16_t) override {
        abort();
    }
    void remove_nodes_from_bucket_database(const document::Bucket&, const std::vector<uint16_t>&) override {
        abort();
    }
    document::BucketId make_split_bit_constrained_bucket_id(const document::DocumentId&) const override {
        abort();
    }
    void recheck_bucket_info(uint16_t, const document::Bucket&) override {
        abort();
    }
    document::BucketId get_sibling(const document::BucketId&) const override {
        abort();
    }
    void send_inline_split_if_bucket_too_large(document::BucketSpace, const BucketDatabase::Entry&, uint8_t) override {
        abort();
    }
    OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket&) const override {
        abort();
    }
    PendingMessageTracker& pending_message_tracker() noexcept override {
        return _message_tracker;
    }
    const PendingMessageTracker& pending_message_tracker() const noexcept override {
        return _message_tracker;
    }
    bool has_pending_message(uint16_t, const document::Bucket&, uint32_t) const override {
        abort();
    }
    const lib::ClusterState* pending_cluster_state_or_null(const document::BucketSpace&) const override {
        abort();
    }
    const lib::ClusterStateBundle& cluster_state_bundle() const override {
        abort();
    }
    bool storage_node_is_up(document::BucketSpace, uint32_t) const override {
        abort();
    }
    const BucketGcTimeCalculator::BucketIdHasher& bucket_id_hasher() const override {
        abort();
    }
    const NodeSupportedFeaturesRepo& node_supported_features_repo() const noexcept override {
        abort();
    }
};

struct BlockingOperationStarterTest : Test {
    std::shared_ptr<Operation> createMockOperation() {
        return std::make_shared<MockOperation>(makeDocumentBucket(BucketId(16, 1)));
    }
    std::shared_ptr<Operation> createBlockingMockOperation() {
        auto op = std::make_shared<MockOperation>(makeDocumentBucket(BucketId(16, 1)));
        op->setShouldBlock(true);
        return op;
    }

    framework::defaultimplementation::FakeClock _clock;
    std::unique_ptr<MockOperationStarter> _starterImpl;
    std::unique_ptr<StorageComponentRegisterImpl> _compReg;
    std::unique_ptr<PendingMessageTracker> _messageTracker;
    std::unique_ptr<FakeDistributorStripeOperationContext> _fake_ctx;
    std::unique_ptr<OperationSequencer> _operation_sequencer;
    std::unique_ptr<BlockingOperationStarter> _operationStarter;

    void SetUp() override;
};

void
BlockingOperationStarterTest::SetUp()
{
    _starterImpl = std::make_unique<MockOperationStarter>();
    _compReg = std::make_unique<StorageComponentRegisterImpl>();
    _compReg->setClock(_clock);
    _clock.setAbsoluteTimeInSeconds(1);
    _messageTracker = std::make_unique<PendingMessageTracker>(*_compReg, 0);
    _fake_ctx = std::make_unique<FakeDistributorStripeOperationContext>(*_messageTracker);
    _operation_sequencer = std::make_unique<OperationSequencer>();
    _operationStarter = std::make_unique<BlockingOperationStarter>(*_fake_ctx, *_operation_sequencer, *_starterImpl);
}

TEST_F(BlockingOperationStarterTest, operation_not_blocked_when_no_messages_pending) {
    auto operation = createMockOperation();
    ASSERT_TRUE(_operationStarter->start(operation, OperationStarter::Priority(0)));
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri 0\n",
              _starterImpl->toString());
    EXPECT_FALSE(as_mock_operation(*operation).get_was_blocked());
}

TEST_F(BlockingOperationStarterTest, operation_blocked_when_messages_pending) {
    // start should return true but not forward message to underlying starter.
    auto operation = createBlockingMockOperation();
    ASSERT_TRUE(_operationStarter->start(operation, OperationStarter::Priority(0)));
    EXPECT_EQ("", _starterImpl->toString());
    EXPECT_TRUE(as_mock_operation(*operation).get_was_blocked());
}

}
