// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <string>
#include <sstream>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/config/config-stor-distributormanager.h>

namespace storage {
namespace distributor {

using document::BucketId;

class BucketDBMetricUpdaterTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(BucketDBMetricUpdaterTest);
    CPPUNIT_TEST(testDocAndByteCountsAreUpdated);
    CPPUNIT_TEST(testBucketsWithTooFewAndTooManyCopies);
    CPPUNIT_TEST(testBucketsWithVaryingTrustedness);
    CPPUNIT_TEST(testPickCountsFromTrustedCopy);
    CPPUNIT_TEST(testPickLargestCopyIfNoTrusted);
    CPPUNIT_TEST(testCompleteRoundClearsWorkingState);
    CPPUNIT_TEST(testMinBucketReplicaTrackedAndReportedPerNode);
    CPPUNIT_TEST(nonTrustedReplicasAlsoCountedInModeAny);
    CPPUNIT_TEST(minimumReplicaCountReturnedForNodeInModeAny);
    CPPUNIT_TEST_SUITE_END();

    void visitBucketWith2Copies1Trusted(BucketDBMetricUpdater& metricUpdater);
    void visitBucketWith2CopiesBothTrusted(
            BucketDBMetricUpdater& metricUpdater);
    void visitBucketWith1Copy(BucketDBMetricUpdater& metricUpdater);


    using NodeToReplicasMap = std::unordered_map<uint16_t, uint32_t>;
    NodeToReplicasMap replicaStatsOf(BucketDBMetricUpdater& metricUpdater);

    metrics::LoadTypeSet _loadTypes;
public:
    BucketDBMetricUpdaterTest();

    void testDocAndByteCountsAreUpdated();
    void testBucketsWithTooFewAndTooManyCopies();
    void testBucketsWithVaryingTrustedness();
    void testPickCountsFromTrustedCopy();
    void testPickLargestCopyIfNoTrusted();
    void testCompleteRoundClearsWorkingState();
    void testMinBucketReplicaTrackedAndReportedPerNode();
    void nonTrustedReplicasAlsoCountedInModeAny();
    void minimumReplicaCountReturnedForNodeInModeAny();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketDBMetricUpdaterTest);

BucketDBMetricUpdaterTest::BucketDBMetricUpdaterTest()
{
    _loadTypes.push_back(metrics::LoadType(0, "foo"));
}

namespace {

void addNode(BucketInfo& info, uint16_t node, uint32_t crc) {
    auto apiInfo = api::BucketInfo(crc, crc + 1, crc + 2);
    std::vector<uint16_t> order;
    info.addNode(BucketCopy(1234, node, apiInfo), order);
}

typedef bool Trusted;

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

void
BucketDBMetricUpdaterTest::testDocAndByteCountsAreUpdated()
{
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms(_loadTypes);

    CPPUNIT_ASSERT_EQUAL(false, metricUpdater.hasCompletedRound());

    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    metricUpdater.completeRound(false);

    CPPUNIT_ASSERT_EQUAL(true, metricUpdater.hasCompletedRound());

    CPPUNIT_ASSERT_EQUAL(int64_t(0), dms.docsStored.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), dms.bytesStored.getLast());
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(10));
        metricUpdater.visit(e, 1);
    }

    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(true, metricUpdater.hasCompletedRound());

    CPPUNIT_ASSERT_EQUAL(int64_t(11), dms.docsStored.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(12), dms.bytesStored.getLast());

    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(20));
        metricUpdater.visit(e, 1);
    }

    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(32), dms.docsStored.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(34), dms.bytesStored.getLast());
}

void
BucketDBMetricUpdaterTest::testBucketsWithTooFewAndTooManyCopies()
{
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms(_loadTypes);

    metricUpdater.completeRound();
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets_toofewcopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets_toomanycopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets.getLast());

    // 1 copy too little
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(10));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets_toofewcopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets_toomanycopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets.getLast());

    // 1 copy too many
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(40, 40));
        metricUpdater.visit(e, 1);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets_toofewcopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets_toomanycopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(2), ims.buckets.getLast());

    // Right amount of copies, just inc bucket counter.
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(40, 40));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets_toofewcopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets_toomanycopies.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(3), ims.buckets.getLast());
}

