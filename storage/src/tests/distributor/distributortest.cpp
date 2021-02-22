// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/metrics/updatehook.h>
#include <thread>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using document::FixedBucketSpaces;
using document::BucketSpace;
using document::Bucket;
using document::BucketId;
using namespace ::testing;

namespace storage::distributor {

struct DistributorTest : Test, DistributorTestUtil {
    DistributorTest();
    ~DistributorTest() override;

    // TODO handle edge case for window between getnodestate reply already
    // sent and new request not yet received

    void assertBucketSpaceStats(size_t expBucketPending, size_t expBucketTotal, uint16_t node, const vespalib::string &bucketSpace,
                                const BucketSpacesStatsProvider::PerNodeBucketSpacesStats &stats);
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

    void configureDistributor(const ConfigBuilder& config) {
        getConfig().configure(config);
        _distributor->enableNextConfig();
    }

    auto currentReplicaCountingMode() const noexcept {
        return _distributor->_bucketDBMetricUpdater
                .getMinimumReplicaCountingMode();
    }

    std::string testOp(std::shared_ptr<api::StorageMessage> msg)
    {
        _distributor->handleMessage(msg);

        std::string tmp = _sender.getCommands();
        _sender.clear();
        return tmp;
    }

    void tickDistributorNTimes(uint32_t n) {
        for (uint32_t i = 0; i < n; ++i) {
            tick();
        }
    }

    typedef bool ResetTrusted;

    std::string updateBucketDB(const std::string& firstState,
                               const std::string& secondState,
                               bool resetTrusted = false)
    {
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

            distributor_component().removeNodesFromDB(makeDocumentBucket(document::BucketId(16, 1)), removedNodes);

            uint32_t flags(DatabaseUpdate::CREATE_IF_NONEXISTING
                           | (resetTrusted ? DatabaseUpdate::RESET_TRUSTED : 0));

            distributor_component().updateBucketDatabase(makeDocumentBucket(document::BucketId(16, 1)),
                                                         changedNodes,
                                                         flags);
        }

        std::string retVal = dumpBucket(document::BucketId(16, 1));
        getBucketDatabase().clear();
        return retVal;
    }

    size_t explicit_node_state_reply_send_invocations() const noexcept {
        return _node->getNodeStateUpdater().explicit_node_state_reply_send_invocations();
    }

    StatusReporterDelegate& distributor_status_delegate() {
        return _distributor->_distributorStatusDelegate;
    }

    framework::TickingThreadPool& distributor_thread_pool() {
        return _distributor->_threadPool;
    }

    const std::vector<std::shared_ptr<Distributor::Status>>& distributor_status_todos() {
        return _distributor->_statusToDo;
    }

    Distributor::MetricUpdateHook distributor_metric_update_hook() {
        return _distributor->_metricUpdateHook;
    }

    SimpleMaintenanceScanner::PendingMaintenanceStats& distributor_maintenance_stats() {
        return _distributor->_maintenanceStats;
    }

    BucketSpacesStatsProvider::PerNodeBucketSpacesStats distributor_bucket_spaces_stats() {
        return _distributor->getBucketSpacesStats();
    }

    DistributorHostInfoReporter& distributor_host_info_reporter() {
        return _distributor->_hostInfoReporter;
    }

    bool distributor_handle_message(const std::shared_ptr<api::StorageMessage>& msg) {
        return _distributor->handleMessage(msg);
    }

    void configure_stale_reads_enabled(bool enabled) {
        ConfigBuilder builder;
        builder.allowStaleReadsDuringClusterStateTransitions = enabled;
        configureDistributor(builder);
    }

    void configure_update_fast_path_restart_enabled(bool enabled) {
        ConfigBuilder builder;
        builder.restartWithFastUpdatePathIfAllGetTimestampsAreConsistent = enabled;
        configureDistributor(builder);
    }

    void configure_merge_operations_disabled(bool disabled) {
        ConfigBuilder builder;
        builder.mergeOperationsDisabled = disabled;
        configureDistributor(builder);
    }

    void configure_use_weak_internal_read_consistency(bool use_weak) {
        ConfigBuilder builder;
        builder.useWeakInternalReadConsistencyForClientGets = use_weak;
        configureDistributor(builder);
    }

    void configure_metadata_update_phase_enabled(bool enabled) {
        ConfigBuilder builder;
        builder.enableMetadataOnlyFetchPhaseForInconsistentUpdates = enabled;
        configureDistributor(builder);
    }

    void configure_prioritize_global_bucket_merges(bool enabled) {
        ConfigBuilder builder;
        builder.prioritizeGlobalBucketMerges = enabled;
        configureDistributor(builder);
    }

