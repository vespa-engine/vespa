// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/distributor_host_info_reporter.h>
#include <vespa/storage/distributor/latency_statistics_provider.h>
#include <vespa/storage/distributor/min_replica_provider.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <tests/common/hostreporter/util.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/storage/distributor/bucket_spaces_stats_provider.h>

namespace storage::distributor {

using PerNodeBucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats;
using End = vespalib::JsonStream::End;
using File = vespalib::File;
using Object = vespalib::JsonStream::Object;

class DistributorHostInfoReporterTest : public CppUnit::TestFixture
{
    CPPUNIT_TEST_SUITE(DistributorHostInfoReporterTest);
    CPPUNIT_TEST(hostInfoWithPutLatenciesOnly);
    CPPUNIT_TEST(hostInfoAllInfo);
    CPPUNIT_TEST(generateExampleJson);
    CPPUNIT_TEST(noReportGeneratedIfDisabled);
    CPPUNIT_TEST(bucket_spaces_stats_are_reported);
    CPPUNIT_TEST_SUITE_END();

    void hostInfoWithPutLatenciesOnly();
    void hostInfoAllInfo();
    void verifyReportedNodeLatencies(const vespalib::Slime& root,
                                     uint16_t nodeIndex,
                                     int64_t latencySum,
                                     int64_t count);
    void verifyBucketSpaceStats(const vespalib::Slime& root,
                                uint16_t nodeIndex,
                                const vespalib::string& bucketSpaceName,
                                size_t bucketsTotal,
                                size_t bucketsPending);
    void verifyBucketSpaceStats(const vespalib::Slime& root,
                                uint16_t nodeIndex,
                                const vespalib::string& bucketSpaceName);
    void generateExampleJson();
    void noReportGeneratedIfDisabled();
    void bucket_spaces_stats_are_reported();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DistributorHostInfoReporterTest);

using ms = std::chrono::milliseconds;

namespace {

OperationStats
makeOpStats(std::chrono::milliseconds totalLatency, uint64_t numRequests)
{
    OperationStats stats;
    stats.totalLatency = totalLatency;
    stats.numRequests = numRequests;
    return stats;
}

// My kingdom for GoogleMock!
struct MockedLatencyStatisticsProvider : LatencyStatisticsProvider
{
    NodeStatsSnapshot returnedSnapshot;

    NodeStatsSnapshot doGetLatencyStatistics() const override {
        return returnedSnapshot;
    }
};

struct MockedMinReplicaProvider : MinReplicaProvider
{
    std::unordered_map<uint16_t, uint32_t> minReplica;

    std::unordered_map<uint16_t, uint32_t> getMinReplica() const override {
        return minReplica;
    }
};

struct MockedBucketSpacesStatsProvider : public BucketSpacesStatsProvider {
    PerNodeBucketSpacesStats stats;

    PerNodeBucketSpacesStats getBucketSpacesStats() const override {
        return stats;
    }
};

const vespalib::slime::Inspector&
getNode(const vespalib::Slime& root, uint16_t nodeIndex)
{
    auto& storage_nodes = root.get()["distributor"]["storage-nodes"];
    const size_t n = storage_nodes.entries();
    for (size_t i = 0; i < n; ++i) {
        if (storage_nodes[i]["node-index"].asLong() == nodeIndex) {
            return storage_nodes[i];
        }
    }
    throw std::runtime_error("No node found with index "
                             + std::to_string(nodeIndex));
}

int
getMinReplica(const vespalib::Slime& root, uint16_t nodeIndex)
{
    return getNode(root, nodeIndex)["min-current-replication-factor"].asLong();
}

const vespalib::slime::Inspector&
getLatenciesForNode(const vespalib::Slime& root, uint16_t nodeIndex)
{
    return getNode(root, nodeIndex)["ops-latency"];
}

const vespalib::slime::Inspector&
getBucketSpaceStats(const vespalib::Slime& root, uint16_t nodeIndex, const vespalib::string& bucketSpaceName)
{
    const auto& bucketSpaces = getNode(root, nodeIndex)["bucket-spaces"];
    for (size_t i = 0; i < bucketSpaces.entries(); ++i) {
        if (bucketSpaces[i]["name"].asString().make_stringref() == bucketSpaceName) {
            return bucketSpaces[i];
        }
    }
    throw std::runtime_error("No bucket space found with name " + bucketSpaceName);
}

}

void
DistributorHostInfoReporterTest::verifyReportedNodeLatencies(const vespalib::Slime& root,
                                                             uint16_t nodeIndex,
                                                             int64_t latencySum,
                                                             int64_t count)
{
    auto& latencies = getLatenciesForNode(root, nodeIndex);
    CPPUNIT_ASSERT_EQUAL(latencySum,
                         latencies["put"]["latency-ms-sum"].asLong());
    CPPUNIT_ASSERT_EQUAL(count, latencies["put"]["count"].asLong());
}

void
DistributorHostInfoReporterTest::verifyBucketSpaceStats(const vespalib::Slime& root,
                                                        uint16_t nodeIndex,
                                                        const vespalib::string& bucketSpaceName,
                                                        size_t bucketsTotal,
                                                        size_t bucketsPending)
{
    const auto &stats = getBucketSpaceStats(root, nodeIndex, bucketSpaceName);
    CPPUNIT_ASSERT_EQUAL(bucketsTotal, static_cast<size_t>(stats["total"].asLong()));
    CPPUNIT_ASSERT_EQUAL(bucketsPending, static_cast<size_t>(stats["pending"].asLong()));
}

void
DistributorHostInfoReporterTest::verifyBucketSpaceStats(const vespalib::Slime& root,
                                                        uint16_t nodeIndex,
                                                        const vespalib::string& bucketSpaceName)
{
    const auto &stats = getBucketSpaceStats(root, nodeIndex, bucketSpaceName);
    CPPUNIT_ASSERT(!stats["total"].valid());
    CPPUNIT_ASSERT(!stats["pending"].valid());
}

struct Fixture {
    MockedLatencyStatisticsProvider latencyStatsProvider;
    MockedMinReplicaProvider minReplicaProvider;
    MockedBucketSpacesStatsProvider bucketSpacesStatsProvider;
    DistributorHostInfoReporter reporter;
    Fixture()
        : latencyStatsProvider(),
          minReplicaProvider(),
          bucketSpacesStatsProvider(),
          reporter(latencyStatsProvider, minReplicaProvider, bucketSpacesStatsProvider)
    {}
    ~Fixture() {}
};

void
DistributorHostInfoReporterTest::hostInfoWithPutLatenciesOnly()
{
    Fixture f;
    NodeStatsSnapshot snapshot;
    snapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    snapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };

