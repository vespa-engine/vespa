// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/maintenancemocks.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storage/distributor/maintenance/simplemaintenancescanner.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using document::BucketId;
using document::test::makeBucketSpace;
using Priority = MaintenancePriority;
using namespace ::testing;
using namespace std::chrono_literals;

struct SimpleMaintenanceScannerTest : Test {
    using PendingStats = SimpleMaintenanceScanner::PendingMaintenanceStats;

    std::unique_ptr<MockMaintenancePriorityGenerator> _priorityGenerator;
    std::unique_ptr<DistributorBucketSpaceRepo> _bucketSpaceRepo;
    std::unique_ptr<SimpleBucketPriorityDatabase> _priorityDb;
    std::unique_ptr<SimpleMaintenanceScanner> _scanner;

    void addBucketToDb(document::BucketSpace bucketSpace, int bucketNum);
    void addBucketToDb(int bucketNum);
    bool scanEntireDatabase(int expected);
    std::string stringifyGlobalPendingStats(const PendingStats& stats) const;

    void SetUp() override;
};

void
SimpleMaintenanceScannerTest::SetUp()
{
    _priorityGenerator = std::make_unique<MockMaintenancePriorityGenerator>();
    _bucketSpaceRepo = std::make_unique<DistributorBucketSpaceRepo>(0u);
    _priorityDb = std::make_unique<SimpleBucketPriorityDatabase>();
    _scanner = std::make_unique<SimpleMaintenanceScanner>(*_priorityDb, *_priorityGenerator, *_bucketSpaceRepo);
}

void
SimpleMaintenanceScannerTest::addBucketToDb(document::BucketSpace bucketSpace, int bucketNum)
{
    BucketDatabase::Entry entry(BucketId(16, bucketNum), BucketInfo());
    auto& bucketDb(_bucketSpaceRepo->get(bucketSpace).getBucketDatabase());
    bucketDb.update(entry);
}

void
SimpleMaintenanceScannerTest::addBucketToDb(int bucketNum)
{
    addBucketToDb(makeBucketSpace(), bucketNum);
}

std::string
SimpleMaintenanceScannerTest::stringifyGlobalPendingStats(
        const PendingStats& stats) const
{
    std::ostringstream ss;
    ss << stats.global;
    return ss.str();
}

TEST_F(SimpleMaintenanceScannerTest, prioritize_single_bucket) {
    addBucketToDb(1);
    std::string expected("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri VERY_HIGH)\n");

    auto scanResult = _scanner->scanNext();
    ASSERT_FALSE(scanResult.isDone());
    EXPECT_EQ(makeBucketSpace().getId(), scanResult.getBucketSpace().getId());
    EXPECT_EQ(expected, _priorityDb->toString());

    ASSERT_TRUE(_scanner->scanNext().isDone());
    EXPECT_EQ(expected, _priorityDb->toString());
}

TEST_F(SimpleMaintenanceScannerTest, prioritize_single_bucket_alt_bucket_space) {
    document::BucketSpace bucketSpace(4);
    _bucketSpaceRepo->add(bucketSpace, std::make_unique<DistributorBucketSpace>());
    (void)_scanner->fetch_and_reset();
    addBucketToDb(bucketSpace, 1);
    std::string expected("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000004), BucketId(0x4000000000000001)), pri VERY_HIGH)\n");

    auto scanResult = _scanner->scanNext();
    ASSERT_FALSE(scanResult.isDone());
    EXPECT_EQ(bucketSpace.getId(), scanResult.getBucketSpace().getId());
    EXPECT_EQ(expected, _priorityDb->toString());

    ASSERT_TRUE(_scanner->scanNext().isDone());
    EXPECT_EQ(expected, _priorityDb->toString());
}

namespace {

std::string sortLines(const std::string& source) {
    vespalib::StringTokenizer st(source,"\n","");
    std::vector<std::string> lines;
    std::copy(st.begin(), st.end(), std::back_inserter(lines));
    std::sort(lines.begin(), lines.end());
    std::ostringstream ost;
    for (auto& line : lines) {
        ost << line << "\n";
    }
    return ost.str();
}

}

