// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/bucket_spaces_stats_provider.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <gmock/gmock.h>

using document::Bucket;
using document::BucketId;
using document::BucketSpace;
using document::FixedBucketSpaces;
using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

/**
 * This was copied from LegacyDistributorTest and adjusted to work with one distributor stripe.
 */
struct DistributorStripeTest : Test, DistributorStripeTestUtil {
    DistributorStripeTest();
    ~DistributorStripeTest() override;

    std::vector<document::BucketSpace> _bucketSpaces;

    void SetUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
    };

    void TearDown() override {
        close();
    }

    // Simple type aliases to make interfacing with certain utility functions
    // easier. Note that this is only for readability and does not provide any
    // added type safety.
    using NodeCount = int;
    using Redundancy = int;

    using ConfigBuilder = vespa::config::content::core::StorDistributormanagerConfigBuilder;

    auto currentReplicaCountingMode() const noexcept {
        return _stripe->_bucketDBMetricUpdater.getMinimumReplicaCountingMode();
    }

    std::string testOp(std::shared_ptr<api::StorageMessage> msg) {
        _stripe->handleMessage(msg);

        std::string tmp = _sender.getCommands();
        _sender.clear();
        return tmp;
    }

    void tickDistributorNTimes(uint32_t n) {
        for (uint32_t i = 0; i < n; ++i) {
            tick();
        }
    }

    using ResetTrusted = bool;

    std::string updateBucketDB(const std::string& firstState,
                               const std::string& secondState,
                               bool resetTrusted = false) {
        std::vector<std::string> states(toVector<std::string>(firstState, secondState));

        for (uint32_t i = 0; i < states.size(); ++i) {
            std::vector<uint16_t> removedNodes;
            std::vector<BucketCopy> changedNodes;

            vespalib::StringTokenizer tokenizer(states[i], ",");
            for (uint32_t j = 0; j < tokenizer.size(); ++j) {
                vespalib::StringTokenizer tokenizer2(tokenizer[j], ":");

                bool trusted = false;
                if (tokenizer2.size() > 2) {
                    trusted = true;
                }

                uint16_t node = atoi(tokenizer2[0].data());
                if (tokenizer2[1] == "r") {
                    removedNodes.push_back(node);
                } else {
                    uint32_t checksum = atoi(tokenizer2[1].data());
                    changedNodes.push_back(
                            BucketCopy(
                                    i + 1,
                                    node,
                                    api::BucketInfo(
                                            checksum,
                                            checksum / 2,
                                            checksum / 4)).setTrusted(trusted));
                }
            }

            operation_context().remove_nodes_from_bucket_database(makeDocumentBucket(document::BucketId(16, 1)), removedNodes);

            uint32_t flags(DatabaseUpdate::CREATE_IF_NONEXISTING
                           | (resetTrusted ? DatabaseUpdate::RESET_TRUSTED : 0));

            operation_context().update_bucket_database(makeDocumentBucket(document::BucketId(16, 1)),
                                                       changedNodes,
                                                       flags);
        }

        std::string retVal = dumpBucket(document::BucketId(16, 1));
        getBucketDatabase().clear();
        return retVal;
    }

    void assertBucketSpaceStats(size_t expBucketPending, size_t expBucketTotal, uint16_t node, const vespalib::string& bucketSpace,
                                const BucketSpacesStatsProvider::PerNodeBucketSpacesStats& stats);

    SimpleMaintenanceScanner::PendingMaintenanceStats stripe_maintenance_stats() {
        return _stripe->pending_maintenance_stats();
    }

    BucketSpacesStatsProvider::PerNodeBucketSpacesStats stripe_bucket_spaces_stats() {
        return _stripe->getBucketSpacesStats();
    }

    bool stripe_handle_message(const std::shared_ptr<api::StorageMessage>& msg) {
        // TODO: Avoid using private DistributorStripe functions
        return _stripe->handleMessage(msg);
    }

    void configure_stale_reads_enabled(bool enabled) {
        ConfigBuilder builder;
        builder.allowStaleReadsDuringClusterStateTransitions = enabled;
        configure_stripe(builder);
    }

    void configure_update_fast_path_restart_enabled(bool enabled) {
        ConfigBuilder builder;
        builder.restartWithFastUpdatePathIfAllGetTimestampsAreConsistent = enabled;
        configure_stripe(builder);
    }

    void configure_merge_operations_disabled(bool disabled) {
        ConfigBuilder builder;
        builder.mergeOperationsDisabled = disabled;
        configure_stripe(builder);
    }

    void configure_use_weak_internal_read_consistency(bool use_weak) {
        ConfigBuilder builder;
        builder.useWeakInternalReadConsistencyForClientGets = use_weak;
        configure_stripe(builder);
    }

    void configure_metadata_update_phase_enabled(bool enabled) {
        ConfigBuilder builder;
        builder.enableMetadataOnlyFetchPhaseForInconsistentUpdates = enabled;
        configure_stripe(builder);
    }

    void configure_prioritize_global_bucket_merges(bool enabled) {
        ConfigBuilder builder;
        builder.prioritizeGlobalBucketMerges = enabled;
        configure_stripe(builder);
    }

    void configure_max_activation_inhibited_out_of_sync_groups(uint32_t n_groups) {
        ConfigBuilder builder;
        builder.maxActivationInhibitedOutOfSyncGroups = n_groups;
        configure_stripe(builder);
    }

    void configure_implicitly_clear_priority_on_schedule(bool implicitly_clear) {
        ConfigBuilder builder;
        builder.implicitlyClearBucketPriorityOnSchedule = implicitly_clear;
        configure_stripe(builder);
    }

    void configure_use_unordered_merge_chaining(bool use_unordered) {
        ConfigBuilder builder;
        builder.useUnorderedMergeChaining = use_unordered;
        configure_stripe(builder);
    }

    void configure_enable_two_phase_garbage_collection(bool use_two_phase) {
        ConfigBuilder builder;
        builder.enableTwoPhaseGarbageCollection = use_two_phase;
        configure_stripe(builder);
    }

    bool scheduler_has_implicitly_clear_priority_on_schedule_set() const noexcept {
        return _stripe->_scheduler->implicitly_clear_priority_on_schedule();
    }

    void configureMaxClusterClockSkew(int seconds);
    void configure_mutation_sequencing(bool enabled);
    void configure_merge_busy_inhibit_duration(int seconds);

    void set_up_and_start_get_op_with_stale_reads_enabled(bool enabled);

};