    void configure_max_activation_inhibited_out_of_sync_groups(uint32_t n_groups) {
        ConfigBuilder builder;
        builder.maxActivationInhibitedOutOfSyncGroups = n_groups;
        configureDistributor(builder);
    }

    void configureMaxClusterClockSkew(int seconds);
    void sendDownClusterStateCommand();
    void replyToSingleRequestBucketInfoCommandWith1Bucket();
    void sendDownDummyRemoveCommand();
    void assertSingleBouncedRemoveReplyPresent();
    void assertNoMessageBounced();
    void configure_mutation_sequencing(bool enabled);
    void configure_merge_busy_inhibit_duration(int seconds);
    void do_test_pending_merge_getnodestate_reply_edge(BucketSpace space);

    void set_up_and_start_get_op_with_stale_reads_enabled(bool enabled);
};

DistributorTest::DistributorTest()
    : Test(),
      DistributorTestUtil(),
      _bucketSpaces()
{
}

DistributorTest::~DistributorTest() = default;

TEST_F(DistributorTest, operation_generation) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

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

TEST_F(DistributorTest, operations_generated_and_started_without_duplicates) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }

    tickDistributorNTimes(20);

    ASSERT_FALSE(tick());

    ASSERT_EQ(6, _sender.commands().size());
}

TEST_F(DistributorTest, recovery_mode_on_cluster_state_change) {
    setupDistributor(Redundancy(1), NodeCount(2),
                     "storage:1 .0.s:d distributor:1");
    enableDistributorClusterState("storage:1 distributor:1");

    EXPECT_TRUE(_distributor->isInRecoveryMode());
    for (uint32_t i = 0; i < 3; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }
    for (int i = 0; i < 3; ++i) {
        tick();
        EXPECT_TRUE(_distributor->isInRecoveryMode());
    }
    tick();
    EXPECT_FALSE(_distributor->isInRecoveryMode());

    enableDistributorClusterState("storage:2 distributor:1");
    EXPECT_TRUE(_distributor->isInRecoveryMode());
}

TEST_F(DistributorTest, operations_are_throttled) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    getConfig().setMinPendingMaintenanceOps(1);
    getConfig().setMaxPendingMaintenanceOps(1);

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }
    tickDistributorNTimes(20);
    ASSERT_EQ(1, _sender.commands().size());
}

TEST_F(DistributorTest, handle_unknown_maintenance_reply) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    {
        auto cmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(document::BucketId(16, 1234)));
        auto reply = std::make_shared<api::SplitBucketReply>(*cmd);
        ASSERT_TRUE(_distributor->handleReply(reply));
    }

    {
        // RemoveLocationReply must be treated as a maintenance reply since
        // it's what GC is currently built around.
        auto cmd = std::make_shared<api::RemoveLocationCommand>(
                "false", makeDocumentBucket(document::BucketId(30, 1234)));
        auto reply = std::shared_ptr<api::StorageReply>(cmd->makeReply());
        ASSERT_TRUE(_distributor->handleReply(reply));
    }
}

TEST_F(DistributorTest, contains_time_statement) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    EXPECT_FALSE(getConfig().containsTimeStatement(""));
    EXPECT_FALSE(getConfig().containsTimeStatement("testdoctype1"));
    EXPECT_FALSE(getConfig().containsTimeStatement("testdoctype1.headerfield > 42"));
    EXPECT_TRUE(getConfig().containsTimeStatement("testdoctype1.headerfield > now()"));
    EXPECT_TRUE(getConfig().containsTimeStatement("testdoctype1.headerfield > now() - 3600"));
    EXPECT_TRUE(getConfig().containsTimeStatement("testdoctype1.headerfield == now() - 3600"));
}

TEST_F(DistributorTest, update_bucket_database) {
    enableDistributorClusterState("distributor:1 storage:3");

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

namespace {

using namespace framework::defaultimplementation;

class StatusRequestThread : public framework::Runnable {
    StatusReporterDelegate& _reporter;
    std::string _result;
public:
    explicit StatusRequestThread(StatusReporterDelegate& reporter)
        : _reporter(reporter)
    {}
    void run(framework::ThreadHandle&) override {
        framework::HttpUrlPath path("/distributor?page=buckets");
        std::ostringstream stream;
        _reporter.reportStatus(stream, path);
        _result = stream.str();
    }

    std::string getResult() const {
        return _result;
    }
};

}

TEST_F(DistributorTest, tick_processes_status_requests) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    // Must go via delegate since reportStatus is now just a rendering
    // function and not a request enqueuer (see Distributor::handleStatusRequest).
    StatusRequestThread thread(distributor_status_delegate());
    FakeClock clock;
    ThreadPoolImpl pool(clock);
    int ticksBeforeWait = 1;
    framework::Thread::UP tp(pool.startThread(thread, "statustest", 5ms, 5s, ticksBeforeWait));

    while (true) {
        std::this_thread::sleep_for(1ms);
        framework::TickingLockGuard guard(distributor_thread_pool().freezeCriticalTicks());
        if (!distributor_status_todos().empty()) {
            break;
        }
        
    }
    ASSERT_TRUE(tick());

    tp->interruptAndJoin();

    EXPECT_THAT(thread.getResult(), HasSubstr("BucketId(0x4000000000000001)"));
}