TEST_F(SimpleMaintenanceScannerTest, prioritize_multiple_buckets) {
    addBucketToDb(1);
    addBucketToDb(2);
    addBucketToDb(3);
    std::string expected("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri VERY_HIGH)\n"
                         "PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000002)), pri VERY_HIGH)\n"
                         "PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000003)), pri VERY_HIGH)\n");

    ASSERT_TRUE(scanEntireDatabase(3));
    EXPECT_EQ(sortLines(expected),
              sortLines(_priorityDb->toString()));
}

bool
SimpleMaintenanceScannerTest::scanEntireDatabase(int expected)
{
    for (int i = 0; i < expected; ++i) {
        if (_scanner->scanNext().isDone()) {
            return false;
        }
    }
    return _scanner->scanNext().isDone();
}

TEST_F(SimpleMaintenanceScannerTest, reset) {
    addBucketToDb(1);
    addBucketToDb(3);

    ASSERT_TRUE(scanEntireDatabase(2));
    std::string expected("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri VERY_HIGH)\n"
                         "PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000003)), pri VERY_HIGH)\n");
    EXPECT_EQ(expected, _priorityDb->toString());

    addBucketToDb(2);
    ASSERT_TRUE(scanEntireDatabase(0));
    EXPECT_EQ(expected, _priorityDb->toString());

    (void)_scanner->fetch_and_reset();
    ASSERT_TRUE(scanEntireDatabase(3));

    expected = "PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri VERY_HIGH)\n"
               "PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000002)), pri VERY_HIGH)\n"
               "PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000003)), pri VERY_HIGH)\n";
    EXPECT_EQ(sortLines(expected), sortLines(_priorityDb->toString()));
}

TEST_F(SimpleMaintenanceScannerTest, pending_maintenance_operation_statistics) {
    addBucketToDb(1);
    addBucketToDb(3);

    std::string expectedEmpty("delete bucket: 0, merge bucket: 0, "
                              "split bucket: 0, join bucket: 0, "
                              "set bucket state: 0, garbage collection: 0");
    {
        const auto & stats = _scanner->getPendingMaintenanceStats();
        EXPECT_EQ(expectedEmpty, stringifyGlobalPendingStats(stats));
    }

    ASSERT_TRUE(scanEntireDatabase(2));

    // All mock operations generated have the merge type.
    {
        const auto & stats = _scanner->getPendingMaintenanceStats();
        std::string expected("delete bucket: 0, merge bucket: 2, "
                             "split bucket: 0, join bucket: 0, "
                             "set bucket state: 0, garbage collection: 0");
        EXPECT_EQ(expected, stringifyGlobalPendingStats(stats));
    }

    (void)_scanner->fetch_and_reset();
    {
        const auto & stats = _scanner->getPendingMaintenanceStats();
        EXPECT_EQ(expectedEmpty, stringifyGlobalPendingStats(stats));
    }
}