DistributorStripeTest::DistributorStripeTest()
    : Test(),
      DistributorStripeTestUtil(),
      _bucketSpaces()
{
}

DistributorStripeTest::~DistributorStripeTest() = default;

TEST_F(DistributorStripeTest, operation_generation)
{
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    document::BucketId bid;
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    EXPECT_EQ("Remove", testOp(std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(bid),
            document::DocumentId("id:m:test:n=1:foo"),
            api::Timestamp(1234))));

    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "foo", "bar", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 1));
    cmd->addBucketToBeVisited(document::BucketId());

    EXPECT_EQ("Visitor Create", testOp(cmd));
}

TEST_F(DistributorStripeTest, operations_generated_and_started_without_duplicates)
{
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }

    tickDistributorNTimes(20);

    ASSERT_FALSE(tick());

    ASSERT_EQ(6, _sender.commands().size());
}

TEST_F(DistributorStripeTest, maintenance_scheduling_inhibited_if_cluster_state_is_pending)
{
    setup_stripe(Redundancy(2), NodeCount(4), "storage:3 distributor:1");
    simulate_set_pending_cluster_state("storage:4 distributor:1");

    _sender.commands().clear(); // Remove pending bucket info requests

    tickDistributorNTimes(1);
    EXPECT_FALSE(stripe_is_in_recovery_mode());

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=2"); // Needs activation, merging
    }
    tickDistributorNTimes(10);

    // No ops should have been actually generated
    ASSERT_EQ(0, _sender.commands().size());
}

TEST_F(DistributorStripeTest, non_activation_maintenance_inhibited_if_explicitly_toggled)
{
    setup_stripe(Redundancy(2), NodeCount(4), "storage:3 distributor:1");
    tickDistributorNTimes(1);
    ASSERT_FALSE(stripe_is_in_recovery_mode());

    for (uint32_t i = 0; i < 3; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=2/3/4/t/a"); // Needs merging, but not activation (already active)
    }
    _stripe->inhibit_non_activation_maintenance_operations(true);
    tickDistributorNTimes(10);

    // No ops should have been actually generated
    ASSERT_EQ("", _sender.getCommands());
}

TEST_F(DistributorStripeTest, activation_maintenance_not_inhibited_even_if_explicitly_toggled)
{
    setup_stripe(Redundancy(2), NodeCount(4), "storage:3 distributor:1");
    tickDistributorNTimes(1);
    ASSERT_FALSE(stripe_is_in_recovery_mode());

    for (uint32_t i = 0; i < 3; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=2/3/4"); // Needs activation and merging
    }
    _stripe->inhibit_non_activation_maintenance_operations(true);
    tickDistributorNTimes(10);

    ASSERT_EQ("SetBucketState,SetBucketState,SetBucketState", _sender.getCommands());
}

