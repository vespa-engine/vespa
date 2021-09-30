// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storage/storageserver/bouncer.h>
#include <vespa/storage/storageserver/bouncer_metrics.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct BouncerTest : public Test {
    std::unique_ptr<TestStorageApp> _node;
    std::unique_ptr<DummyStorageLink> _upper;
    Bouncer* _manager;
    DummyStorageLink* _lower;

    BouncerTest();

    void SetUp() override;
    void TearDown() override;

    void setUpAsNode(const lib::NodeType& type);

    using Priority = api::StorageMessage::Priority;

    static constexpr int RejectionDisabledConfigValue = -1;

    // Note: newThreshold is intentionally int (rather than Priority) in order
    // to be able to test out of bounds values.
    void configureRejectionThreshold(int newThreshold);

    std::shared_ptr<api::StorageCommand> createDummyFeedMessage(
            api::Timestamp timestamp,
            Priority priority = 0);

    std::shared_ptr<api::StorageCommand> createDummyFeedMessage(
            api::Timestamp timestamp,
            document::BucketSpace bucketSpace);

    void expectMessageBouncedWithRejection();
    void expectMessageBouncedWithAbort();
    void expectMessageNotBounced();
};

BouncerTest::BouncerTest()
    : _node(),
      _upper(),
      _manager(nullptr),
      _lower(nullptr)
{
}

void BouncerTest::setUpAsNode(const lib::NodeType& type) {
    vdstestlib::DirConfig config(getStandardConfig(type == lib::NodeType::STORAGE));
    if (type == lib::NodeType::STORAGE) {
        _node.reset(new TestServiceLayerApp(NodeIndex(2), config.getConfigId()));
    } else {
        _node.reset(new TestDistributorApp(NodeIndex(2), config.getConfigId()));
    }
    _upper.reset(new DummyStorageLink());
    _manager = new Bouncer(_node->getComponentRegister(), config.getConfigId());
    _lower = new DummyStorageLink();
    _upper->push_back(std::unique_ptr<StorageLink>(_manager));
    _upper->push_back(std::unique_ptr<StorageLink>(_lower));
    _upper->open();
    _node->getClock().setAbsoluteTimeInSeconds(10);
}

void
BouncerTest::SetUp() {
    setUpAsNode(lib::NodeType::STORAGE);
}

void
BouncerTest::TearDown() {
    _manager = nullptr;
    _lower = nullptr;
    if (_upper) {
        _upper->close();
        _upper->flush();
        _upper.reset();
    }
    _node.reset();
}

std::shared_ptr<api::StorageCommand>
BouncerTest::createDummyFeedMessage(api::Timestamp timestamp,
                                    api::StorageMessage::Priority priority)
{
    auto cmd = std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("id:ns:foo::bar"),
            timestamp);
    cmd->setPriority(priority);
    return cmd;
}

std::shared_ptr<api::StorageCommand>
BouncerTest::createDummyFeedMessage(api::Timestamp timestamp,
                                    document::BucketSpace bucketSpace)
{
    auto cmd = std::make_shared<api::RemoveCommand>(
            document::Bucket(bucketSpace, document::BucketId(0)),
            document::DocumentId("id:ns:foo::bar"),
            timestamp);
    cmd->setPriority(Priority(0));
    return cmd;
}

std::shared_ptr<api::StorageCommand> create_dummy_get_message() {
    return std::make_shared<api::GetCommand>(
            document::Bucket(document::FixedBucketSpaces::default_space(), document::BucketId(0)),
            document::DocumentId("id:ns:foo::bar"),
            document::AllFields::NAME);
}

TEST_F(BouncerTest, future_timestamp) {
    EXPECT_EQ(0, _manager->metrics().clock_skew_aborts.getValue());

    // Fail when future timestamps (more than 5 seconds) are received.
    {
        _upper->sendDown(createDummyFeedMessage(16 * 1000000));

        ASSERT_EQ(1, _upper->getNumReplies());
        EXPECT_EQ(0, _upper->getNumCommands());
        EXPECT_EQ(api::ReturnCode::REJECTED,
                  dynamic_cast<api::RemoveReply&>(*_upper->getReply(0)).getResult().getResult());
        _upper->reset();
    }
    EXPECT_EQ(1, _manager->metrics().clock_skew_aborts.getValue());

    // Verify that 1 second clock skew is OK
    {
        _upper->sendDown(createDummyFeedMessage(11 * 1000000));

        EXPECT_EQ(0, _upper->getNumReplies());
        EXPECT_EQ(1, _lower->getNumCommands());
        _lower->reset();
    }

    // Verify that past is OK
    {
        _upper->sendDown(createDummyFeedMessage(5 * 1000000));

        EXPECT_EQ(1, _lower->getNumCommands());
    }

    EXPECT_EQ(1, _manager->metrics().clock_skew_aborts.getValue());
}

