// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
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
#include <vespa/storageapi/message/persistence.h>
#include <vespa/config/common/exceptions.h>

using document::test::makeDocumentBucket;

namespace storage {

struct BouncerTest : public CppUnit::TestFixture {
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _upper;
    Bouncer* _manager;
    DummyStorageLink* _lower;

    BouncerTest();

    void setUp() override;
    void tearDown() override;

    void testFutureTimestamp();
    void testAllowNotifyBucketChangeEvenWhenDistributorDown();
    void rejectLowerPrioritizedFeedMessagesWhenConfigured();
    void doNotRejectHigherPrioritizedFeedMessagesThanConfigured();
    void rejectionThresholdIsExclusive();
    void onlyRejectFeedMessagesWhenConfigured();
    void rejectionIsDisabledByDefaultInConfig();
    void readOnlyOperationsAreNotRejected();
    void internalOperationsAreNotRejected();
    void outOfBoundsConfigValuesThrowException();
    void abort_request_when_derived_bucket_space_node_state_is_marked_down();

    CPPUNIT_TEST_SUITE(BouncerTest);
    CPPUNIT_TEST(testFutureTimestamp);
    CPPUNIT_TEST(testAllowNotifyBucketChangeEvenWhenDistributorDown);
    CPPUNIT_TEST(rejectLowerPrioritizedFeedMessagesWhenConfigured);
    CPPUNIT_TEST(doNotRejectHigherPrioritizedFeedMessagesThanConfigured);
    CPPUNIT_TEST(rejectionThresholdIsExclusive);
    CPPUNIT_TEST(onlyRejectFeedMessagesWhenConfigured);
    CPPUNIT_TEST(rejectionIsDisabledByDefaultInConfig);
    CPPUNIT_TEST(readOnlyOperationsAreNotRejected);
    CPPUNIT_TEST(internalOperationsAreNotRejected);
    CPPUNIT_TEST(outOfBoundsConfigValuesThrowException);
    CPPUNIT_TEST(abort_request_when_derived_bucket_space_node_state_is_marked_down);
    CPPUNIT_TEST_SUITE_END();

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

    void assertMessageBouncedWithRejection();
    void assertMessageBouncedWithAbort();
    void assertMessageNotBounced();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BouncerTest);

BouncerTest::BouncerTest()
    : _node(),
      _upper(),
      _manager(0),
      _lower(0)
{
}

void
BouncerTest::setUp() {
    try{
        vdstestlib::DirConfig config(getStandardConfig(true));
        _node.reset(new TestServiceLayerApp(
                DiskCount(1), NodeIndex(2), config.getConfigId()));
        _upper.reset(new DummyStorageLink());
        _manager = new Bouncer(_node->getComponentRegister(),
                               config.getConfigId());
        _lower = new DummyStorageLink();
        _upper->push_back(std::unique_ptr<StorageLink>(_manager));
        _upper->push_back(std::unique_ptr<StorageLink>(_lower));
        _upper->open();
    } catch (std::exception& e) {
        std::cerr << "Failed to static initialize objects: " << e.what()
                  << "\n";
    }
    _node->getClock().setAbsoluteTimeInSeconds(10);
}

void
BouncerTest::tearDown() {
    _manager = 0;
    _lower = 0;
    _upper->close();
    _upper->flush();
    _upper.reset(0);
    _node.reset(0);
}

std::shared_ptr<api::StorageCommand>
BouncerTest::createDummyFeedMessage(api::Timestamp timestamp,
                                    api::StorageMessage::Priority priority)
{
    auto cmd = std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("doc:foo:bar"),
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
            document::DocumentId("doc:foo:bar"),
            timestamp);
    cmd->setPriority(Priority(0));
    return cmd;
}

void
BouncerTest::testFutureTimestamp()
{
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), _manager->metrics().clock_skew_aborts.getValue());

    // Fail when future timestamps (more than 5 seconds) are received.
    {
        _upper->sendDown(createDummyFeedMessage(16 * 1000000));

        CPPUNIT_ASSERT_EQUAL(1, (int)_upper->getNumReplies());
        CPPUNIT_ASSERT_EQUAL(0, (int)_upper->getNumCommands());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode::REJECTED,
                             static_cast<api::RemoveReply&>(*_upper->getReply(0)).
                             getResult().getResult());
        _upper->reset();
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), _manager->metrics().clock_skew_aborts.getValue());

    // Verify that 1 second clock skew is OK
    {
        _upper->sendDown(createDummyFeedMessage(11 * 1000000));

        CPPUNIT_ASSERT_EQUAL(0, (int)_upper->getNumReplies());
        CPPUNIT_ASSERT_EQUAL(1, (int)_lower->getNumCommands());
        _lower->reset();
    }

    // Verify that past is OK
    {
        _upper->sendDown(createDummyFeedMessage(5 * 1000000));

        CPPUNIT_ASSERT_EQUAL(1, (int)_lower->getNumCommands());
    }

    CPPUNIT_ASSERT_EQUAL(uint64_t(1), _manager->metrics().clock_skew_aborts.getValue());
}