TEST_F(DistributorStripeTest, recovery_mode_on_cluster_state_change)
{
    setup_stripe(Redundancy(1), NodeCount(2),
                 "storage:1 .0.s:d distributor:1");
    enable_cluster_state("storage:1 distributor:1");

    EXPECT_TRUE(stripe_is_in_recovery_mode());
    for (uint32_t i = 0; i < 3; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }
    for (int i = 0; i < 3; ++i) {
        tick();
        EXPECT_TRUE(stripe_is_in_recovery_mode());
    }
    tick();
    EXPECT_FALSE(stripe_is_in_recovery_mode());

    enable_cluster_state("storage:2 distributor:1");
    EXPECT_TRUE(stripe_is_in_recovery_mode());
}

// TODO STRIPE how to throttle across stripes?
TEST_F(DistributorStripeTest, operations_are_throttled)
{
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    auto config = make_config();
    config->setMinPendingMaintenanceOps(1);
    config->setMaxPendingMaintenanceOps(1);
    configure_stripe(config);

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }
    tickDistributorNTimes(20);
    ASSERT_EQ(1, _sender.commands().size());
}

TEST_F(DistributorStripeTest, handle_unknown_maintenance_reply)
{
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    {
        auto cmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(document::BucketId(16, 1234)));
        auto reply = std::make_shared<api::SplitBucketReply>(*cmd);
        ASSERT_TRUE(_stripe->handleReply(reply));
    }

    {
        // RemoveLocationReply must be treated as a maintenance reply since
        // it's what GC is currently built around.
        auto cmd = std::make_shared<api::RemoveLocationCommand>(
                "false", makeDocumentBucket(document::BucketId(30, 1234)));
        auto reply = std::shared_ptr<api::StorageReply>(cmd->makeReply());
        ASSERT_TRUE(_stripe->handleReply(reply));
    }
}

TEST_F(DistributorStripeTest, update_bucket_database)
{
    enable_cluster_state("distributor:1 storage:3");

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
              "node(idx=1,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false)",
              updateBucketDB("0:456,1:456,2:789", "2:r"));

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
              "node(idx=2,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
              "node(idx=1,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false)",
              updateBucketDB("0:456,1:456", "2:456"));

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x315,docs=394/394,bytes=197/197,trusted=false,active=false,ready=false), "
              "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=false,active=false,ready=false), "
              "node(idx=1,crc=0x34a,docs=421/421,bytes=210/210,trusted=false,active=false,ready=false)",
              updateBucketDB("0:456:t,1:456:t,2:123", "0:789,1:842,2:333"));

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x315,docs=394/394,bytes=197/197,trusted=true,active=false,ready=false), "
              "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=false,active=false,ready=false), "
              "node(idx=1,crc=0x315,docs=394/394,bytes=197/197,trusted=true,active=false,ready=false)",
              updateBucketDB("0:456:t,1:456:t,2:123", "0:789,1:789,2:333"));

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=true,active=false,ready=false)",
              updateBucketDB("0:456:t,1:456:t", "0:r,1:r,2:333"));

    // Copies are in sync so should still be trusted even if explicitly reset.
    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
              "node(idx=2,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
              "node(idx=1,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false)",
              updateBucketDB("0:456,1:456", "2:456", ResetTrusted(true)));

    // When resetting, first inserted copy should not end up as implicitly trusted.
    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=false,active=false,ready=false), "
              "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=false,active=false,ready=false)",
              updateBucketDB("0:456", "2:333", ResetTrusted(true)));
}

TEST_F(DistributorStripeTest, priority_config_is_propagated_to_distributor_configuration)
{
    using namespace vespa::config::content::core;

    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    ConfigBuilder builder;
    builder.priorityMergeMoveToIdealNode = 1;
    builder.priorityMergeOutOfSyncCopies = 2;
    builder.priorityMergeTooFewCopies = 3;
    builder.priorityActivateNoExistingActive = 4;
    builder.priorityActivateWithExistingActive = 5;
    builder.priorityDeleteBucketCopy = 6;
    builder.priorityJoinBuckets = 7;
    builder.prioritySplitDistributionBits = 8;
    builder.prioritySplitLargeBucket = 9;
    builder.prioritySplitInconsistentBucket = 10;
    builder.priorityGarbageCollection = 11;
    builder.priorityMergeGlobalBuckets = 12;

    configure_stripe(builder);

    const auto& mp = getConfig().getMaintenancePriorities();
    EXPECT_EQ(1, static_cast<int>(mp.mergeMoveToIdealNode));
    EXPECT_EQ(2, static_cast<int>(mp.mergeOutOfSyncCopies));
    EXPECT_EQ(3, static_cast<int>(mp.mergeTooFewCopies));
    EXPECT_EQ(4, static_cast<int>(mp.activateNoExistingActive));
    EXPECT_EQ(5, static_cast<int>(mp.activateWithExistingActive));
    EXPECT_EQ(6, static_cast<int>(mp.deleteBucketCopy));
    EXPECT_EQ(7, static_cast<int>(mp.joinBuckets));
    EXPECT_EQ(8, static_cast<int>(mp.splitDistributionBits));
    EXPECT_EQ(9, static_cast<int>(mp.splitLargeBucket));
    EXPECT_EQ(10, static_cast<int>(mp.splitInconsistentBucket));
    EXPECT_EQ(11, static_cast<int>(mp.garbageCollection));
    EXPECT_EQ(12, static_cast<int>(mp.mergeGlobalBuckets));
}

