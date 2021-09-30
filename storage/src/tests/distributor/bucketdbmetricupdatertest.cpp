// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>
#include <sstream>

namespace storage::distributor {

using document::BucketId;
using namespace ::testing;

struct BucketDBMetricUpdaterTest : Test {
    void visitBucketWith2Copies1Trusted(BucketDBMetricUpdater& metricUpdater);
    void visitBucketWith2CopiesBothTrusted(
            BucketDBMetricUpdater& metricUpdater);
    void visitBucketWith1Copy(BucketDBMetricUpdater& metricUpdater);

    using NodeToReplicasMap = std::unordered_map<uint16_t, uint32_t>;
    NodeToReplicasMap replicaStatsOf(BucketDBMetricUpdater& metricUpdater);

    BucketDBMetricUpdaterTest();
};

BucketDBMetricUpdaterTest::BucketDBMetricUpdaterTest()
{
}

namespace {

void addNode(BucketInfo& info, uint16_t node, uint32_t crc) {
    auto apiInfo = api::BucketInfo(crc, crc + 1, crc + 2);
    std::vector<uint16_t> order;
    info.addNode(BucketCopy(1234, node, apiInfo), order);
}

using Trusted = bool;

BucketInfo
makeInfo(uint32_t copy0Crc)
{
    BucketInfo info;
    addNode(info, 0, copy0Crc);
    return info;
}

BucketInfo
makeInfo(uint32_t copy0Crc, uint32_t copy1Crc)
{
    BucketInfo info;
    addNode(info, 0, copy0Crc);
    addNode(info, 1, copy1Crc);
    return info;
}

}  // anonymous namespace

TEST_F(BucketDBMetricUpdaterTest, doc_and_byte_counts_are_updated) {
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    EXPECT_FALSE(metricUpdater.hasCompletedRound());

    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    metricUpdater.completeRound(false);

    EXPECT_TRUE(metricUpdater.hasCompletedRound());

    EXPECT_EQ(0, dms.docsStored.getLast());
    EXPECT_EQ(0, dms.bytesStored.getLast());
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(10));
        metricUpdater.visit(e, 1);
    }

    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_TRUE(metricUpdater.hasCompletedRound());

    EXPECT_EQ(11, dms.docsStored.getLast());
    EXPECT_EQ(12, dms.bytesStored.getLast());

    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(20));
        metricUpdater.visit(e, 1);
    }

    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(32, dms.docsStored.getLast());
    EXPECT_EQ(34, dms.bytesStored.getLast());
}

TEST_F(BucketDBMetricUpdaterTest, bucket_db_memory_usage_metrics_are_updated) {
    BucketDBMetricUpdater metric_updater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    vespalib::MemoryUsage mem_usage;
    mem_usage.incAllocatedBytes(1000);
    mem_usage.incDeadBytes(700);
    metric_updater.update_db_memory_usage(mem_usage, true);

    mem_usage.incAllocatedBytes(500);
    mem_usage.incDeadBytes(100);
    metric_updater.update_db_memory_usage(mem_usage, false);

    metric_updater.completeRound(false);
    metric_updater.getLastCompleteStats().propagateMetrics(ims, dms);

    auto* m = dms.mutable_dbs.memory_usage.getMetric("allocated_bytes");
    ASSERT_TRUE(m != nullptr);
    EXPECT_EQ(m->getLongValue("last"), 1000);

    m = dms.mutable_dbs.memory_usage.getMetric("dead_bytes");
    ASSERT_TRUE(m != nullptr);
    EXPECT_EQ(m->getLongValue("last"), 700);

    m = dms.read_only_dbs.memory_usage.getMetric("allocated_bytes");
    ASSERT_TRUE(m != nullptr);
    EXPECT_EQ(m->getLongValue("last"), 1500);

    m = dms.read_only_dbs.memory_usage.getMetric("dead_bytes");
    ASSERT_TRUE(m != nullptr);
    EXPECT_EQ(m->getLongValue("last"), 800);
}

TEST_F(BucketDBMetricUpdaterTest, buckets_with_too_few_and_too_many_copies) {
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    metricUpdater.completeRound();
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    EXPECT_EQ(0, ims.buckets_toofewcopies.getLast());
    EXPECT_EQ(0, ims.buckets_toomanycopies.getLast());
    EXPECT_EQ(0, ims.buckets.getLast());

    // 1 copy too little
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(10));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(1, ims.buckets_toofewcopies.getLast());
    EXPECT_EQ(0, ims.buckets_toomanycopies.getLast());
    EXPECT_EQ(1, ims.buckets.getLast());

    // 1 copy too many
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(40, 40));
        metricUpdater.visit(e, 1);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(1, ims.buckets_toofewcopies.getLast());
    EXPECT_EQ(1, ims.buckets_toomanycopies.getLast());
    EXPECT_EQ(2, ims.buckets.getLast());

    // Right amount of copies, just inc bucket counter.
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(40, 40));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(1, ims.buckets_toofewcopies.getLast());
    EXPECT_EQ(1, ims.buckets_toomanycopies.getLast());
    EXPECT_EQ(3, ims.buckets.getLast());
}