void
BucketDBMetricUpdaterTest::testBucketsWithVaryingTrustedness()
{
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms(_loadTypes);

    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets_notrusted.getLast());
    // Has only trusted (implicit for first added)
    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(100));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets_notrusted.getLast());
    // Has at least one trusted (implicit for first added)
    {
        BucketDatabase::Entry e(document::BucketId(16, 2), makeInfo(100, 200));
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    CPPUNIT_ASSERT_EQUAL(int64_t(0), ims.buckets_notrusted.getLast());
    // Has no trusted
    {
        BucketInfo info(makeInfo(100, 200));
        info.resetTrusted();
        BucketDatabase::Entry e(document::BucketId(16, 3), info);
        metricUpdater.visit(e, 2);
    }
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);
    CPPUNIT_ASSERT_EQUAL(int64_t(1), ims.buckets_notrusted.getLast());
}

void
BucketDBMetricUpdaterTest::testPickCountsFromTrustedCopy()
{
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms(_loadTypes);

    // First copy added is implicitly trusted, but it is not the largest.
    BucketDatabase::Entry e(document::BucketId(16, 2), makeInfo(100, 200));
    metricUpdater.visit(e, 2);
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(101), dms.docsStored.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(102), dms.bytesStored.getLast());
}

void
BucketDBMetricUpdaterTest::testPickLargestCopyIfNoTrusted()
{
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms(_loadTypes);

    // No trusted copies, so must pick second copy.
    BucketInfo info(makeInfo(100, 200));
    info.resetTrusted();
    BucketDatabase::Entry e(document::BucketId(16, 2), info);
    metricUpdater.visit(e, 2);
    metricUpdater.completeRound(false);
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(201), dms.docsStored.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(202), dms.bytesStored.getLast());
}

void
BucketDBMetricUpdaterTest::testCompleteRoundClearsWorkingState()
{
    BucketDBMetricUpdater metricUpdater;
    IdealStateMetricSet ims;
    DistributorMetricSet dms(_loadTypes);

    {
        BucketDatabase::Entry e(document::BucketId(16, 1), makeInfo(10));
        metricUpdater.visit(e, 1);
    }
    metricUpdater.completeRound();
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(11), dms.docsStored.getLast());
    // Completing the round again with no visits having been done will
    // propagate an empty working state to the complete state.
    metricUpdater.completeRound();
    metricUpdater.getLastCompleteStats().propagateMetrics(ims, dms);

    CPPUNIT_ASSERT_EQUAL(int64_t(0), dms.docsStored.getLast());
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

void BucketDBMetricUpdaterTest::testMinBucketReplicaTrackedAndReportedPerNode()
{
    BucketDBMetricUpdater metricUpdater;

    // Node 0 and 1 should have min replica 1, while node 2 should have min
    // replica 2.
    visitBucketWith2Copies1Trusted(metricUpdater);
    visitBucketWith2CopiesBothTrusted(metricUpdater);

    CPPUNIT_ASSERT_EQUAL(NodeToReplicasMap({{0, 1}, {1, 1}, {2, 2}}),
                         replicaStatsOf(metricUpdater));
}

void
BucketDBMetricUpdaterTest::nonTrustedReplicasAlsoCountedInModeAny()
{
    BucketDBMetricUpdater metricUpdater;
    using CountingMode = BucketDBMetricUpdater::ReplicaCountingMode;
    metricUpdater.setMinimumReplicaCountingMode(CountingMode::ANY);
    visitBucketWith2Copies1Trusted(metricUpdater);
    visitBucketWith2CopiesBothTrusted(metricUpdater);

    CPPUNIT_ASSERT_EQUAL(NodeToReplicasMap({{0, 2}, {1, 2}, {2, 2}}),
                         replicaStatsOf(metricUpdater));
}

void
BucketDBMetricUpdaterTest::minimumReplicaCountReturnedForNodeInModeAny()
{
    BucketDBMetricUpdater metricUpdater;
    using CountingMode = BucketDBMetricUpdater::ReplicaCountingMode;
    metricUpdater.setMinimumReplicaCountingMode(CountingMode::ANY);
    visitBucketWith2CopiesBothTrusted(metricUpdater);
    visitBucketWith1Copy(metricUpdater);

    // Node 2 has a bucket with only 1 replica.
    CPPUNIT_ASSERT_EQUAL(NodeToReplicasMap({{0, 2}, {2, 1}}),
                         replicaStatsOf(metricUpdater));
}

} // distributor
} // storage