TEST_F(DistributorStripeTest, no_db_resurrection_for_bucket_not_owned_in_pending_state) {
    setup_stripe(Redundancy(1), NodeCount(10), "storage:2 distributor:2");
    // Force new state into being the pending state. According to the initial
    // state we own the bucket, but according to the pending state, we do
    // not. This must be handled correctly by the database update code.
    simulate_set_pending_cluster_state("storage:10 distributor:10");

    document::BucketId nonOwnedBucket(16, 3);
    EXPECT_FALSE(getDistributorBucketSpace().get_bucket_ownership_flags(nonOwnedBucket).owned_in_pending_state());
    EXPECT_FALSE(getDistributorBucketSpace().check_ownership_in_pending_and_current_state(nonOwnedBucket).isOwned());

    std::vector<BucketCopy> copies;
    copies.emplace_back(1234, 0, api::BucketInfo(0x567, 1, 2));
    operation_context().update_bucket_database(makeDocumentBucket(nonOwnedBucket), copies,
                                               DatabaseUpdate::CREATE_IF_NONEXISTING);

    EXPECT_EQ("NONEXISTING", dumpBucket(nonOwnedBucket));
}

TEST_F(DistributorStripeTest, added_db_buckets_without_gc_timestamp_implicitly_get_current_time)
{
    setup_stripe(Redundancy(1), NodeCount(10), "storage:2 distributor:2");
    getClock().setAbsoluteTimeInSeconds(101234);
    document::BucketId bucket(16, 7654);

    std::vector<BucketCopy> copies;
    copies.emplace_back(1234, 0, api::BucketInfo(0x567, 1, 2));
    operation_context().update_bucket_database(makeDocumentBucket(bucket), copies,
                                               DatabaseUpdate::CREATE_IF_NONEXISTING);
    BucketDatabase::Entry e(getBucket(bucket));
    EXPECT_EQ(101234, e->getLastGarbageCollectionTime());
}

