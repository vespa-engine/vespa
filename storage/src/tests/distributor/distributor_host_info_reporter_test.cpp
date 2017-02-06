// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/distributor_host_info_reporter.h>
#include <vespa/storage/distributor/latency_statistics_provider.h>
#include <vespa/storage/distributor/min_replica_provider.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/jsonstream.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <tests/common/hostreporter/util.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace distributor {

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
    CPPUNIT_TEST_SUITE_END();

    void hostInfoWithPutLatenciesOnly();
    void hostInfoAllInfo();
    void verifyReportedNodeLatencies(
            const vespalib::Slime& root,
            uint16_t node,
            int64_t latencySum,
            int64_t count);
    void generateExampleJson();
    void noReportGeneratedIfDisabled();
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

    NodeStatsSnapshot doGetLatencyStatistics() const {
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

} // anon ns

void
DistributorHostInfoReporterTest::verifyReportedNodeLatencies(
        const vespalib::Slime& root,
        uint16_t node,
        int64_t latencySum,
        int64_t count)
{
    auto& latencies = getLatenciesForNode(root, node);
    CPPUNIT_ASSERT_EQUAL(latencySum,
                         latencies["put"]["latency-ms-sum"].asLong());
    CPPUNIT_ASSERT_EQUAL(count, latencies["put"]["count"].asLong());
}

void
DistributorHostInfoReporterTest::hostInfoWithPutLatenciesOnly()
{
    MockedLatencyStatisticsProvider latencyStatsProvider;
    MockedMinReplicaProvider minReplicaProvider;
    DistributorHostInfoReporter reporter(latencyStatsProvider,
                                         minReplicaProvider);

    NodeStatsSnapshot snapshot;
    snapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    snapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };

    latencyStatsProvider.returnedSnapshot = snapshot;

    vespalib::Slime root;
    util::reporterToSlime(reporter, root);
    verifyReportedNodeLatencies(root, 0, 10000, 3);
    verifyReportedNodeLatencies(root, 5, 25000, 7);
}

void
DistributorHostInfoReporterTest::hostInfoAllInfo()
{
    MockedLatencyStatisticsProvider latencyStatsProvider;
    MockedMinReplicaProvider minReplicaProvider;
    DistributorHostInfoReporter reporter(latencyStatsProvider,
                                         minReplicaProvider);

    NodeStatsSnapshot latencySnapshot;
    latencySnapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    latencySnapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };
    latencyStatsProvider.returnedSnapshot = latencySnapshot;

    std::unordered_map<uint16_t, uint32_t> minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    minReplicaProvider.minReplica = minReplica;

    vespalib::Slime root;
    util::reporterToSlime(reporter, root);
    verifyReportedNodeLatencies(root, 0, 10000, 3);
    verifyReportedNodeLatencies(root, 5, 25000, 7);

    CPPUNIT_ASSERT_EQUAL(2, getMinReplica(root, 0));
    CPPUNIT_ASSERT_EQUAL(9, getMinReplica(root, 5));
}

void
DistributorHostInfoReporterTest::generateExampleJson()
{
    MockedLatencyStatisticsProvider latencyStatsProvider;
    MockedMinReplicaProvider minReplicaProvider;
    DistributorHostInfoReporter reporter(latencyStatsProvider,
                                         minReplicaProvider);

    NodeStatsSnapshot snapshot;
    snapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    snapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };
    latencyStatsProvider.returnedSnapshot = snapshot;

    std::unordered_map<uint16_t, uint32_t> minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    minReplicaProvider.minReplica = minReplica;

    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);

    stream << Object();
    reporter.report(stream);
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
    MockedLatencyStatisticsProvider latencyStatsProvider;
    MockedMinReplicaProvider minReplicaProvider;
    DistributorHostInfoReporter reporter(latencyStatsProvider,
                                         minReplicaProvider);
    reporter.enableReporting(false);

    NodeStatsSnapshot snapshot;
    snapshot.nodeToStats[0] = { makeOpStats(ms(10000), 3) };
    snapshot.nodeToStats[5] = { makeOpStats(ms(25000), 7) };

    latencyStatsProvider.returnedSnapshot = snapshot;

    vespalib::Slime root;
    util::reporterToSlime(reporter, root);
    CPPUNIT_ASSERT_EQUAL(size_t(0), root.get().children());
}

} // distributor
} // storage

