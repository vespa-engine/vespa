// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/bucket_spaces_stats_provider.h>
#include <vespa/storage/distributor/distributor_host_info_reporter.h>
#include <vespa/storage/distributor/min_replica_provider.h>
#include <tests/common/hostreporter/util.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <chrono>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using End = vespalib::JsonStream::End;
using File = vespalib::File;
using MinReplicaStats = std::unordered_map<uint16_t, uint32_t>;
using Object = vespalib::JsonStream::Object;
using PerNodeBucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats;
using BucketSpacesStats = BucketSpacesStatsProvider::BucketSpacesStats;
using namespace ::testing;

struct DistributorHostInfoReporterTest : Test {
    static void verifyBucketSpaceStats(const vespalib::Slime& root, uint16_t nodeIndex, const vespalib::string& bucketSpaceName,
                                       size_t bucketsTotal, size_t bucketsPending);
    static void verifyBucketSpaceStats(const vespalib::Slime& root, uint16_t nodeIndex, const vespalib::string& bucketSpaceName);
};

using ms = std::chrono::milliseconds;

namespace {

// My kingdom for GoogleMock!
struct MockedMinReplicaProvider : MinReplicaProvider
{
    MinReplicaStats minReplica;

    ~MockedMinReplicaProvider() override;
    std::unordered_map<uint16_t, uint32_t> getMinReplica() const override {
        return minReplica;
    }
};

MockedMinReplicaProvider::~MockedMinReplicaProvider() = default;


struct MockedBucketSpacesStatsProvider : public BucketSpacesStatsProvider {
    PerNodeBucketSpacesStats stats;

    ~MockedBucketSpacesStatsProvider() override;
    PerNodeBucketSpacesStats getBucketSpacesStats() const override {
        return stats;
    }
};

MockedBucketSpacesStatsProvider::~MockedBucketSpacesStatsProvider() = default;

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
DistributorHostInfoReporterTest::verifyBucketSpaceStats(const vespalib::Slime& root,
                                                        uint16_t nodeIndex,
                                                        const vespalib::string& bucketSpaceName,
                                                        size_t bucketsTotal,
                                                        size_t bucketsPending)
{
    const auto &stats = getBucketSpaceStats(root, nodeIndex, bucketSpaceName);
    const auto &buckets = stats["buckets"];
    EXPECT_EQ(bucketsTotal, static_cast<size_t>(buckets["total"].asLong()));
    EXPECT_EQ(bucketsPending, static_cast<size_t>(buckets["pending"].asLong()));
}

void
DistributorHostInfoReporterTest::verifyBucketSpaceStats(const vespalib::Slime& root,
                                                        uint16_t nodeIndex,
                                                        const vespalib::string& bucketSpaceName)
{
    const auto &stats = getBucketSpaceStats(root, nodeIndex, bucketSpaceName);
    EXPECT_FALSE(stats["buckets"].valid());
}

struct Fixture {
    MockedMinReplicaProvider minReplicaProvider;
    MockedBucketSpacesStatsProvider bucketSpacesStatsProvider;
    DistributorHostInfoReporter reporter;
    Fixture()
        : minReplicaProvider(),
          bucketSpacesStatsProvider(),
          reporter(minReplicaProvider, bucketSpacesStatsProvider)
    {}
    ~Fixture() = default;
};

TEST_F(DistributorHostInfoReporterTest, min_replica_stats_are_reported) {
    Fixture f;

    MinReplicaStats minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    f.minReplicaProvider.minReplica = minReplica;

    vespalib::Slime root;
    util::reporterToSlime(f.reporter, root);

    EXPECT_EQ(2, getMinReplica(root, 0));
    EXPECT_EQ(9, getMinReplica(root, 5));
}

TEST_F(DistributorHostInfoReporterTest, merge_min_replica_stats) {

    MinReplicaStats min_replica_a;
    min_replica_a[3] = 2;
    min_replica_a[5] = 4;

    MinReplicaStats min_replica_b;
    min_replica_b[5] = 6;
    min_replica_b[7] = 8;

    MinReplicaStats result;
    merge_min_replica_stats(result, min_replica_a);
    merge_min_replica_stats(result, min_replica_b);

    EXPECT_EQ(3, result.size());
    EXPECT_EQ(2, result[3]);
    EXPECT_EQ(4, result[5]);
    EXPECT_EQ(8, result[7]);
}

TEST_F(DistributorHostInfoReporterTest, generate_example_json) {
    Fixture f;

    MinReplicaStats minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    f.minReplicaProvider.minReplica = minReplica;

    PerNodeBucketSpacesStats stats;
    stats[0]["default"] = BucketSpaceStats(11, 3);
    stats[0]["global"] = BucketSpaceStats(13, 5);
    stats[5]["default"] = BucketSpaceStats();
    f.bucketSpacesStatsProvider.stats = stats;

    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);