TEST_F(DistributorTest, metric_update_hook_updates_pending_maintenance_metrics) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    // To ensure we count all operations, not just those fitting within the
    // pending window.
    getConfig().setMinPendingMaintenanceOps(1);
    getConfig().setMaxPendingMaintenanceOps(1);

    // 1 bucket must be merged, 1 must be split, 1 should be activated.
    addNodesToBucketDB(document::BucketId(16, 1), "0=2/2/2/t/a,1=1/1/1");
    addNodesToBucketDB(document::BucketId(16, 2),
                       "0=100/10000000/200000/t/a,1=100/10000000/200000/t");
    addNodesToBucketDB(document::BucketId(16, 3),
                       "0=200/300/400/t,1=200/300/400/t");

    // Go many full scanner rounds to check that metrics are set, not
    // added to existing.
    tickDistributorNTimes(50);

    // By this point, no hook has been called so the metrics have not been
    // set.
    using MO = MaintenanceOperation;
    {
        const IdealStateMetricSet& metrics(getIdealStateManager().getMetrics());
        EXPECT_EQ(0, metrics.operations[MO::MERGE_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::SPLIT_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::SET_BUCKET_STATE]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::DELETE_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::JOIN_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::GARBAGE_COLLECTION]->pending.getLast());
    }

    // Force trigger update hook
    std::mutex l;
    distributor_metric_update_hook().updateMetrics(metrics::MetricLockGuard(l));
    // Metrics should now be updated to the last complete working state
    {
        const IdealStateMetricSet& metrics(getIdealStateManager().getMetrics());
        EXPECT_EQ(1, metrics.operations[MO::MERGE_BUCKET]->pending.getLast());
        EXPECT_EQ(1, metrics.operations[MO::SPLIT_BUCKET]->pending.getLast());
        EXPECT_EQ(1, metrics.operations[MO::SET_BUCKET_STATE]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::DELETE_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::JOIN_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::GARBAGE_COLLECTION]->pending.getLast());
    }
}

namespace {

uint64_t db_sample_interval_sec(const Distributor& d) noexcept {
    return std::chrono::duration_cast<std::chrono::seconds>(d.db_memory_sample_interval()).count();
}

}

TEST_F(DistributorTest, bucket_db_memory_usage_metrics_only_updated_at_fixed_time_intervals) {
    getClock().setAbsoluteTimeInSeconds(1000);

    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a,1=2/2/2");
    tickDistributorNTimes(10);

    std::mutex l;
    distributor_metric_update_hook().updateMetrics(metrics::MetricLockGuard(l));
    auto* m = getDistributor().getMetrics().mutable_dbs.memory_usage.getMetric("used_bytes");
    ASSERT_TRUE(m != nullptr);
    auto last_used = m->getLongValue("last");
    EXPECT_GT(last_used, 0);

    // Add another bucket to the DB. This should increase the underlying used number of
    // bytes, but this should not be aggregated into the metrics until the sampling time
    // interval has passed. Instead, old metric gauge values should be preserved.
    addNodesToBucketDB(document::BucketId(16, 2), "0=1/1/1/t/a,1=2/2/2");

    const auto sample_interval_sec = db_sample_interval_sec(getDistributor());
    getClock().setAbsoluteTimeInSeconds(1000 + sample_interval_sec - 1); // Not there yet.
    tickDistributorNTimes(50);
    distributor_metric_update_hook().updateMetrics(metrics::MetricLockGuard(l));

    m = getDistributor().getMetrics().mutable_dbs.memory_usage.getMetric("used_bytes");
    auto now_used = m->getLongValue("last");
    EXPECT_EQ(now_used, last_used);

    getClock().setAbsoluteTimeInSeconds(1000 + sample_interval_sec + 1);
    tickDistributorNTimes(10);
    distributor_metric_update_hook().updateMetrics(metrics::MetricLockGuard(l));

    m = getDistributor().getMetrics().mutable_dbs.memory_usage.getMetric("used_bytes");
    now_used = m->getLongValue("last");
    EXPECT_GT(now_used, last_used);
}