TEST_F(SimpleMaintenanceScannerTest, per_node_maintenance_stats_are_tracked) {
    addBucketToDb(1);
    addBucketToDb(3);
    {
        const auto & stats = _scanner->getPendingMaintenanceStats();
        NodeMaintenanceStats emptyStats;
        EXPECT_EQ(emptyStats, stats.perNodeStats.forNode(0, makeBucketSpace()));
    }
    ASSERT_TRUE(scanEntireDatabase(2));
    // Mock is currently hardwired to increment movingOut for node 1 and
    // copyingIn for node 2 per bucket iterated (we've got 2).
        const auto & stats = _scanner->getPendingMaintenanceStats();
    {
        NodeMaintenanceStats wantedNode1Stats;
        wantedNode1Stats.movingOut = 2;
        EXPECT_EQ(wantedNode1Stats, stats.perNodeStats.forNode(1, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wantedNode2Stats;
        wantedNode2Stats.copyingIn = 2;
        EXPECT_EQ(wantedNode2Stats, stats.perNodeStats.forNode(2, makeBucketSpace()));
    }
}

TEST_F(SimpleMaintenanceScannerTest, merge_node_maintenance_stats) {

    NodeMaintenanceStats stats_a;
    stats_a.movingOut = 1;
    stats_a.syncing = 2;
    stats_a.copyingIn = 3;
    stats_a.copyingOut = 4;
    stats_a.total = 5;

    NodeMaintenanceStats stats_b;
    stats_b.movingOut = 10;
    stats_b.syncing = 20;
    stats_b.copyingIn = 30;
    stats_b.copyingOut = 40;
    stats_b.total = 50;

    NodeMaintenanceStats result;
    result.merge(stats_a);
    result.merge(stats_b);

    NodeMaintenanceStats exp;
    exp.movingOut = 11;
    exp.syncing = 22;
    exp.copyingIn = 33;
    exp.copyingOut = 44;
    exp.total = 55;
    EXPECT_EQ(exp, result);
}

TEST_F(SimpleMaintenanceScannerTest, merge_pending_maintenance_stats) {
    auto default_space = document::FixedBucketSpaces::default_space();
    auto global_space = document::FixedBucketSpaces::global_space();

    PendingStats stats_a;
    stats_a.global.pending[MaintenanceOperation::DELETE_BUCKET] = 1;
    stats_a.global.pending[MaintenanceOperation::MERGE_BUCKET] = 2;
    stats_a.global.pending[MaintenanceOperation::SPLIT_BUCKET] = 3;
    stats_a.global.pending[MaintenanceOperation::JOIN_BUCKET] = 4;
    stats_a.global.pending[MaintenanceOperation::SET_BUCKET_STATE] = 5;
    stats_a.global.pending[MaintenanceOperation::GARBAGE_COLLECTION] = 6;
    stats_a.perNodeStats.incMovingOut(3, default_space);
    stats_a.perNodeStats.incSyncing(3, global_space);
    stats_a.perNodeStats.incCopyingIn(5, default_space);
    stats_a.perNodeStats.incCopyingOut(5, global_space);
    stats_a.perNodeStats.incTotal(5, default_space);
    stats_a.perNodeStats.update_observed_time_since_last_gc(100s);

    PendingStats stats_b;
    stats_b.global.pending[MaintenanceOperation::DELETE_BUCKET] = 10;
    stats_b.global.pending[MaintenanceOperation::MERGE_BUCKET] = 20;
    stats_b.global.pending[MaintenanceOperation::SPLIT_BUCKET] = 30;
    stats_b.global.pending[MaintenanceOperation::JOIN_BUCKET] = 40;
    stats_b.global.pending[MaintenanceOperation::SET_BUCKET_STATE] = 50;
    stats_b.global.pending[MaintenanceOperation::GARBAGE_COLLECTION] = 60;
    stats_b.perNodeStats.incMovingOut(7, default_space);
    stats_b.perNodeStats.incSyncing(7, global_space);
    stats_b.perNodeStats.incCopyingIn(5, default_space);
    stats_b.perNodeStats.incCopyingOut(5, global_space);
    stats_b.perNodeStats.incTotal(5, default_space);
    stats_b.perNodeStats.update_observed_time_since_last_gc(300s);

    PendingStats result;
    result.merge(stats_a);
    result.merge(stats_b);

    PendingStats exp;
    exp.global.pending[MaintenanceOperation::DELETE_BUCKET] = 11;
    exp.global.pending[MaintenanceOperation::MERGE_BUCKET] = 22;
    exp.global.pending[MaintenanceOperation::SPLIT_BUCKET] = 33;
    exp.global.pending[MaintenanceOperation::JOIN_BUCKET] = 44;
    exp.global.pending[MaintenanceOperation::SET_BUCKET_STATE] = 55;
    exp.global.pending[MaintenanceOperation::GARBAGE_COLLECTION] = 66;
    exp.perNodeStats.incMovingOut(3, default_space);
    exp.perNodeStats.incSyncing(3, global_space);
    exp.perNodeStats.incCopyingIn(5, default_space);
    exp.perNodeStats.incCopyingIn(5, default_space);
    exp.perNodeStats.incCopyingOut(5, global_space);
    exp.perNodeStats.incCopyingOut(5, global_space);
    exp.perNodeStats.incTotal(5, default_space);
    exp.perNodeStats.incTotal(5, default_space);
    exp.perNodeStats.incMovingOut(7, default_space);
    exp.perNodeStats.incSyncing(7, global_space);
    exp.perNodeStats.update_observed_time_since_last_gc(300s);
    EXPECT_EQ(exp.global, result.global);
    EXPECT_EQ(exp.perNodeStats, result.perNodeStats);
}

TEST_F(SimpleMaintenanceScannerTest, empty_bucket_db_is_immediately_done_by_default) {
    auto res = _scanner->scanNext();
    EXPECT_TRUE(res.isDone());
    (void)_scanner->fetch_and_reset();
    res = _scanner->scanNext();
    EXPECT_TRUE(res.isDone());
}

}