TEST_F(BouncerTest, allow_notify_bucket_change_even_when_distributor_down) {
    lib::NodeState state(lib::NodeType::DISTRIBUTOR, lib::State::DOWN);
    _node->getNodeStateUpdater().setReportedNodeState(state);
    // Trigger Bouncer state update
    auto clusterState = std::make_shared<lib::ClusterState>(
            "distributor:3 storage:3");
    _node->getNodeStateUpdater().setClusterState(clusterState);
            
    
    document::BucketId bucket(16, 1234);
    api::BucketInfo info(0x1, 0x2, 0x3);
    auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket), info);
    _upper->sendDown(cmd);

    EXPECT_EQ(0, _upper->getNumReplies());
    EXPECT_EQ(1, _lower->getNumCommands());
}

void
BouncerTest::expectMessageBouncedWithRejection()
{
    ASSERT_EQ(1, _upper->getNumReplies());
    EXPECT_EQ(0, _upper->getNumCommands());
    EXPECT_EQ(api::ReturnCode::REJECTED,
              dynamic_cast<api::RemoveReply&>(*_upper->getReply(0)).getResult().getResult());
    EXPECT_EQ(size_t(0), _lower->getNumCommands());
}

void
BouncerTest::expectMessageBouncedWithAbort()
{
    ASSERT_EQ(1, _upper->getNumReplies());
    EXPECT_EQ(0, _upper->getNumCommands());
    auto& reply = dynamic_cast<api::StorageReply&>(*_upper->getReply(0));
    EXPECT_EQ(api::ReturnCode(api::ReturnCode::ABORTED,
              "We don't allow command of type MessageType(12, Remove) "
              "when node is in state Down (on storage.2)"),
              reply.getResult());
    EXPECT_EQ(0, _lower->getNumCommands());
}

void
BouncerTest::expectMessageNotBounced()
{
    EXPECT_EQ(size_t(0), _upper->getNumReplies());
    EXPECT_EQ(size_t(1), _lower->getNumCommands());
}

void
BouncerTest::configureRejectionThreshold(int newThreshold)
{
    using Builder = vespa::config::content::core::StorBouncerConfigBuilder;
    auto config = std::make_unique<Builder>();
    config->feedRejectionPriorityThreshold = newThreshold;
    _manager->configure(std::move(config));
}

TEST_F(BouncerTest, reject_lower_prioritized_feed_messages_when_configured) {
    configureRejectionThreshold(Priority(120));
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(121)));
    expectMessageBouncedWithRejection();
}

TEST_F(BouncerTest, do_not_reject_higher_prioritized_feed_messages_than_configured) {
    configureRejectionThreshold(Priority(120));
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(119)));
    expectMessageNotBounced();
}

TEST_F(BouncerTest, priority_rejection_threshold_is_exclusive) {
    configureRejectionThreshold(Priority(120));
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(120)));
    expectMessageNotBounced();
}

TEST_F(BouncerTest, only_priority_reject_feed_messages_when_configured) {
    configureRejectionThreshold(RejectionDisabledConfigValue);
    // A message with even the lowest priority should not be rejected.
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(255)));
    expectMessageNotBounced();
}

TEST_F(BouncerTest, priority_rejection_is_disabled_by_default_in_config) {
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(255)));
    expectMessageNotBounced();
}

TEST_F(BouncerTest, read_only_operations_are_not_priority_rejected) {
    configureRejectionThreshold(Priority(1));
    // StatBucket is an external operation, but it's not a mutating operation
    // and should therefore not be blocked.
    auto cmd = std::make_shared<api::StatBucketCommand>(
            makeDocumentBucket(document::BucketId(16, 5)), "");
    cmd->setPriority(Priority(2));
    _upper->sendDown(cmd);
    expectMessageNotBounced();
}

TEST_F(BouncerTest, internal_operations_are_not_rejected) {
    configureRejectionThreshold(Priority(1));
    document::BucketId bucket(16, 1234);
    api::BucketInfo info(0x1, 0x2, 0x3);
    auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket), info);
    cmd->setPriority(Priority(2));
    _upper->sendDown(cmd);
    expectMessageNotBounced();
}