TEST_F(DistributorTest, priority_config_is_propagated_to_distributor_configuration) {
    using namespace vespa::config::content::core;

    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

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

    getConfig().configure(builder);

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

TEST_F(DistributorTest, no_db_resurrection_for_bucket_not_owned_in_pending_state) {
    setupDistributor(Redundancy(1), NodeCount(10), "storage:2 distributor:2");
    lib::ClusterState newState("storage:10 distributor:10");
    auto stateCmd = std::make_shared<api::SetSystemStateCommand>(newState);
    // Force newState into being the pending state. According to the initial
    // state we own the bucket, but according to the pending state, we do
    // not. This must be handled correctly by the database update code.
    getBucketDBUpdater().onSetSystemState(stateCmd);

    document::BucketId nonOwnedBucket(16, 3);
    EXPECT_FALSE(getDistributorBucketSpace().get_bucket_ownership_flags(nonOwnedBucket).owned_in_pending_state());
    EXPECT_FALSE(getDistributorBucketSpace().check_ownership_in_pending_and_current_state(nonOwnedBucket).isOwned());

    std::vector<BucketCopy> copies;
    copies.emplace_back(1234, 0, api::BucketInfo(0x567, 1, 2));
    distributor_component().updateBucketDatabase(makeDocumentBucket(nonOwnedBucket), copies,
                                                 DatabaseUpdate::CREATE_IF_NONEXISTING);

    EXPECT_EQ("NONEXISTING", dumpBucket(nonOwnedBucket));
}

TEST_F(DistributorTest, added_db_buckets_without_gc_timestamp_implicitly_get_current_time) {
    setupDistributor(Redundancy(1), NodeCount(10), "storage:2 distributor:2");
    getClock().setAbsoluteTimeInSeconds(101234);
    document::BucketId bucket(16, 7654);

    std::vector<BucketCopy> copies;
    copies.emplace_back(1234, 0, api::BucketInfo(0x567, 1, 2));
    distributor_component().updateBucketDatabase(makeDocumentBucket(bucket), copies,
                                                 DatabaseUpdate::CREATE_IF_NONEXISTING);
    BucketDatabase::Entry e(getBucket(bucket));
    EXPECT_EQ(101234, e->getLastGarbageCollectionTime());
}

TEST_F(DistributorTest, merge_stats_are_accumulated_during_database_iteration) {
    setupDistributor(Redundancy(2), NodeCount(3), "storage:3 distributor:1");
    // Copies out of sync. Not possible for distributor to _reliably_ tell
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

    const auto& stats = distributor_maintenance_stats();
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
    auto bucketStats = distributor_bucket_spaces_stats();
    ASSERT_EQ(3, bucketStats.size());
    assertBucketSpaceStats(1, 3, 0, "default", bucketStats);
    assertBucketSpaceStats(0, 1, 1, "default", bucketStats);
    assertBucketSpaceStats(3, 1, 2, "default", bucketStats);
}

void
DistributorTest::assertBucketSpaceStats(size_t expBucketPending, size_t expBucketTotal, uint16_t node,
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
TEST_F(DistributorTest, stats_generated_for_preempted_operations) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    // For this test it suffices to have a single bucket with multiple aspects
    // wrong about it. In this case, let a bucket be both out of sync _and_
    // missing an active copy. This _should_ give a statistic with both nodes 0
    // and 1 requiring a sync. If instead merge stats generation is preempted
    // by activation, we'll see no merge stats at all.
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1,1=2/2/2");
    tickDistributorNTimes(50);
    const auto& stats = distributor_maintenance_stats();
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

TEST_F(DistributorTest, host_info_reporter_config_is_propagated_to_reporter) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    // Default is enabled=true.
    EXPECT_TRUE(distributor_host_info_reporter().isReportingEnabled());

    ConfigBuilder builder;
    builder.enableHostInfoReporting = false;
    configureDistributor(builder);

    EXPECT_FALSE(distributor_host_info_reporter().isReportingEnabled());
}

TEST_F(DistributorTest, replica_counting_mode_is_configured_to_trusted_by_default) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    EXPECT_EQ(ConfigBuilder::MinimumReplicaCountingMode::TRUSTED, currentReplicaCountingMode());
}

TEST_F(DistributorTest, replica_counting_mode_config_is_propagated_to_metric_updater) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    ConfigBuilder builder;
    builder.minimumReplicaCountingMode = ConfigBuilder::MinimumReplicaCountingMode::ANY;
    configureDistributor(builder);
    EXPECT_EQ(ConfigBuilder::MinimumReplicaCountingMode::ANY, currentReplicaCountingMode());
}

TEST_F(DistributorTest, max_consecutively_inhibited_maintenance_ticks_config_is_propagated_to_internal_config) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    ConfigBuilder builder;
    builder.maxConsecutivelyInhibitedMaintenanceTicks = 123;
    getConfig().configure(builder);
    EXPECT_EQ(getConfig().max_consecutively_inhibited_maintenance_ticks(), 123);
}