void
BouncerTest::testAllowNotifyBucketChangeEvenWhenDistributorDown()
{
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

    CPPUNIT_ASSERT_EQUAL(size_t(0), _upper->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(size_t(1), _lower->getNumCommands());
}

void
BouncerTest::assertMessageBouncedWithRejection()
{
    CPPUNIT_ASSERT_EQUAL(size_t(1), _upper->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(size_t(0), _upper->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::REJECTED,
            static_cast<api::RemoveReply&>(*_upper->getReply(0)).
            getResult().getResult());
    CPPUNIT_ASSERT_EQUAL(size_t(0), _lower->getNumCommands());
}

void
BouncerTest::assertMessageBouncedWithAbort()
{
    CPPUNIT_ASSERT_EQUAL(size_t(1), _upper->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(size_t(0), _upper->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED,
            static_cast<api::RemoveReply&>(*_upper->getReply(0)).
            getResult().getResult());
    CPPUNIT_ASSERT_EQUAL(size_t(0), _lower->getNumCommands());
}

void
BouncerTest::assertMessageNotBounced()
{
    CPPUNIT_ASSERT_EQUAL(size_t(0), _upper->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(size_t(1), _lower->getNumCommands());
}

void
BouncerTest::configureRejectionThreshold(int newThreshold)
{
    using Builder = vespa::config::content::core::StorBouncerConfigBuilder;
    auto config = std::make_unique<Builder>();
    config->feedRejectionPriorityThreshold = newThreshold;
    _manager->configure(std::move(config));
}

void
BouncerTest::rejectLowerPrioritizedFeedMessagesWhenConfigured()
{
    configureRejectionThreshold(Priority(120));
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(121)));
    assertMessageBouncedWithRejection();
}

void
BouncerTest::doNotRejectHigherPrioritizedFeedMessagesThanConfigured()
{
    configureRejectionThreshold(Priority(120));
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(119)));
    assertMessageNotBounced();
}

void
BouncerTest::rejectionThresholdIsExclusive()
{
    configureRejectionThreshold(Priority(120));
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(120)));
    assertMessageNotBounced();
}

void
BouncerTest::onlyRejectFeedMessagesWhenConfigured()
{
    configureRejectionThreshold(RejectionDisabledConfigValue);
    // A message with even the lowest priority should not be rejected.
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(255)));
    assertMessageNotBounced();
}

void
BouncerTest::rejectionIsDisabledByDefaultInConfig()
{
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, Priority(255)));
    assertMessageNotBounced();
}

void
BouncerTest::readOnlyOperationsAreNotRejected()
{
    configureRejectionThreshold(Priority(1));
    // StatBucket is an external operation, but it's not a mutating operation
    // and should therefore not be blocked.
    auto cmd = std::make_shared<api::StatBucketCommand>(
            makeDocumentBucket(document::BucketId(16, 5)), "");
    cmd->setPriority(Priority(2));
    _upper->sendDown(cmd);
    assertMessageNotBounced();
}

void
BouncerTest::internalOperationsAreNotRejected()
{
    configureRejectionThreshold(Priority(1));
    document::BucketId bucket(16, 1234);
    api::BucketInfo info(0x1, 0x2, 0x3);
    auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket), info);
    cmd->setPriority(Priority(2));
    _upper->sendDown(cmd);
    assertMessageNotBounced();
}

void
BouncerTest::outOfBoundsConfigValuesThrowException()
{
    try {
        configureRejectionThreshold(256);
        CPPUNIT_FAIL("Upper bound violation not caught");
    } catch (config::InvalidConfigException &) {}

    try {
        configureRejectionThreshold(-2);
        CPPUNIT_FAIL("Lower bound violation not caught");
    } catch (config::InvalidConfigException &) {}
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

void
BouncerTest::abort_request_when_derived_bucket_space_node_state_is_marked_down()
{
    auto state = makeClusterStateBundle("distributor:3 storage:3", {{ document::FixedBucketSpaces::default_space(), "distributor:3 storage:3 .2.s:d" }});
    _node->getNodeStateUpdater().setClusterStateBundle(state);
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, document::FixedBucketSpaces::default_space()));
    assertMessageBouncedWithAbort();
    _upper->reset();
    _upper->sendDown(createDummyFeedMessage(11 * 1000000, document::FixedBucketSpaces::global_space()));
    assertMessageNotBounced();
}

} // storage