TEST_F(DistributorStripeTest, merge_stats_are_accumulated_during_database_iteration)
{
    setup_stripe(Redundancy(2), NodeCount(3), "storage:3 distributor:1");
    // Copies out of sync. Not possible for stripe to _reliably_ tell
    // which direction(s) data will flow, so for simplicity assume that we
    // must sync both copies.
    // Note that we mark certain copies as active to prevent the bucketstate
    // checker from pre-empting the merges.
    // -> syncing[0] += 1, syncing[2] += 1
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a,2=2/2/2");
    // Must add missing node 2 for bucket
    // -> copyingOut[0] += 1, copyingIn[2] += 1
    addNodesToBucketDB(document::BucketId(16, 2), "0=1/1/1/t/a");
    // Moving from non-ideal node 1 to ideal node 2. Both nodes 0 and 1 will
    // be involved in this merge, but only node 1 will be tagged as source only
    // (i.e. to be deleted after the merge is completed).
    // -> copyingOut[0] += 1, movingOut[1] += 1, copyingIn[2] += 1
    addNodesToBucketDB(document::BucketId(16, 3), "0=2/2/2/t/a,1=2/2/2/t");

    // Go many full scanner rounds to check that stats are set, not
    // added to existing.
    tickDistributorNTimes(50);

    const auto stats = stripe_maintenance_stats();
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        wanted.copyingOut = 2;
        wanted.total = 3;
        EXPECT_EQ(wanted, stats.perNodeStats.forNode(0, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.movingOut = 1;
        wanted.total = 1;
        EXPECT_EQ(wanted, stats.perNodeStats.forNode(1, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        wanted.copyingIn = 2;
        wanted.total = 1;
        EXPECT_EQ(wanted, stats.perNodeStats.forNode(2, makeBucketSpace()));
    }
    auto bucketStats = stripe_bucket_spaces_stats();
    ASSERT_EQ(3, bucketStats.size());
    assertBucketSpaceStats(1, 3, 0, "default", bucketStats);
    assertBucketSpaceStats(0, 1, 1, "default", bucketStats);
    assertBucketSpaceStats(3, 1, 2, "default", bucketStats);

    EXPECT_EQ(stats.perNodeStats.total_replica_stats().movingOut, 1);
    EXPECT_EQ(stats.perNodeStats.total_replica_stats().copyingOut, 2);
    EXPECT_EQ(stats.perNodeStats.total_replica_stats().copyingIn, 2);
    EXPECT_EQ(stats.perNodeStats.total_replica_stats().syncing, 2);
}

void
DistributorStripeTest::assertBucketSpaceStats(size_t expBucketPending, size_t expBucketTotal, uint16_t node,
                                              const vespalib::string& bucketSpace,
                                              const BucketSpacesStatsProvider::PerNodeBucketSpacesStats& stats)
{
    auto nodeItr = stats.find(node);
    ASSERT_TRUE(nodeItr != stats.end());
    ASSERT_EQ(1, nodeItr->second.size());
    auto bucketSpaceItr = nodeItr->second.find(bucketSpace);
    ASSERT_TRUE(bucketSpaceItr != nodeItr->second.end());
    ASSERT_TRUE(bucketSpaceItr->second.valid());
    ASSERT_EQ(expBucketTotal, bucketSpaceItr->second.bucketsTotal());
    ASSERT_EQ(expBucketPending, bucketSpaceItr->second.bucketsPending());
}

/**
 * Since maintenance operations are prioritized differently, activation
 * pre-empts merging and other ops. If this also implies pre-empting running
 * their state checkers at all, we won't get any statistics from any other
 * operations for the bucket.
 */
TEST_F(DistributorStripeTest, stats_generated_for_preempted_operations)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    // For this test it suffices to have a single bucket with multiple aspects
    // wrong about it. In this case, let a bucket be both out of sync _and_
    // missing an active copy. This _should_ give a statistic with both nodes 0
    // and 1 requiring a sync. If instead merge stats generation is preempted
    // by activation, we'll see no merge stats at all.
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1,1=2/2/2");
    tickDistributorNTimes(50);
    const auto stats = stripe_maintenance_stats();
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        wanted.total = 1;
        EXPECT_EQ(wanted, stats.perNodeStats.forNode(0, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        wanted.total = 1;
        EXPECT_EQ(wanted, stats.perNodeStats.forNode(1, makeBucketSpace()));
    }
}

TEST_F(DistributorStripeTest, replica_counting_mode_is_configured_to_trusted_by_default)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    EXPECT_EQ(ConfigBuilder::MinimumReplicaCountingMode::TRUSTED, currentReplicaCountingMode());
}

TEST_F(DistributorStripeTest, replica_counting_mode_config_is_propagated_to_metric_updater)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    ConfigBuilder builder;
    builder.minimumReplicaCountingMode = ConfigBuilder::MinimumReplicaCountingMode::ANY;
    configure_stripe(builder);
    EXPECT_EQ(ConfigBuilder::MinimumReplicaCountingMode::ANY, currentReplicaCountingMode());
}

TEST_F(DistributorStripeTest, max_consecutively_inhibited_maintenance_ticks_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    ConfigBuilder builder;
    builder.maxConsecutivelyInhibitedMaintenanceTicks = 123;
    configure_stripe(builder);
    EXPECT_EQ(getConfig().max_consecutively_inhibited_maintenance_ticks(), 123);
}

TEST_F(DistributorStripeTest, bucket_activation_is_enabled_by_default)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    EXPECT_FALSE(getConfig().isBucketActivationDisabled());
}

TEST_F(DistributorStripeTest, bucket_activation_config_is_propagated_to_distributor_configuration)
{
    using namespace vespa::config::content::core;

    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    ConfigBuilder builder;
    builder.disableBucketActivation = true;
    configure_stripe(builder);

    EXPECT_TRUE(getConfig().isBucketActivationDisabled());
}

void
DistributorStripeTest::configureMaxClusterClockSkew(int seconds)
{
    ConfigBuilder builder;
    builder.maxClusterClockSkewSec = seconds;
    configure_stripe(builder);
}

TEST_F(DistributorStripeTest, max_clock_skew_config_is_propagated_to_distributor_config)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    configureMaxClusterClockSkew(5);
    EXPECT_EQ(getConfig().getMaxClusterClockSkew(), std::chrono::seconds(5));
}

TEST_F(DistributorStripeTest, inhibit_default_merge_if_global_merges_pending_config_is_propagated)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    ConfigBuilder builder;
    builder.inhibitDefaultMergesWhenGlobalMergesPending = true;
    configure_stripe(builder);
    EXPECT_TRUE(getConfig().inhibit_default_merges_when_global_merges_pending());

    builder.inhibitDefaultMergesWhenGlobalMergesPending = false;
    configure_stripe(builder);
    EXPECT_FALSE(getConfig().inhibit_default_merges_when_global_merges_pending());
}

namespace {

auto makeDummyRemoveCommand() {
    return std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("id:foo:testdoctype1:n=1:foo"),
            api::Timestamp(0));
}

}