TEST_F(DistributorTest, bucket_activation_is_enabled_by_default) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    EXPECT_FALSE(getConfig().isBucketActivationDisabled());
}

TEST_F(DistributorTest, bucket_activation_config_is_propagated_to_distributor_configuration) {
    using namespace vespa::config::content::core;

    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    ConfigBuilder builder;
    builder.disableBucketActivation = true;
    getConfig().configure(builder);

    EXPECT_TRUE(getConfig().isBucketActivationDisabled());
}

void
DistributorTest::configureMaxClusterClockSkew(int seconds) {
    using namespace vespa::config::content::core;

    ConfigBuilder builder;
    builder.maxClusterClockSkewSec = seconds;
    getConfig().configure(builder);
    _distributor->enableNextConfig();
}

TEST_F(DistributorTest, max_clock_skew_config_is_propagated_to_distributor_config) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    configureMaxClusterClockSkew(5);
    EXPECT_EQ(getConfig().getMaxClusterClockSkew(), std::chrono::seconds(5));
}

namespace {

auto makeDummyRemoveCommand() {
    return std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("id:foo:testdoctype1:n=1:foo"),
            api::Timestamp(0));
}

auto make_dummy_get_command_for_bucket_1() {
    return std::make_shared<api::GetCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("id:foo:testdoctype1:n=1:foo"),
            document::AllFields::NAME);
}

}

void DistributorTest::sendDownClusterStateCommand() {
    lib::ClusterState newState("bits:1 storage:1 distributor:1");
    auto stateCmd = std::make_shared<api::SetSystemStateCommand>(newState);
    _distributor->handleMessage(stateCmd);
}

void DistributorTest::replyToSingleRequestBucketInfoCommandWith1Bucket() {
    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());
    for (uint32_t i = 0; i < _sender.commands().size(); ++i) {
        ASSERT_EQ(api::MessageType::REQUESTBUCKETINFO, _sender.command(i)->getType());
        auto& bucketReq(static_cast<api::RequestBucketInfoCommand&>
                        (*_sender.command(i)));
        auto bucketReply = bucketReq.makeReply();
        if (bucketReq.getBucketSpace() == FixedBucketSpaces::default_space()) {
            // Make sure we have a bucket to route our remove op to, or we'd get
            // an immediate reply anyway.
            dynamic_cast<api::RequestBucketInfoReply&>(*bucketReply)
                .getBucketInfo().push_back(
                        api::RequestBucketInfoReply::Entry(document::BucketId(1, 1),
                                                           api::BucketInfo(20, 10, 12, 50, 60, true, true)));
        }
        _distributor->handleMessage(std::move(bucketReply));
    }
    _sender.commands().clear();
}

void DistributorTest::sendDownDummyRemoveCommand() {
    _distributor->handleMessage(makeDummyRemoveCommand());
}

void DistributorTest::assertSingleBouncedRemoveReplyPresent() {
    ASSERT_EQ(1, _sender.replies().size()); // Rejected remove
    ASSERT_EQ(api::MessageType::REMOVE_REPLY, _sender.reply(0)->getType());
    auto& reply(static_cast<api::RemoveReply&>(*_sender.reply(0)));
    ASSERT_EQ(api::ReturnCode::STALE_TIMESTAMP, reply.getResult().getResult());
    _sender.replies().clear();
}

void DistributorTest::assertNoMessageBounced() {
    ASSERT_EQ(0, _sender.replies().size());
}

// TODO refactor this to set proper highest timestamp as part of bucket info
// reply once we have the "highest timestamp across all owned buckets" feature
// in place.
TEST_F(DistributorTest, configured_safe_time_point_rejection_works_end_to_end) {
    setupDistributor(Redundancy(2), NodeCount(2),
                     "bits:1 storage:1 distributor:2");
    getClock().setAbsoluteTimeInSeconds(1000);
    configureMaxClusterClockSkew(10);

    sendDownClusterStateCommand();
    ASSERT_NO_FATAL_FAILURE(replyToSingleRequestBucketInfoCommandWith1Bucket());
    // SetSystemStateCommand sent down chain at this point.
    sendDownDummyRemoveCommand();
    ASSERT_NO_FATAL_FAILURE(assertSingleBouncedRemoveReplyPresent());

    // Increment time to first whole second of clock + 10 seconds of skew.
    // Should now not get any feed rejections.
    getClock().setAbsoluteTimeInSeconds(1011);

    sendDownDummyRemoveCommand();
    ASSERT_NO_FATAL_FAILURE(assertNoMessageBounced());
}

void DistributorTest::configure_mutation_sequencing(bool enabled) {
    using namespace vespa::config::content::core;

    ConfigBuilder builder;
    builder.sequenceMutatingOperations = enabled;
    getConfig().configure(builder);
    _distributor->enableNextConfig();
}