    stream << Object();
    f.reporter.report(stream);
    stream << End();
    stream.finalize();

    std::string jsonString = json.str();

    std::string path = "../../../../protocols/getnodestate/distributor.json";
    std::string goldenString = File::readAll(path);

    vespalib::Memory goldenMemory(goldenString);
    vespalib::Slime goldenSlime;
    vespalib::slime::JsonFormat::decode(goldenMemory, goldenSlime);

    vespalib::Memory jsonMemory(jsonString);
    vespalib::Slime jsonSlime;
    vespalib::slime::JsonFormat::decode(jsonMemory, jsonSlime);

    EXPECT_EQ(goldenSlime, jsonSlime);
}

TEST_F(DistributorHostInfoReporterTest, no_report_generated_if_disabled) {
    Fixture f;
    f.reporter.enableReporting(false);

    MinReplicaStats minReplica;
    minReplica[0] = 2;
    minReplica[5] = 9;
    f.minReplicaProvider.minReplica = minReplica;

    vespalib::Slime root;
    util::reporterToSlime(f.reporter, root);
    EXPECT_EQ(0, root.get().children());
}

TEST_F(DistributorHostInfoReporterTest, bucket_spaces_stats_are_reported) {
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
        FAIL() << "No exception thrown";
    } catch (const std::runtime_error& ex) {
        EXPECT_EQ("No bucket space found with name global", vespalib::string(ex.what()));
    }
}

TEST_F(DistributorHostInfoReporterTest, merge_per_node_bucket_spaces_stats) {

    PerNodeBucketSpacesStats stats_a;
    stats_a[3]["default"] = BucketSpaceStats(3, 2);
    stats_a[3]["global"] = BucketSpaceStats(5, 4);
    stats_a[5]["default"] = BucketSpaceStats(7, 6);
    stats_a[5]["global"] = BucketSpaceStats(9, 8);

    PerNodeBucketSpacesStats stats_b;
    stats_b[5]["default"] = BucketSpaceStats(11, 10);
    stats_b[5]["global"] = BucketSpaceStats(13, 12);
    stats_b[7]["default"] = BucketSpaceStats(15, 14);

    PerNodeBucketSpacesStats result;
    merge_per_node_bucket_spaces_stats(result, stats_a);
    merge_per_node_bucket_spaces_stats(result, stats_b);

    PerNodeBucketSpacesStats exp;
    exp[3]["default"] = BucketSpaceStats(3, 2);
    exp[3]["global"] = BucketSpaceStats(5, 4);
    exp[5]["default"] = BucketSpaceStats(7+11, 6+10);
    exp[5]["global"] = BucketSpaceStats(9+13, 8+12);
    exp[7]["default"] = BucketSpaceStats(15, 14);

    EXPECT_EQ(exp, result);
}

TEST_F(DistributorHostInfoReporterTest, merge_bucket_space_stats_maintains_valid_flag) {
    BucketSpaceStats stats_a(5, 3);
    BucketSpaceStats stats_b;

    stats_a.merge(stats_b);
    EXPECT_FALSE(stats_a.valid());
    EXPECT_EQ(5, stats_a.bucketsTotal());
    EXPECT_EQ(3, stats_a.bucketsPending());
}

}