void
DistributorStripeTest::configure_mutation_sequencing(bool enabled)
{
    ConfigBuilder builder;
    builder.sequenceMutatingOperations = enabled;
    configure_stripe(builder);
}

TEST_F(DistributorStripeTest, sequencing_config_is_propagated_to_distributor_config)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    // Should be enabled by default
    EXPECT_TRUE(getConfig().getSequenceMutatingOperations());

    // Explicitly disabled.
    configure_mutation_sequencing(false);
    EXPECT_FALSE(getConfig().getSequenceMutatingOperations());

    // Explicitly enabled.
    configure_mutation_sequencing(true);
    EXPECT_TRUE(getConfig().getSequenceMutatingOperations());
}

void
DistributorStripeTest::configure_merge_busy_inhibit_duration(int seconds)
{
    ConfigBuilder builder;
    builder.inhibitMergeSendingOnBusyNodeDurationSec = seconds;
    configure_stripe(builder);
}

TEST_F(DistributorStripeTest, merge_busy_inhibit_duration_config_is_propagated_to_distributor_config)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    configure_merge_busy_inhibit_duration(7);
    EXPECT_EQ(getConfig().getInhibitMergesOnBusyNodeDuration(), std::chrono::seconds(7));
}

TEST_F(DistributorStripeTest, merge_busy_inhibit_duration_is_propagated_to_pending_message_tracker)
{
    setup_stripe(Redundancy(2), NodeCount(2), "storage:1 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    configure_merge_busy_inhibit_duration(100);
    auto cmd = makeDummyRemoveCommand(); // Remove is for bucket 1
    stripe_handle_message(cmd);

    // Should send to content node 0
    ASSERT_EQ(1, _sender.commands().size());
    ASSERT_EQ(api::MessageType::REMOVE, _sender.command(0)->getType());
    auto& fwd_cmd = dynamic_cast<api::RemoveCommand&>(*_sender.command(0));
    auto reply = fwd_cmd.makeReply();
    reply->setResult(api::ReturnCode(api::ReturnCode::BUSY));
    _stripe->handleReply(std::shared_ptr<api::StorageReply>(std::move(reply)));

    auto& node_info = pending_message_tracker().getNodeInfo();

    EXPECT_TRUE(node_info.isBusy(0));
    getClock().addSecondsToTime(99);
    EXPECT_TRUE(node_info.isBusy(0));
    getClock().addSecondsToTime(2);
    EXPECT_FALSE(node_info.isBusy(0));
}

TEST_F(DistributorStripeTest, external_client_requests_are_handled_individually_in_priority_order)
{
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a");

    std::vector<api::StorageMessage::Priority> priorities({50, 255, 10, 40, 0});
    document::DocumentId id("id:foo:testdoctype1:n=1:foo");
    vespalib::stringref field_set = "";
    for (auto pri : priorities) {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId()), id, field_set);
        cmd->setPriority(pri);
        _stripe->handle_or_enqueue_message(cmd);
    }
    // At the hand-off point we expect client requests to be prioritized.
    // For each tick, a priority-order client request is processed and sent off.
    for (size_t i = 1; i <= priorities.size(); ++i) {
        tickDistributorNTimes(1);
        ASSERT_EQ(i, _sender.commands().size());
    }

    std::vector<int> expected({0, 10, 40, 50, 255});
    std::vector<int> actual;
    for (auto& msg : _sender.commands()) {
        actual.emplace_back(static_cast<int>(msg->getPriority()));
    }
    EXPECT_THAT(actual, ContainerEq(expected));
}

TEST_F(DistributorStripeTest, internal_messages_are_started_in_fifo_order_batch)
{
    // To test internal request ordering, we use NotifyBucketChangeCommand
    // for the reason that it explicitly updates the bucket database for
    // each individual invocation.
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");

    std::vector<api::StorageMessage::Priority> priorities({50, 255, 10, 40, 1});
    for (auto pri : priorities) {
        api::BucketInfo fake_info(pri, pri, pri);
        auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket), fake_info);
        cmd->setSourceIndex(0);
        cmd->setPriority(pri);
        _stripe->handle_or_enqueue_message(cmd);
    }

    // Doing a single tick should process all internal requests in one batch
    tickDistributorNTimes(1);
    ASSERT_EQ(5, _sender.replies().size());

    // The bucket info for priority 1 (last FIFO-order change command received, but
    // highest priority) should be the end-state of the bucket database, _not_ that
    // of lowest priority 255.
    BucketDatabase::Entry e(getBucket(bucket));
    EXPECT_EQ(api::BucketInfo(1, 1, 1), e.getBucketInfo().getNode(0)->getBucketInfo());
}