TEST_F(DistributorTest, sequencing_config_is_propagated_to_distributor_config) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

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
DistributorTest::configure_merge_busy_inhibit_duration(int seconds) {
    using namespace vespa::config::content::core;

    ConfigBuilder builder;
    builder.inhibitMergeSendingOnBusyNodeDurationSec = seconds;
    getConfig().configure(builder);
    _distributor->enableNextConfig();
}

TEST_F(DistributorTest, merge_busy_inhibit_duration_config_is_propagated_to_distributor_config) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    configure_merge_busy_inhibit_duration(7);
    EXPECT_EQ(getConfig().getInhibitMergesOnBusyNodeDuration(), std::chrono::seconds(7));
}

TEST_F(DistributorTest, merge_busy_inhibit_duration_is_propagated_to_pending_message_tracker) {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:1 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    configure_merge_busy_inhibit_duration(100);
    auto cmd = makeDummyRemoveCommand(); // Remove is for bucket 1
    distributor_handle_message(cmd);

    // Should send to content node 0
    ASSERT_EQ(1, _sender.commands().size());
    ASSERT_EQ(api::MessageType::REMOVE, _sender.command(0)->getType());
    auto& fwd_cmd = dynamic_cast<api::RemoveCommand&>(*_sender.command(0));
    auto reply = fwd_cmd.makeReply();
    reply->setResult(api::ReturnCode(api::ReturnCode::BUSY));
    _distributor->handleReply(std::shared_ptr<api::StorageReply>(std::move(reply)));

    auto& node_info = _distributor->getPendingMessageTracker().getNodeInfo();

    EXPECT_TRUE(node_info.isBusy(0));
    getClock().addSecondsToTime(99);
    EXPECT_TRUE(node_info.isBusy(0));
    getClock().addSecondsToTime(2);
    EXPECT_FALSE(node_info.isBusy(0));
}

TEST_F(DistributorTest, external_client_requests_are_handled_individually_in_priority_order) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a");

    std::vector<api::StorageMessage::Priority> priorities({50, 255, 10, 40, 0});
    document::DocumentId id("id:foo:testdoctype1:n=1:foo");
    vespalib::stringref field_set = "";
    for (auto pri : priorities) {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId()), id, field_set);
        cmd->setPriority(pri);
        // onDown appends to internal message FIFO queue, awaiting hand-off.
        _distributor->onDown(cmd);
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

TEST_F(DistributorTest, internal_messages_are_started_in_fifo_order_batch) {
    // To test internal request ordering, we use NotifyBucketChangeCommand
    // for the reason that it explicitly updates the bucket database for
    // each individual invocation.
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");

    std::vector<api::StorageMessage::Priority> priorities({50, 255, 10, 40, 1});
    for (auto pri : priorities) {
        api::BucketInfo fake_info(pri, pri, pri);
        auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket), fake_info);
        cmd->setSourceIndex(0);
        cmd->setPriority(pri);
        _distributor->onDown(cmd);
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

TEST_F(DistributorTest, closing_aborts_priority_queued_client_requests) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");

    document::DocumentId id("id:foo:testdoctype1:n=1:foo");
    vespalib::stringref field_set = "";
    for (int i = 0; i < 10; ++i) {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId()), id, field_set);
        _distributor->onDown(cmd);
    }
    tickDistributorNTimes(1);
    // Closing should trigger 1 abort via startet GetOperation and 9 aborts from pri queue
    _distributor->close();
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

TEST_F(DistributorTest, entering_recovery_mode_resets_bucket_space_stats) {
    // Set up a cluster state + DB contents which implies merge maintenance ops
    setupDistributor(Redundancy(2), NodeCount(2), "version:1 distributor:1 storage:2");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a");
    addNodesToBucketDB(document::BucketId(16, 2), "0=1/1/1/t/a");
    addNodesToBucketDB(document::BucketId(16, 3), "0=2/2/2/t/a");

    tickDistributorNTimes(5); // 1/3rds into second round through database

    enableDistributorClusterState("version:2 distributor:1 storage:3 .1.s:d");
    EXPECT_TRUE(_distributor->isInRecoveryMode());
    // Bucket space stats should now be invalid per space per node, pending stats
    // from state version 2. Exposing stats from version 1 risks reporting stale
    // information back to the cluster controller.
    const auto stats = distributor_bucket_spaces_stats();
    ASSERT_EQ(2, stats.size());

    assert_invalid_stats_for_all_spaces(stats, 0);
    assert_invalid_stats_for_all_spaces(stats, 2);
}

