// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <tests/distributor/top_level_distributor_test_util.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributor_status.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/storage/distributor/distributor_stripe_thread.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/asciistream.h>
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

struct TopLevelDistributorTest : Test, TopLevelDistributorTestUtil {
    TopLevelDistributorTest();
    ~TopLevelDistributorTest() override;

    void SetUp() override {
        create_links();
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

    std::string resolve_stripe_operation_routing(std::shared_ptr<api::StorageMessage> msg) {
        handle_top_level_message(msg);

        vespalib::asciistream posted_msgs;
        auto stripes = distributor_stripes();
        for (size_t i = 0; i < stripes.size(); ++i) {
            // TODO less intrusive, this is brittle.
            for (auto& qmsg : stripes[i]->_messageQueue) {
                posted_msgs << "Stripe " << i << ": " << MessageSenderStub::dumpMessage(*qmsg, false, false);
            }
            stripes[i]->_messageQueue.clear();
        }
        return posted_msgs.str();
    }

    void tick_distributor_n_times(uint32_t n) {
        for (uint32_t i = 0; i < n; ++i) {
            tick();
        }
    }

    StatusReporterDelegate& distributor_status_delegate() {
        return _distributor->_distributorStatusDelegate;
    }

    framework::TickingThreadPool& distributor_thread_pool() {
        return _distributor->_threadPool;
    }

    const std::vector<std::shared_ptr<DistributorStatus>>& distributor_status_todos() {
        return _distributor->_status_to_do;
    }

    Distributor::MetricUpdateHook distributor_metric_update_hook() {
        return _distributor->_metricUpdateHook;
    }
};

TopLevelDistributorTest::TopLevelDistributorTest()
    : Test(),
      TopLevelDistributorTestUtil()
{
}

TopLevelDistributorTest::~TopLevelDistributorTest() = default;

TEST_F(TopLevelDistributorTest, external_operation_is_routed_to_expected_stripe) {
    setup_distributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    auto op = std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId()),
            document::DocumentId("id:m:test:n=1:foo"),
            api::Timestamp(1234));

    // We expect stripe mapping to be deterministic.
    EXPECT_EQ("Stripe 2: Remove", resolve_stripe_operation_routing(op));

    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "foo", "bar", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 1234));
    cmd->addBucketToBeVisited(document::BucketId());

    EXPECT_EQ("Stripe 1: Visitor Create", resolve_stripe_operation_routing(cmd));
}

TEST_F(TopLevelDistributorTest, recovery_mode_on_cluster_state_change_is_triggered_across_all_stripes) {
    setup_distributor(Redundancy(1), NodeCount(2),
                      "storage:1 .0.s:d distributor:1");
    enable_distributor_cluster_state("storage:1 distributor:1");

    EXPECT_TRUE(all_distributor_stripes_are_in_recovery_mode());
    tick();
    EXPECT_FALSE(all_distributor_stripes_are_in_recovery_mode());

    enable_distributor_cluster_state("storage:2 distributor:1");
    EXPECT_TRUE(all_distributor_stripes_are_in_recovery_mode());
}

// TODO STRIPE consider moving to generic test, not specific to top-level distributor or stripe
TEST_F(TopLevelDistributorTest, contains_time_statement) {
    setup_distributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    auto cfg = _component->total_distributor_config_sp();
    EXPECT_FALSE(cfg->containsTimeStatement(""));
    EXPECT_FALSE(cfg->containsTimeStatement("testdoctype1"));
    EXPECT_FALSE(cfg->containsTimeStatement("testdoctype1.headerfield > 42"));
    EXPECT_TRUE(cfg->containsTimeStatement("testdoctype1.headerfield > now()"));
    EXPECT_TRUE(cfg->containsTimeStatement("testdoctype1.headerfield > now() - 3600"));
    EXPECT_TRUE(cfg->containsTimeStatement("testdoctype1.headerfield == now() - 3600"));
}

TEST_F(TopLevelDistributorTest, config_changes_are_propagated_to_all_stripes) {
    setup_distributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    for (auto* s : distributor_stripes()) {
        ASSERT_NE(s->getConfig().getSplitCount(), 1234);
        ASSERT_NE(s->getConfig().getJoinCount(), 123);
    }

    auto cfg = current_distributor_config();
    cfg.splitcount = 1234;
    cfg.joincount = 123;
    reconfigure(cfg);

    for (auto* s : distributor_stripes()) {
        ASSERT_EQ(s->getConfig().getSplitCount(), 1234);
        ASSERT_EQ(s->getConfig().getJoinCount(), 123);
    }
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

TEST_F(TopLevelDistributorTest, tick_aggregates_status_requests_from_all_stripes) {
    setup_distributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    ASSERT_NE(stripe_of_bucket(document::BucketId(16, 1)),
              stripe_of_bucket(document::BucketId(16, 2)));

    add_nodes_to_stripe_bucket_db(document::BucketId(16, 1), "0=1/1/1/t");
    add_nodes_to_stripe_bucket_db(document::BucketId(16, 2), "0=2/2/2/t");

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

    // Result contains buckets from DBs of multiple stripes.
    EXPECT_THAT(thread.getResult(), HasSubstr("BucketId(0x4000000000000001)"));
    EXPECT_THAT(thread.getResult(), HasSubstr("BucketId(0x4000000000000002)"));
}

TEST_F(TopLevelDistributorTest, metric_update_hook_updates_pending_maintenance_metrics) {
    setup_distributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    // To ensure we count all operations, not just those fitting within the pending window.
    auto cfg = current_distributor_config();
    cfg.maxpendingidealstateoperations = 1; // FIXME STRIPE this does not actually seem to be used...!
    reconfigure(cfg);

    // 1 bucket must be merged, 1 must be split, 1 should be activated.
    add_nodes_to_stripe_bucket_db(document::BucketId(16, 1), "0=2/2/2/t/a,1=1/1/1");
    add_nodes_to_stripe_bucket_db(document::BucketId(16, 2), "0=100/10000000/200000/t/a,1=100/10000000/200000/t");
    add_nodes_to_stripe_bucket_db(document::BucketId(16, 3), "0=200/300/400/t,1=200/300/400/t");

    // Go many full scanner rounds to check that metrics are set, not added to existing.
    tick_distributor_n_times(50);

    // By this point, no hook has been called so the metrics have not been set.
    using MO = MaintenanceOperation;
    {
        const IdealStateMetricSet& metrics = total_ideal_state_metrics();
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
        const IdealStateMetricSet& metrics = total_ideal_state_metrics();
        EXPECT_EQ(1, metrics.operations[MO::MERGE_BUCKET]->pending.getLast());
        EXPECT_EQ(1, metrics.operations[MO::SPLIT_BUCKET]->pending.getLast());
        EXPECT_EQ(1, metrics.operations[MO::SET_BUCKET_STATE]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::DELETE_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::JOIN_BUCKET]->pending.getLast());
        EXPECT_EQ(0, metrics.operations[MO::GARBAGE_COLLECTION]->pending.getLast());
    }
}

}