// TODO STRIPE also test that closing distributor closes stripes
TEST_F(DistributorStripeTest, closing_aborts_priority_queued_client_requests)
{
    setup_stripe(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");

    document::DocumentId id("id:foo:testdoctype1:n=1:foo");
    vespalib::stringref field_set = "";
    for (int i = 0; i < 10; ++i) {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId()), id, field_set);
        _stripe->handle_or_enqueue_message(cmd);
    }
    tickDistributorNTimes(1);
    // Closing should trigger 1 abort via startet GetOperation and 9 aborts from pri queue
    _stripe->flush_and_close();
    ASSERT_EQ(10, _sender.replies().size());
    for (auto& msg : _sender.replies()) {
        EXPECT_EQ(api::ReturnCode::ABORTED, dynamic_cast<api::StorageReply&>(*msg).getResult().getResult());
    }
}

namespace {

void assert_invalid_stats_for_all_spaces(
        const BucketSpacesStatsProvider::PerNodeBucketSpacesStats& stats,
        uint16_t node_index) {
    auto stats_iter = stats.find(node_index);
    ASSERT_TRUE(stats_iter != stats.cend());
    ASSERT_EQ(2, stats_iter->second.size());
    auto space_iter = stats_iter->second.find(document::FixedBucketSpaces::default_space_name());
    ASSERT_TRUE(space_iter != stats_iter->second.cend());
    ASSERT_FALSE(space_iter->second.valid());
    space_iter = stats_iter->second.find(document::FixedBucketSpaces::global_space_name());
    ASSERT_TRUE(space_iter != stats_iter->second.cend());
    ASSERT_FALSE(space_iter->second.valid());
}

}

TEST_F(DistributorStripeTest, entering_recovery_mode_resets_bucket_space_stats)
{
    // Set up a cluster state + DB contents which implies merge maintenance ops
    setup_stripe(Redundancy(2), NodeCount(2), "version:1 distributor:1 storage:2");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a");
    addNodesToBucketDB(document::BucketId(16, 2), "0=1/1/1/t/a");
    addNodesToBucketDB(document::BucketId(16, 3), "0=2/2/2/t/a");

    tickDistributorNTimes(5); // 1/3rds into second round through database

    enable_cluster_state("version:2 distributor:1 storage:3 .1.s:d");
    EXPECT_TRUE(stripe_is_in_recovery_mode());
    // Bucket space stats should now be invalid per space per node, pending stats
    // from state version 2. Exposing stats from version 1 risks reporting stale
    // information back to the cluster controller.
    const auto stats = stripe_bucket_spaces_stats();
    ASSERT_EQ(2, stats.size());

    assert_invalid_stats_for_all_spaces(stats, 0);
    assert_invalid_stats_for_all_spaces(stats, 2);
}

TEST_F(DistributorStripeTest, stale_reads_config_is_propagated_to_external_operation_handler)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_stale_reads_enabled(true);
    EXPECT_TRUE(getExternalOperationHandler().concurrent_gets_enabled());

    configure_stale_reads_enabled(false);
    EXPECT_FALSE(getExternalOperationHandler().concurrent_gets_enabled());
}

TEST_F(DistributorStripeTest, fast_path_on_consistent_gets_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_update_fast_path_restart_enabled(true);
    EXPECT_TRUE(getConfig().update_fast_path_restart_enabled());

    configure_update_fast_path_restart_enabled(false);
    EXPECT_FALSE(getConfig().update_fast_path_restart_enabled());
}

TEST_F(DistributorStripeTest, merge_disabling_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_merge_operations_disabled(true);
    EXPECT_TRUE(getConfig().merge_operations_disabled());

    configure_merge_operations_disabled(false);
    EXPECT_FALSE(getConfig().merge_operations_disabled());
}

TEST_F(DistributorStripeTest, metadata_update_phase_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_metadata_update_phase_enabled(true);
    EXPECT_TRUE(getConfig().enable_metadata_only_fetch_phase_for_inconsistent_updates());

    configure_metadata_update_phase_enabled(false);
    EXPECT_FALSE(getConfig().enable_metadata_only_fetch_phase_for_inconsistent_updates());
}

TEST_F(DistributorStripeTest, weak_internal_read_consistency_config_is_propagated_to_internal_configs)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_use_weak_internal_read_consistency(true);
    EXPECT_TRUE(getConfig().use_weak_internal_read_consistency_for_client_gets());
    EXPECT_TRUE(getExternalOperationHandler().use_weak_internal_read_consistency_for_gets());

    configure_use_weak_internal_read_consistency(false);
    EXPECT_FALSE(getConfig().use_weak_internal_read_consistency_for_client_gets());
    EXPECT_FALSE(getExternalOperationHandler().use_weak_internal_read_consistency_for_gets());
}