TEST_F(DistributorTest, leaving_recovery_mode_immediately_sends_getnodestate_replies) {
    setupDistributor(Redundancy(2), NodeCount(2), "version:1 distributor:1 storage:2");
    // Should not send explicit replies during init stage
    ASSERT_EQ(0, explicit_node_state_reply_send_invocations());
    // Add a couple of buckets so we have something to iterate over
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a");
    addNodesToBucketDB(document::BucketId(16, 2), "0=1/1/1/t/a");

    enableDistributorClusterState("version:2 distributor:1 storage:3 .1.s:d");
    EXPECT_TRUE(_distributor->isInRecoveryMode());
    EXPECT_EQ(0, explicit_node_state_reply_send_invocations());
    tickDistributorNTimes(1); // DB round not yet complete
    EXPECT_EQ(0, explicit_node_state_reply_send_invocations());
    tickDistributorNTimes(2); // DB round complete after 2nd bucket + "scan done" discovery tick
    EXPECT_EQ(1, explicit_node_state_reply_send_invocations());
    EXPECT_FALSE(_distributor->isInRecoveryMode());
    // Now out of recovery mode, subsequent round completions should not send replies
    tickDistributorNTimes(10);
    EXPECT_EQ(1, explicit_node_state_reply_send_invocations());
}

void DistributorTest::do_test_pending_merge_getnodestate_reply_edge(BucketSpace space) {
    setupDistributor(Redundancy(2), NodeCount(2), "version:1 distributor:1 storage:2");
    EXPECT_TRUE(_distributor->isInRecoveryMode());
    // 2 buckets with missing replicas triggering merge pending stats
    addNodesToBucketDB(Bucket(space, BucketId(16, 1)), "0=1/1/1/t/a");
    addNodesToBucketDB(Bucket(space, BucketId(16, 2)), "0=1/1/1/t/a");
    tickDistributorNTimes(3);
    EXPECT_FALSE(_distributor->isInRecoveryMode());
    const auto space_name = FixedBucketSpaces::to_string(space);
    assertBucketSpaceStats(2, 0, 1, space_name, _distributor->getBucketSpacesStats());
    // First completed scan sends off merge stats et al to cluster controller
    EXPECT_EQ(1, explicit_node_state_reply_send_invocations());

    // Edge not triggered when 1 bucket with missing replica left
    addNodesToBucketDB(Bucket(space, BucketId(16, 1)), "0=1/1/1/t/a,1=1/1/1/t");
    tickDistributorNTimes(3);
    assertBucketSpaceStats(1, 1, 1, space_name, _distributor->getBucketSpacesStats());
    EXPECT_EQ(1, explicit_node_state_reply_send_invocations());

    // Edge triggered when no more buckets with requiring merge
    addNodesToBucketDB(Bucket(space, BucketId(16, 2)), "0=1/1/1/t/a,1=1/1/1/t");
    tickDistributorNTimes(3);
    assertBucketSpaceStats(0, 2, 1, space_name, _distributor->getBucketSpacesStats());
    EXPECT_EQ(2, explicit_node_state_reply_send_invocations());

    // Should only send when edge happens, not in subsequent DB iterations
    tickDistributorNTimes(10);
    EXPECT_EQ(2, explicit_node_state_reply_send_invocations());

    // Going back to merges pending should _not_ send a getnodestate reply (at least for now)
    addNodesToBucketDB(Bucket(space, BucketId(16, 1)), "0=1/1/1/t/a");
    tickDistributorNTimes(3);
    assertBucketSpaceStats(1, 1, 1, space_name, _distributor->getBucketSpacesStats());
    EXPECT_EQ(2, explicit_node_state_reply_send_invocations());
}

TEST_F(DistributorTest, pending_to_no_pending_default_merges_edge_immediately_sends_getnodestate_replies) {
    do_test_pending_merge_getnodestate_reply_edge(FixedBucketSpaces::default_space());
}

TEST_F(DistributorTest, pending_to_no_pending_global_merges_edge_immediately_sends_getnodestate_replies) {
    do_test_pending_merge_getnodestate_reply_edge(FixedBucketSpaces::global_space());
}

TEST_F(DistributorTest, stale_reads_config_is_propagated_to_external_operation_handler) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_stale_reads_enabled(true);
    EXPECT_TRUE(getExternalOperationHandler().concurrent_gets_enabled());

    configure_stale_reads_enabled(false);
    EXPECT_FALSE(getExternalOperationHandler().concurrent_gets_enabled());
}

TEST_F(DistributorTest, fast_path_on_consistent_gets_config_is_propagated_to_internal_config) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_update_fast_path_restart_enabled(true);
    EXPECT_TRUE(getConfig().update_fast_path_restart_enabled());

    configure_update_fast_path_restart_enabled(false);
    EXPECT_FALSE(getConfig().update_fast_path_restart_enabled());
}