    f.latencyStatsProvider.returnedSnapshot = snapshot;

    vespalib::Slime root;
    util::reporterToSlime(f.reporter, root);
    verifyReportedNodeLatencies(root, 0, 10000, 3);
    verifyReportedNodeLatencies(root, 5, 25000, 7);
}

void
DistributorHostInfoReporterTest::hostInfoAllInfo()
{
    Fixture f;
    NodeStatsSnapshot latencySnapshot;
    latencySnapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    latencySnapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };
    f.latencyStatsProvider.returnedSnapshot = latencySnapshot;

    std::unordered_map<uint16_t, uint32_t> minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    f.minReplicaProvider.minReplica = minReplica;

    vespalib::Slime root;
    util::reporterToSlime(f.reporter, root);
    verifyReportedNodeLatencies(root, 0, 10000, 3);
    verifyReportedNodeLatencies(root, 5, 25000, 7);

    CPPUNIT_ASSERT_EQUAL(2, getMinReplica(root, 0));
    CPPUNIT_ASSERT_EQUAL(9, getMinReplica(root, 5));
}

void
DistributorHostInfoReporterTest::generateExampleJson()
{
    Fixture f;
    NodeStatsSnapshot snapshot;
    snapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    snapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };
    f.latencyStatsProvider.returnedSnapshot = snapshot;

    std::unordered_map<uint16_t, uint32_t> minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    f.minReplicaProvider.minReplica = minReplica;

    PerNodeBucketSpacesStats stats;
    stats[0]["default"] = BucketSpaceStats(11, 3);
    stats[0]["global"] = BucketSpaceStats(13, 5);
    stats[5]["default"] = BucketSpaceStats(17, 7);
    f.bucketSpacesStatsProvider.stats = stats;

    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);

    stream << Object();
    f.reporter.report(stream);
    stream << End();
    stream.finalize();

    std::string jsonString = json.str();

    std::string path = TEST_PATH("../../../protocols/getnodestate/distributor.json");
    std::string goldenString = File::readAll(path);

    vespalib::Memory goldenMemory(goldenString);
    vespalib::Slime goldenSlime;
    vespalib::slime::JsonFormat::decode(goldenMemory, goldenSlime);

    vespalib::Memory jsonMemory(jsonString);
    vespalib::Slime jsonSlime;
    vespalib::slime::JsonFormat::decode(jsonMemory, jsonSlime);

    CPPUNIT_ASSERT_EQUAL(goldenSlime, jsonSlime);
}

void
DistributorHostInfoReporterTest::noReportGeneratedIfDisabled()
{
    Fixture f;
    f.reporter.enableReporting(false);

    NodeStatsSnapshot snapshot;
    snapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    snapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };

    f.latencyStatsProvider.returnedSnapshot = snapshot;

    vespalib::Slime root;
    util::reporterToSlime(f.reporter, root);
    CPPUNIT_ASSERT_EQUAL(size_t(0), root.get().children());
}

void
DistributorHostInfoReporterTest::bucket_spaces_stats_are_reported()
{
    Fixture f;
    PerNodeBucketSpacesStats stats;
    stats[1]["default"] = BucketSpaceStats(11, 3);
    stats[1]["global"] = BucketSpaceStats(13, 5);
    stats[2]["default"] = BucketSpaceStats(17, 7);
    stats[2]["global"] = BucketSpaceStats();
    stats[3]["default"] = BucketSpaceStats(19, 11);
    f.bucketSpacesStatsProvider.stats = stats;

    vespalib::Slime root;
    util::reporterToSlime(f.reporter, root);
    verifyBucketSpaceStats(root, 1, "default", 11, 3);
    verifyBucketSpaceStats(root, 1, "global", 13, 5);
    verifyBucketSpaceStats(root, 2, "default", 17, 7);
    verifyBucketSpaceStats(root, 2, "global");
    verifyBucketSpaceStats(root, 3, "default", 19, 11);
    try {
        verifyBucketSpaceStats(root, 3, "global");
        CPPUNIT_ASSERT(false);
    } catch (const std::runtime_error &ex) {
        CPPUNIT_ASSERT("No bucket space found with name global" == vespalib::string(ex.what()));
    }
}

}