TEST_F(DistributorStripeTest, prioritize_global_bucket_merges_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_prioritize_global_bucket_merges(true);
    EXPECT_TRUE(getConfig().prioritize_global_bucket_merges());

    configure_prioritize_global_bucket_merges(false);
    EXPECT_FALSE(getConfig().prioritize_global_bucket_merges());
}

TEST_F(DistributorStripeTest, max_activation_inhibited_out_of_sync_groups_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_max_activation_inhibited_out_of_sync_groups(3);
    EXPECT_EQ(getConfig().max_activation_inhibited_out_of_sync_groups(), 3);

    configure_max_activation_inhibited_out_of_sync_groups(0);
    EXPECT_EQ(getConfig().max_activation_inhibited_out_of_sync_groups(), 0);
}

TEST_F(DistributorStripeTest, implicitly_clear_priority_on_schedule_config_is_propagated_to_scheduler)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_implicitly_clear_priority_on_schedule(true);
    EXPECT_TRUE(scheduler_has_implicitly_clear_priority_on_schedule_set());

    configure_implicitly_clear_priority_on_schedule(false);
    EXPECT_FALSE(scheduler_has_implicitly_clear_priority_on_schedule_set());
}

TEST_F(DistributorStripeTest, wanted_split_bit_count_is_lower_bounded)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    ConfigBuilder builder;
    builder.minsplitcount = 7;
    configure_stripe(builder);

    EXPECT_EQ(getConfig().getMinimalBucketSplit(), 8);
}

namespace {

auto make_dummy_get_command_for_bucket_1() {
    return std::make_shared<api::GetCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("id:foo:testdoctype1:n=1:foo"),
            document::AllFields::NAME);
}

}

void
DistributorStripeTest::set_up_and_start_get_op_with_stale_reads_enabled(bool enabled)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");
    configure_stale_reads_enabled(enabled);

    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");
    _stripe->handle_or_enqueue_message(make_dummy_get_command_for_bucket_1());
}

TEST_F(DistributorStripeTest, gets_are_started_outside_main_stripe_logic_if_stale_reads_enabled)
{
    set_up_and_start_get_op_with_stale_reads_enabled(true);
    ASSERT_THAT(_sender.commands(), SizeIs(1));
    EXPECT_THAT(_sender.replies(), SizeIs(0));

    // Reply is routed to the correct owner
    auto reply = std::shared_ptr<api::StorageReply>(_sender.command(0)->makeReply());
    _stripe->handle_or_enqueue_message(reply);
    ASSERT_THAT(_sender.commands(), SizeIs(1));
    EXPECT_THAT(_sender.replies(), SizeIs(1));
}

TEST_F(DistributorStripeTest, gets_are_not_started_outside_main_stripe_logic_if_stale_reads_disabled)
{
    set_up_and_start_get_op_with_stale_reads_enabled(false);
    // Get has been placed into distributor queue, so no external messages are produced.
    EXPECT_THAT(_sender.commands(), SizeIs(0));
    EXPECT_THAT(_sender.replies(), SizeIs(0));
}

// There's no need or desire to track "lockfree" Gets in the main pending message tracker,
// as we only have to track mutations to inhibit maintenance ops safely. Furthermore,
// the message tracker is a multi-index and therefore has some runtime cost.
TEST_F(DistributorStripeTest, gets_started_outside_stripe_thread_are_not_tracked_by_pending_message_tracker)
{
    set_up_and_start_get_op_with_stale_reads_enabled(true);
    Bucket bucket(FixedBucketSpaces::default_space(), BucketId(16, 1));
    EXPECT_FALSE(pending_message_tracker().hasPendingMessage(
            0, bucket, api::MessageType::GET_ID));
}

TEST_F(DistributorStripeTest, closing_aborts_gets_started_outside_stripe_thread)
{
    set_up_and_start_get_op_with_stale_reads_enabled(true);
    _stripe->flush_and_close();
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ(api::ReturnCode::ABORTED, _sender.reply(0)->getResult().getResult());
}

TEST_F(DistributorStripeTest, use_unordered_merge_chaining_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_use_unordered_merge_chaining(true);
    EXPECT_TRUE(getConfig().use_unordered_merge_chaining());

    configure_use_unordered_merge_chaining(false);
    EXPECT_FALSE(getConfig().use_unordered_merge_chaining());
}

TEST_F(DistributorStripeTest, enable_two_phase_gc_config_is_propagated_to_internal_config)
{
    setup_stripe(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    EXPECT_TRUE(getConfig().enable_two_phase_garbage_collection());

    configure_enable_two_phase_garbage_collection(true);
    EXPECT_TRUE(getConfig().enable_two_phase_garbage_collection());

    configure_enable_two_phase_garbage_collection(false);
    EXPECT_FALSE(getConfig().enable_two_phase_garbage_collection());
}

}