TEST_F(BucketDBMetricUpdaterTest, buckets_with_varying_trustedness) {
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    EXPECT_EQ(0, ims.buckets_notrusted.getLast());
    // Has only trusted (implicit for first added)
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(100));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    EXPECT_EQ(0, ims.buckets_notrusted.getLast());
    // Has at least one trusted (implicit for first added)
    {
        BucketDatabase::Entry e(document::BucketId(16, 2), makeInfo(100, 200));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    EXPECT_EQ(0, ims.buckets_notrusted.getLast());
    // Has no trusted
    {
        BucketInfo info(makeInfo(100, 200));
        info.resetTrusted();
        BucketDatabase::Entry e(document::BucketId(16, 3), info);
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    EXPECT_EQ(1, ims.buckets_notrusted.getLast());
}

TEST_F(BucketDBMetricUpdaterTest, pick_counts_from_trusted_copy) {
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    // First copy added is implicitly trusted, but it is not the largest.
    BucketDatabase::Entry e(document::BucketId(16, 2), makeInfo(100, 200));
    metricUpdater.visit(e, 2);
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(101, dms.docsStored.getLast());
    EXPECT_EQ(102, dms.bytesStored.getLast());
}

TEST_F(BucketDBMetricUpdaterTest, pick_largest_copy_if_no_trusted) {
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    // No trusted copies, so must pick second copy.
    BucketInfo info(makeInfo(100, 200));
    info.resetTrusted();
    BucketDatabase::Entry e(document::BucketId(16, 2), info);
    metricUpdater.visit(e, 2);
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(201, dms.docsStored.getLast());
    EXPECT_EQ(202, dms.bytesStored.getLast());
}

TEST_F(BucketDBMetricUpdaterTest, complete_round_clears_working_state) {
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms;

    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(10));
        metricUpdater.visit(e, 1);
    }
    metricUpdater.completeRound();
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(11, dms.docsStored.getLast());
    // Completing the round again with no visits having been done will
    // propagate an empty working state to the complete state.
    metricUpdater.completeRound();
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    EXPECT_EQ(0, dms.docsStored.getLast());
}

// Replicas on nodes 0 and 1.
void
BucketDBMetricUpdaterTest::visitBucketWith2Copies1Trusted(
        BucketDBMetricUpdater& metricUpdater)
{
    BucketInfo info;
    addNode(info, 0, 100);
    addNode(info, 1, 101);  // Note different checksums => #trusted = 1
    BucketDatabase::Entry e(document::BucketId(16, 1), info);
    metricUpdater.visit(e, 2);
}

// Replicas on nodes 0 and 2.
void
BucketDBMetricUpdaterTest::visitBucketWith2CopiesBothTrusted(
        BucketDBMetricUpdater& metricUpdater)
{
    BucketInfo info;
    addNode(info, 0, 200);
    addNode(info, 2, 200);
    BucketDatabase::Entry e(document::BucketId(16, 2), info);
    metricUpdater.visit(e, 2);
}

// Single replica on node 2.
void
BucketDBMetricUpdaterTest::visitBucketWith1Copy(
        BucketDBMetricUpdater& metricUpdater)
{
    BucketInfo info;
    addNode(info, 2, 100);
    BucketDatabase::Entry e(document::BucketId(16, 1), info);
    metricUpdater.visit(e, 2);
}

BucketDBMetricUpdaterTest::NodeToReplicasMap
BucketDBMetricUpdaterTest::replicaStatsOf(BucketDBMetricUpdater& metricUpdater)
{
    metricUpdater.completeRound(true);
    return metricUpdater.getLastCompleteStats()._minBucketReplica;
}

TEST_F(BucketDBMetricUpdaterTest, min_bucket_replica_tracked_and_reported_per_node) {
    BucketDBMetricUpdater metricUpdater;

    // Node 0 and 1 should have min replica 1, while node 2 should have min
    // replica 2.
    visitBucketWith2Copies1Trusted(metricUpdater);
    visitBucketWith2CopiesBothTrusted(metricUpdater);

    EXPECT_EQ(NodeToReplicasMap({{0, 1}, {1, 1}, {2, 2}}),
              replicaStatsOf(metricUpdater));
}

TEST_F(BucketDBMetricUpdaterTest, non_trusted_replicas_also_counted_in_mode_any) {
    BucketDBMetricUpdater metricUpdater;
    using CountingMode = BucketDBMetricUpdater::ReplicaCountingMode;
    metricUpdater.setMinimumReplicaCountingMode(CountingMode::ANY);
    visitBucketWith2Copies1Trusted(metricUpdater);
    visitBucketWith2CopiesBothTrusted(metricUpdater);

    EXPECT_EQ(NodeToReplicasMap({{0, 2}, {1, 2}, {2, 2}}),
              replicaStatsOf(metricUpdater));
}

TEST_F(BucketDBMetricUpdaterTest, minimum_replica_count_returned_for_node_in_mode_any) {
    BucketDBMetricUpdater metricUpdater;
    using CountingMode = BucketDBMetricUpdater::ReplicaCountingMode;
    metricUpdater.setMinimumReplicaCountingMode(CountingMode::ANY);
    visitBucketWith2CopiesBothTrusted(metricUpdater);
    visitBucketWith1Copy(metricUpdater);

    // Node 2 has a bucket with only 1 replica.
    EXPECT_EQ(NodeToReplicasMap({{0, 2}, {2, 1}}),
              replicaStatsOf(metricUpdater));
}

} // storage::distributor