TEST_F(BouncerTest, out_of_bounds_config_values_throw_exception) {
    EXPECT_THROW(configureRejectionThreshold(256), config::InvalidConfigException);
    EXPECT_THROW(configureRejectionThreshold(-2), config::InvalidConfigException);
}


namespace {

std::shared_ptr<const lib::ClusterStateBundle>
makeClusterStateBundle(const vespalib::string &baselineState, const std::map<document::BucketSpace, vespalib::string> &derivedStates)
{
    lib::ClusterStateBundle::BucketSpaceStateMapping derivedBucketSpaceStates;
    for (const auto &entry : derivedStates) {
        derivedBucketSpaceStates[entry.first] = std::make_shared<const lib::ClusterState>(entry.second);
    }
    return std::make_shared<const lib::ClusterStateBundle>(lib::ClusterState(baselineState), std::move(derivedBucketSpaceStates));
}

}

TEST_F(BouncerTest, abort_request_when_derived_bucket_space_node_state_is_marked_down) {
    EXPECT_EQ(0, _manager->metrics().unavailable_node_aborts.getValue());

    auto state = makeClusterStateBundle("distributor:3 storage:3", {{ document::FixedBucketSpaces::default_space(), "distributor:3 storage:3 .2.s:d" }});
    _node->getNodeStateUpdater().setClusterStateBundle(state);
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, document::FixedBucketSpaces::default_space()));
    expectMessageBouncedWithAbort();
    EXPECT_EQ(1, _manager->metrics().unavailable_node_aborts.getValue());

    _upper->reset();
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, document::FixedBucketSpaces::global_space()));
    expectMessageNotBounced();
    EXPECT_EQ(1, _manager->metrics().unavailable_node_aborts.getValue());
}

TEST_F(BouncerTest, client_operations_are_allowed_through_on_cluster_state_down_distributor) {
    TearDown();
    setUpAsNode(lib::NodeType::DISTRIBUTOR);

    // Distributor states never vary across bucket spaces, so not necessary to test with
    // anything except baseline state here.
    auto state = makeClusterStateBundle("distributor:3 .2.s:d storage:3", {});
    _node->getNodeStateUpdater().setClusterStateBundle(state);
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, document::FixedBucketSpaces::default_space()));
    expectMessageNotBounced();
    EXPECT_EQ(0, _manager->metrics().unavailable_node_aborts.getValue());
}

TEST_F(BouncerTest, cluster_state_activation_commands_are_not_bounced) {
    TearDown();
    setUpAsNode(lib::NodeType::DISTRIBUTOR);

    auto state = makeClusterStateBundle("version:10 distributor:3 .2.s:d storage:3", {}); // Our index (2) is down
    _node->getNodeStateUpdater().setClusterStateBundle(state);

    auto activate_cmd = std::make_shared<api::ActivateClusterStateVersionCommand>(11);
    _upper->sendDown(activate_cmd);
    expectMessageNotBounced();
}

TEST_F(BouncerTest, allow_get_operations_when_node_is_in_maintenance_mode) {
    auto state = makeClusterStateBundle("version:10 distributor:3 storage:3 .2.s:m", {}); // Our index is 2
    _node->getNodeStateUpdater().setClusterStateBundle(state);
    _upper->sendDown(create_dummy_get_message());
    expectMessageNotBounced();
    EXPECT_EQ(0, _manager->metrics().unavailable_node_aborts.getValue());
}

namespace {

std::shared_ptr<api::RemoveCommand> make_remove_with_used_bits(uint8_t n_bits) {
    auto id = document::DocumentId("id:ns:foo::bar");
    auto raw_bucket = id.getGlobalId().convertToBucketId();
    return std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId(n_bits, raw_bucket.getRawId())),
            id, api::Timestamp(1000));
}

}

TEST_F(BouncerTest, operation_with_too_few_bucket_bits_is_rejected) {
    auto cmd = make_remove_with_used_bits(spi::BucketLimits::MinUsedBits - 1);
    _upper->sendDown(std::move(cmd));
    expectMessageBouncedWithRejection();
}

TEST_F(BouncerTest, operation_with_sufficient_bucket_bits_is_not_rejected) {
    auto cmd = make_remove_with_used_bits(spi::BucketLimits::MinUsedBits);
    _upper->sendDown(std::move(cmd));
    expectMessageNotBounced();
}

} // storage