TEST_F(DistributorTest, merge_disabling_config_is_propagated_to_internal_config) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_merge_operations_disabled(true);
    EXPECT_TRUE(getConfig().merge_operations_disabled());

    configure_merge_operations_disabled(false);
    EXPECT_FALSE(getConfig().merge_operations_disabled());
}

TEST_F(DistributorTest, metadata_update_phase_config_is_propagated_to_internal_config) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_metadata_update_phase_enabled(true);
    EXPECT_TRUE(getConfig().enable_metadata_only_fetch_phase_for_inconsistent_updates());

    configure_metadata_update_phase_enabled(false);
    EXPECT_FALSE(getConfig().enable_metadata_only_fetch_phase_for_inconsistent_updates());
}

TEST_F(DistributorTest, weak_internal_read_consistency_config_is_propagated_to_internal_configs) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_use_weak_internal_read_consistency(true);
    EXPECT_TRUE(getConfig().use_weak_internal_read_consistency_for_client_gets());
    EXPECT_TRUE(getExternalOperationHandler().use_weak_internal_read_consistency_for_gets());

    configure_use_weak_internal_read_consistency(false);
    EXPECT_FALSE(getConfig().use_weak_internal_read_consistency_for_client_gets());
    EXPECT_FALSE(getExternalOperationHandler().use_weak_internal_read_consistency_for_gets());
}

void DistributorTest::set_up_and_start_get_op_with_stale_reads_enabled(bool enabled) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");
    configure_stale_reads_enabled(enabled);

    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");
    _distributor->onDown(make_dummy_get_command_for_bucket_1());
}

TEST_F(DistributorTest, gets_are_started_outside_main_distributor_logic_if_stale_reads_enabled) {
    set_up_and_start_get_op_with_stale_reads_enabled(true);
    ASSERT_THAT(_sender.commands(), SizeIs(1));
    EXPECT_THAT(_sender.replies(), SizeIs(0));

    // Reply is routed to the correct owner
    auto reply = std::shared_ptr<api::StorageReply>(_sender.command(0)->makeReply());
    _distributor->onDown(reply);
    ASSERT_THAT(_sender.commands(), SizeIs(1));
    EXPECT_THAT(_sender.replies(), SizeIs(1));
}

TEST_F(DistributorTest, gets_are_not_started_outside_main_distributor_logic_if_stale_reads_disabled) {
    set_up_and_start_get_op_with_stale_reads_enabled(false);
    // Get has been placed into distributor queue, so no external messages are produced.
    EXPECT_THAT(_sender.commands(), SizeIs(0));
    EXPECT_THAT(_sender.replies(), SizeIs(0));
}

// There's no need or desire to track "lockfree" Gets in the main pending message tracker,
// as we only have to track mutations to inhibit maintenance ops safely. Furthermore,
// the message tracker is a multi-index and therefore has some runtime cost.
TEST_F(DistributorTest, gets_started_outside_main_thread_are_not_tracked_by_main_pending_message_tracker) {
    set_up_and_start_get_op_with_stale_reads_enabled(true);
    Bucket bucket(FixedBucketSpaces::default_space(), BucketId(16, 1));
    EXPECT_FALSE(_distributor->getPendingMessageTracker().hasPendingMessage(
            0, bucket, api::MessageType::GET_ID));
}

TEST_F(DistributorTest, closing_aborts_gets_started_outside_main_distributor_thread) {
    set_up_and_start_get_op_with_stale_reads_enabled(true);
    _distributor->close();
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ(api::ReturnCode::ABORTED, _sender.reply(0)->getResult().getResult());
}

TEST_F(DistributorTest, prioritize_global_bucket_merges_config_is_propagated_to_internal_config) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_prioritize_global_bucket_merges(true);
    EXPECT_TRUE(getConfig().prioritize_global_bucket_merges());

    configure_prioritize_global_bucket_merges(false);
    EXPECT_FALSE(getConfig().prioritize_global_bucket_merges());
}

TEST_F(DistributorTest, max_activation_inhibited_out_of_sync_groups_config_is_propagated_to_internal_config) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    configure_max_activation_inhibited_out_of_sync_groups(3);
    EXPECT_EQ(getConfig().max_activation_inhibited_out_of_sync_groups(), 3);

    configure_max_activation_inhibited_out_of_sync_groups(0);
    EXPECT_EQ(getConfig().max_activation_inhibited_out_of_sync_groups(), 0);
}

TEST_F(DistributorTest, wanted_split_bit_count_is_lower_bounded) {
    createLinks();
    setupDistributor(Redundancy(1), NodeCount(1), "distributor:1 storage:1");

    ConfigBuilder builder;
    builder.minsplitcount = 7;
    configureDistributor(builder);

    EXPECT_EQ(getConfig().getMinimalBucketSplit(), 8);
}

}
