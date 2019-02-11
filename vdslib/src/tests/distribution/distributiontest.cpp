// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/distribution/idealnodecalculator.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/stllike/lexical_cast.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/config/subscription/configuri.h>
#include <vespa/fastos/file.h>
#include <chrono>
#include <thread>
#include <fstream>

namespace storage::lib {

struct DistributionTest : public CppUnit::TestFixture {
    void testVerifyJavaDistributions();
    void testVerifyJavaDistributions2();
    void testDiskSkewLocal();
    void testDiskSkewGlobal();
    void testDiskIntersection();
    void testDown();
    void testInitializing();
    void testDiskDown();
    void testDiskDownMaintenance();
    void testDiskCapacityWeights();
    void testUnchangedDistribution();

    void testSerializeDeserialize();

    void testSkew();
    void testDistribution();
    void testGreedyDistribution();
    void testSkewWithDown();
    void testMove();
    void testMoveConstraints();
    void testMinimalDataMovement();
    void testDistributionBits();

    void testRedundancyHierarchicalDistribution();
    void testHierarchicalDistribution();
    void testHierarchicalDistributionPerformance();
    void testHierarchicalNoRedistribution();
    void testGroupCapacity();

    void testHighSplitBit();

    void testActivePerGroup();
    void testHierarchicalDistributeLessThanRedundancy();

    void testEmptyAndCopy();

    CPPUNIT_TEST_SUITE(DistributionTest);
    CPPUNIT_TEST(testVerifyJavaDistributions);
    CPPUNIT_TEST(testVerifyJavaDistributions2);
    CPPUNIT_TEST(testDiskSkewLocal);
    CPPUNIT_TEST(testDiskSkewGlobal);
    CPPUNIT_TEST(testDiskIntersection);
    CPPUNIT_TEST(testMove);
    CPPUNIT_TEST(testMoveConstraints);
    CPPUNIT_TEST(testDown);
    CPPUNIT_TEST(testInitializing);
    CPPUNIT_TEST(testDiskDown);
    CPPUNIT_TEST(testDiskDownMaintenance);
    CPPUNIT_TEST(testDiskCapacityWeights);
    CPPUNIT_TEST(testUnchangedDistribution);
    CPPUNIT_TEST(testSerializeDeserialize);

    CPPUNIT_TEST(testRedundancyHierarchicalDistribution);
    CPPUNIT_TEST(testHierarchicalDistribution);
    // CPPUNIT_TEST(testHierarchicalDistributionPerformance);
    CPPUNIT_TEST(testHierarchicalNoRedistribution);
    CPPUNIT_TEST(testHierarchicalDistributeLessThanRedundancy);
    CPPUNIT_TEST(testDistributionBits);
    CPPUNIT_TEST(testGroupCapacity);

    CPPUNIT_TEST(testHighSplitBit);
    CPPUNIT_TEST(testActivePerGroup);

    // Skew tests. Should probably be in separate test file.
    /*
    CPPUNIT_TEST(testSkew);
    CPPUNIT_TEST(testDistribution);
    CPPUNIT_TEST(testGreedyDistribution);
    CPPUNIT_TEST(testSkewWithDown);
    */

    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DistributionTest);

template <typename T>
T readConfig(const config::ConfigUri & uri)
{
    return *config::ConfigGetter<T>::getConfig(uri.getConfigId(), uri.getContext());
}

void
DistributionTest::testVerifyJavaDistributions()
{
    std::vector<std::string> tests;
    tests.push_back("capacity");
    tests.push_back("depth2");
    tests.push_back("depth3");
    tests.push_back("retired");
    for (uint32_t i=0; i<tests.size(); ++i) {
        std::string test = tests[i];
        ClusterState state;
        {
            std::ifstream in(TEST_PATH("distribution/testdata/java_" + test + ".state").c_str());
            std::string mystate;
            in >> mystate;
            in.close();
            state = ClusterState(mystate);
        }
        Distribution distr(readConfig<vespa::config::content::StorDistributionConfig>(
                "file:" + TEST_PATH("distribution/testdata/java_") + test + ".cfg"));
        std::ofstream of((TEST_PATH("distribution/testdata/cpp_") + test + ".distribution").c_str());

        long maxBucket = 1;
        long mask = 0;
        for (uint32_t distributionBits = 0; distributionBits <= 32;
             ++distributionBits)
        {
            state.setDistributionBitCount(distributionBits);
            RandomGen randomizer(distributionBits);
            for (uint32_t bucketIndex = 0; bucketIndex < 64; ++bucketIndex) {
                if (bucketIndex >= maxBucket) break;
                uint64_t bucketId = bucketIndex;
                    // Use random bucket if we dont test all
                if (maxBucket > 64) {
                    bucketId = randomizer.nextUint64();
                }
                document::BucketId bucket(distributionBits, bucketId);
                for (uint32_t redundancy = 1;
                     redundancy <= distr.getRedundancy(); ++redundancy)
                {
                    int distributorIndex = distr.getIdealDistributorNode(
                            state, bucket, "uim");
                    of << distributionBits << " " << (bucketId & mask)
                       << " " << redundancy << " " << distributorIndex << "\n";
                }
            }
            mask = (mask << 1) | 1;
            maxBucket = maxBucket << 1;
        }
        of.close();
        std::ostringstream cmd;
        cmd << "diff -u " << TEST_PATH("distribution/testdata/cpp_") << test << ".distribution "
            << TEST_PATH("distribution/testdata/java_") << test << ".distribution";
        int result = system(cmd.str().c_str());
        CPPUNIT_ASSERT_EQUAL_MSG("Failed distribution sync test: " + test,
                                 0, result);
    }
}

namespace {
    struct ExpectedResult {
        ExpectedResult() { }
        ExpectedResult(const ExpectedResult &) = default;
        ExpectedResult & operator = (const ExpectedResult &) = default;
        ExpectedResult(ExpectedResult &&) = default;
        ExpectedResult & operator = (ExpectedResult &&) = default;
        ~ExpectedResult() { }
        document::BucketId bucket;
        IdealNodeList nodes;
        vespalib::string failure;
    };
    void verifyJavaDistribution(
            const vespalib::string& name,
            const ClusterState& state,
            const Distribution& distribution,
            const NodeType& nodeType,
            uint16_t redundancy,
            uint16_t nodeCount,
            vespalib::stringref upStates,
            const std::vector<ExpectedResult> results)
    {
        (void) nodeCount;
        for (uint32_t i=0, n=results.size(); i<n; ++i) {
            std::string testId = name + " " + results[i].bucket.toString();
            try{
                std::vector<uint16_t> nvect;
                distribution.getIdealNodes(nodeType, state, results[i].bucket,
                                           nvect, upStates.data(), redundancy);
                IdealNodeList nodes;
                for (uint32_t j=0, m=nvect.size(); j<m; ++j) {
                    nodes.push_back(Node(nodeType, nvect[j]));
                }
                /*
                if (results[i].nodes.toString() != nodes.toString()) {
                    std::cerr << "Failure: " << testId << " "
                              << results[i].nodes.toString() << " in java but "
                              << nodes.toString() << " in C++.\n";
                }// */
                //*
                CPPUNIT_ASSERT_EQUAL_MSG(testId,
                        results[i].nodes.toString(), nodes.toString());
                // */
                if (results[i].nodes.size() > 0) {
                    CPPUNIT_ASSERT_EQUAL_MSG(testId, vespalib::string("NONE"),
                                             results[i].failure);
                } else {
                    CPPUNIT_ASSERT_EQUAL_MSG(testId,
                            vespalib::string("NO_DISTRIBUTORS_AVAILABLE"),
                            results[i].failure);
                }
            } catch (vespalib::Exception& e) {
                CPPUNIT_ASSERT_EQUAL_MSG(testId, results[i].failure,
                                         e.getMessage());
            }
        }
    }
} // anonymous

auto readFile(const std::string & filename) {
    vespalib::File file(filename);
    file.open(vespalib::File::READONLY);

    std::vector<char> buf(file.getFileSize());
    off_t read = file.read(&buf[0], buf.size(), 0);

    CPPUNIT_ASSERT_EQUAL(read, file.getFileSize());
    return buf;
}

void
DistributionTest::testVerifyJavaDistributions2()
{
    vespalib::DirectoryList files(
            vespalib::listDirectory(TEST_PATH("distribution/testdata")));
    for (uint32_t i=0, n=files.size(); i<n; ++i) {
        size_t pos = files[i].find(".java.results");
        if (pos == vespalib::string::npos || pos + 13 != files[i].size()) {
            //std::cerr << "Skipping unmatched file '" << files[i] << "'.\n";
            continue;
        }

        vespalib::string name(files[i].substr(0, pos));
        using namespace vespalib::slime;
        vespalib::Slime slime;

        auto buf = readFile(TEST_PATH("distribution/testdata/") + files[i]);
        auto size = JsonFormat::decode({&buf[0], buf.size()}, slime);

        if (size == 0) {
            std::cerr << "\n\nSize of " << files[i] << " is 0. Maybe is not generated yet? Taking a 5 second nap!";
            std::this_thread::sleep_for(std::chrono::seconds(5));

            buf = readFile(TEST_PATH("distribution/testdata/") + files[i]);
            size = JsonFormat::decode({&buf[0], buf.size()}, slime);

            if (size == 0) {
                std::cerr << "\n\nError verifying " << files[i] << ". File doesn't exist or is empty";
            }
        }

        CPPUNIT_ASSERT(size != 0);
        Cursor& c(slime.get());

        ClusterState cs(c["cluster-state"].asString().make_string());
        std::string distConfig(c["distribution"].asString().make_string());
        Distribution d(distConfig);
        const NodeType& nt(
                NodeType::get(c["node-type"].asString().make_string()));
        uint32_t redundancy(c["redundancy"].asLong());
        uint32_t nodeCount(c["node-count"].asLong());
        vespalib::string upStates(c["up-states"].asString().make_string());
        std::vector<ExpectedResult> results;
        for (uint32_t j=0, m=c["result"].entries(); j<m; ++j) {
            Cursor& e(c["result"][j]);
            ExpectedResult result;
            std::string bucketString(e["bucket"].asString().make_string());
            char *end = 0;
            uint64_t rawBucket = strtoull(bucketString.c_str(), &end, 16);
            CPPUNIT_ASSERT_EQUAL(int(0), int(*end));
            result.bucket = document::BucketId(rawBucket);
            result.failure = e["failure"].asString().make_string();
            for (uint32_t k=0; k<e["nodes"].entries(); ++k) {
                result.nodes.push_back(Node(nt, e["nodes"][k].asLong()));
            }
            results.push_back(result);
        }
        verifyJavaDistribution(name, cs, d, nt, redundancy, nodeCount,
                               upStates, results);
        //std::cerr << name << ": Verified " << results.size() << " tests.\n";
    }
}

void
DistributionTest::testUnchangedDistribution()
{
    ClusterState state("distributor:10 storage:10");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    std::ifstream in(TEST_PATH("distribution/testdata/41-distributordistribution"));

    for (unsigned i = 0; i < 65536; i++) {
        uint16_t node = distr.getIdealDistributorNode(
                state, document::BucketId(16, i), "u");

        char buf[100];
        in.getline(buf, 100);

        CPPUNIT_ASSERT_EQUAL(atoi(buf), (int)node);
    }
}

namespace {
    struct Test {
        const NodeType* _nodeType;
        std::string _state;
        std::unique_ptr<Distribution> _distribution;
        uint32_t _bucketsToTest;
        const char* _upStates;
        uint16_t _redundancy;

        Test();
        ~Test();

        Test& state(const std::string& s) {
            _state = s;
            return *this;
        }

        Test& upStates(const char* ups) {
            _upStates = ups;
            return *this;
        }

        Test& nodeType(const NodeType& type) {
            _nodeType = &type;
            return *this;
        }

        Test& distribution(Distribution* d) {
            _distribution.reset(d);
            return *this;
        }

        std::vector<uint16_t> getNodeCounts() const {
            std::vector<uint16_t> result(10, 0);
            for (uint32_t i=0; i<_bucketsToTest; ++i) {
                document::BucketId bucket(16, i);
                std::vector<uint16_t> nodes;
                ClusterState clusterState(_state);
                _distribution->getIdealNodes(
                        *_nodeType, clusterState, bucket, nodes,
                        _upStates, _redundancy);
                for (uint32_t j=0; j<nodes.size(); ++j) {
                    ++result[nodes[j]];
                }
            }
            return result;
        }
        std::vector<uint16_t> getDiskCounts(uint16_t node) const {
            std::vector<uint16_t> result(3, 0);
            for (uint32_t i=0; i<_bucketsToTest; ++i) {
                document::BucketId bucket(16, i);
                std::vector<uint16_t> nodes;
                ClusterState clusterState(_state);
                _distribution->getIdealNodes(
                        *_nodeType, clusterState, bucket, nodes,
                        _upStates, _redundancy);
                for (uint32_t j=0; j<nodes.size(); ++j) {
                    if (nodes[j] == node) {
                        const NodeState& nodeState(clusterState.getNodeState(
                                Node(NodeType::STORAGE, node)));
                            // If disk was down, bucket should not map to this
                            // node at all
                        uint16_t disk = _distribution->getIdealDisk(
                                nodeState, node, bucket,
                                Distribution::IDEAL_DISK_EVEN_IF_DOWN);
                        ++result[disk];
                    }
                }
            }
            return result;
        }
    };

    Test::Test()
        : _nodeType(&NodeType::STORAGE),
          _state("distributor:10 storage:10"),
          _distribution(new Distribution(Distribution::getDefaultDistributionConfig(3, 10))),
          _bucketsToTest(100),
          _upStates("uir"),
          _redundancy(2)
    { }
    Test::~Test() { }

    std::vector<uint16_t> createNodeCountList(const std::string& source,
                                              std::vector<uint16_t>& vals) {
        std::vector<uint16_t> result(vals.size(), 0);
        vespalib::StringTokenizer st(source, " ");
        for (uint32_t i=0; i<st.size(); ++i) {
            vespalib::StringTokenizer st2(st[i], ":");
            uint16_t node(vespalib::lexical_cast<uint16_t>(st2[0]));
            uint16_t value = vals[node];
            if (st2[1] == std::string("*")) {
                value = vals[node];
            } else if (st2[1] == std::string("+")) {
                if (vals[node] > 0) {
                    value = vals[node];
                } else {
                    value = 0xffff;
                }
            } else {
                value = vespalib::lexical_cast<uint16_t>(st2[1]);
            }
            result[node] = value;
        }
        return result;
    }
}

#define ASSERT_BUCKET_NODE_COUNTS(test, result) \
{ \
    std::vector<uint16_t> cnt123(test.getNodeCounts()); \
    std::vector<uint16_t> exp123(createNodeCountList(result, cnt123)); \
    /*std::cerr << "Expected " << exp123 << " Got " << cnt123 << "\n";*/ \
    CPPUNIT_ASSERT_EQUAL(exp123, cnt123); \
}

#define ASSERT_BUCKET_DISK_COUNTS(node, test, result) \
{ \
    std::vector<uint16_t> cnt123(test.getDiskCounts(node)); \
    std::vector<uint16_t> exp123(createNodeCountList(result, cnt123)); \
    CPPUNIT_ASSERT_EQUAL(exp123, cnt123); \
}

void
DistributionTest::testDown()
{
    ASSERT_BUCKET_NODE_COUNTS(
            Test().state("storage:10 .4.s:m .5.s:m .6.s:d .7.s:d .9.s:r")
                  .upStates("u"),
            "0:+ 1:+ 2:+ 3:+ 8:+");

    ASSERT_BUCKET_NODE_COUNTS(
            Test().state("storage:10 .4.s:m .5.s:m .6.s:d .7.s:d .9.s:r")
                  .upStates("ur"),
            "0:+ 1:+ 2:+ 3:+ 8:+ 9:+");
}

void
DistributionTest::testDiskDown()
{
    ASSERT_BUCKET_DISK_COUNTS(
            2,
            Test().state("storage:10 .2.d:3 .2.d.0:d"),
            "1:+ 2:+");
}

void
DistributionTest::testSerializeDeserialize()
{
    Test t1;
    Test t2;
    t2.distribution(new Distribution(t1._distribution->serialize()));
    CPPUNIT_ASSERT_EQUAL(t1.getNodeCounts(), t2.getNodeCounts());
}

void
DistributionTest::testDiskDownMaintenance()
{
    ASSERT_BUCKET_DISK_COUNTS(
            2,
            Test().state("storage:10 .2.s:m .2.d:3 .2.d.0:d").upStates("um"),
            "1:+ 2:+");
}

void
DistributionTest::testInitializing()
{
    ASSERT_BUCKET_NODE_COUNTS(
            Test().state("distributor:3 .0.s:i .1.s:i .2.s:i")
                  .upStates("ui")
                  .nodeType(NodeType::DISTRIBUTOR),
            "0:+ 1:+ 2:+");
}

void
DistributionTest::testHighSplitBit()
{
    // Only 3 nodes of 10 are up => all copies should end on the 3 nodes and
    // none on the down nodes
    ClusterState state("storage:100");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 100));

    vespalib::asciistream ost1;
    vespalib::asciistream ost2;

    for (uint32_t bits = 33; bits < 36; ++bits) {
        uint64_t base = 0x23456789;
        base |= (1 << bits);

        document::BucketId bid1 = document::BucketId(bits, base);
        document::BucketId bid2 = document::BucketId(bits, base);

        std::vector<uint16_t> nodes1 =
            distr.getIdealStorageNodes(state,
                                       bid1,
                                       "u");

        std::vector<uint16_t> nodes2 =
            distr.getIdealStorageNodes(state,
                                       bid2,
                                       "u");

        ost1 << bid1 << " vs. " << bid2 << ": ";
        ost2 << bid1 << " vs. " << bid2 << ": ";

        for (uint32_t i = 0; i < nodes1.size(); ++i) {
            ost1 << nodes1[i] << " ";
        }
        ost1 << "\n";

        for (uint32_t i = 0; i < nodes2.size(); ++i) {
            ost2 << nodes2[i] << " ";
        }
        ost2 << "\n";
    }

    CPPUNIT_ASSERT_EQUAL(ost1.str(), ost2.str());
}



void
DistributionTest::testDiskCapacityWeights()
{
    uint16_t num_disks = 10;
    std::vector<double> capacities(num_disks);

    RandomGen rg(13);
    std::ostringstream ost;
    ost << "d:" << num_disks;
    for (unsigned i = 0; i < num_disks; ++i) {
        capacities[i] = rg.nextDouble();
        ost << " d." << i << ".c:" << capacities[i];
    }

    NodeState nodeState(ost.str(), &NodeType::STORAGE);

    Distribution distr(Distribution::getDefaultDistributionConfig(2, 3));

    for(int j=0; j < 10; ++j) {
        std::vector<float> diskDist(num_disks);
        for(int i=0; i < 1000; ++i) {
            document::BucketId id(16, i);
            int index = distr.getPreferredAvailableDisk(nodeState, j, id);
            diskDist[index]+=1;
        }

        //normalization
        for (unsigned i = 0; i < num_disks; ++i) {
            diskDist[i] /= capacities[i];
        }

        std::sort(diskDist.begin(), diskDist.end());

        double avg=0.0;
        for (unsigned i = 0; i < num_disks; ++i) {
            avg+=diskDist[i];
        }
        avg /= num_disks;
        
        double skew = (diskDist[num_disks-1]-avg)/(diskDist[num_disks-1]);

        CPPUNIT_ASSERT(skew < 0.3);
    }
}


void
DistributionTest::testDiskSkewLocal()
{
    Distribution distr(Distribution::getDefaultDistributionConfig(2, 3, Distribution::MODULO_INDEX));
    std::vector<float> diskDist(100);
    NodeState nodeState;
    nodeState.setDiskCount(100);
    for(int i=0; i < 65536; i++) {
        document::BucketId id(16, i);
        int index = distr.getPreferredAvailableDisk(nodeState, 7, id);
        diskDist[index]+=1;
    }

    std::sort(diskDist.begin(), diskDist.end());

    CPPUNIT_ASSERT((diskDist[99]-diskDist[0])/(diskDist[99]) < 0.05);

}

void
DistributionTest::testDiskSkewGlobal()
{
    uint16_t num_disks = 10;
    uint16_t num_nodes = 10;
    Distribution distr(Distribution::getDefaultDistributionConfig(2, num_nodes, Distribution::MODULO_INDEX));
    std::vector<std::vector<float> > diskDist(num_nodes, std::vector<float>(num_disks));
    NodeState nodeState;
    nodeState.setDiskCount(num_disks);
    for(uint16_t idx=0; idx < num_nodes; idx++) {
        for(int i=0; i < 1000; i++) {
            document::BucketId id(16, i);
            int diskIndex = distr.getPreferredAvailableDisk(nodeState, idx, id);
            diskDist[idx][diskIndex]+=1;
        }
    }

    std::vector<float> diskDist2;
    for(uint16_t idx=0; idx < num_nodes; idx++) {
        for(uint16_t d=0; d < num_disks; d++) {
            diskDist2.push_back(diskDist[idx][d]);
        }
    }

    std::sort(diskDist2.begin(), diskDist2.end());
    
    double skew = (diskDist2[num_nodes*num_disks-1]-diskDist2[0])/(diskDist2[num_nodes*num_disks-1]);

    CPPUNIT_ASSERT(skew < 0.2);

}


void
DistributionTest::testDiskIntersection()
{
    uint16_t num_disks = 8;
    uint16_t num_nodes = 20;
    float max = 0;
    Distribution distr(Distribution::getDefaultDistributionConfig(2, num_nodes, Distribution::MODULO_INDEX));

    NodeState nodeState;
    nodeState.setDiskCount(num_disks);

    for(uint16_t i=0; i < num_nodes-1; i++) {
        for(uint16_t j=i+1; j < num_nodes; j++) {
            uint64_t count =0;
//std::cerr << "Comparing node " << i << " and node " << j << ":\n";
            for(int b=0; b < 1000; b++) {
                document::BucketId id(16, b);
                int idxI = distr.getPreferredAvailableDisk(nodeState, i, id);
                int idxJ = distr.getPreferredAvailableDisk(nodeState, j, id);
//if (b < 50) std::cerr << "  " << b << ": " << idxI << ", " << idxJ << "\n";
                if(idxI == idxJ){
                    count++;
                }
            }
            if(count > max){
                max = count;
            }
        }
    }
    if (max / 1000 > 0.5) {
        std::ostringstream ost;
        ost << "Value of " << max << " / " << 1000 << " is more than 0.5";
        CPPUNIT_FAIL(ost.str());
    }
}


void
DistributionTest::testSkew()
{
    const int buckets = 200000;
    const int nodes = 50;
    const size_t copies = 3;

    ClusterState systemState("storage:50");

    Distribution distr(Distribution::getDefaultDistributionConfig(copies, nodes));

    std::vector<std::pair<uint64_t, std::vector<uint16_t> > > _distribution(buckets);
    std::vector<int> _nodeCount(nodes, 0);

    for (int i = 0; i < buckets; i++) {
        _distribution[i].first = i * 100;
        _distribution[i].second = distr.getIdealStorageNodes(
                systemState, document::BucketId(26, i * 100));
        CPPUNIT_ASSERT_EQUAL(copies, _distribution[i].second.size());
        sort(_distribution[i].second.begin(), _distribution[i].second.end());
        unique(_distribution[i].second.begin(), _distribution[i].second.end());
        CPPUNIT_ASSERT_EQUAL(copies, _distribution[i].second.size());

        for (unsigned j = 0; j < _distribution[i].second.size(); j++) {
            _nodeCount[_distribution[i].second[j]]++;
        }
    }

/**
    for (int i = 0; i < nodes; i++) {
        fprintf(stderr, "%d ", _nodeCount[i]);
    }

*/
    sort(_nodeCount.begin(), _nodeCount.end());

/**
    // Check distribution
    for (int i = 0; i < nodes; i++) {
        fprintf(stderr, "%d ", _nodeCount[i]);
    }
*/

    double skew = _nodeCount[nodes - 1] - _nodeCount[0];
    skew /= _nodeCount[nodes - 1];
    //    fprintf(stderr, " skew = %f\n", skew);
    CPPUNIT_ASSERT_MESSAGE("Distribution skew too big (> 6%)", skew < 0.06);

}

// Get node with distribution farest from average
int
getMaxAbs(const std::vector<int> distribution, double avg, int start)
{
    int max = start;
    for (uint32_t i = 0; i < distribution.size(); i++) {
        if (std::fabs(distribution[i]-avg) > std::fabs(distribution[max]-avg))
            max = i;
    }
    return max;
}

void
getSkew(int n, std::vector<int> distribution, int &min, int &max, double &skew)
{
    min = 0;
    max = 0;

    for(int i=0; i<n; i++){
        if(distribution[i] < distribution[min])
            min = i;
        if(distribution[i] > distribution[max])
            max = i;
    }

    skew = distribution[max] - distribution[min];
    skew /= distribution[max];
}


double
get_K_lowest(std::vector<double> v, int k)
{
    double lowest[k];

    for (int i = 0; i < k; i++){
        lowest[i] = v[i];
    }

    for (uint32_t i = 0; i < v.size(); i++){
        for (int j = 0; j < k; j++){
            if( v[i] < lowest[j]){
                for (int l = k-1; l > j; l--){
                    lowest[l] = lowest[l-1];
                }
                lowest[j] = v[i];
                break;
            }
        }
    }

    return lowest[k-1];
}

void
DistributionTest::testDistribution()
{
    const int min_buckets = 1024*64;
    const int max_buckets = 1024*64;
    const int min_nodes = 2;
    const int max_nodes = 50;

    FastOS_File file;
    FastOS_File file_w;

    for(int b=min_buckets; b<= max_buckets; b+=b){
        std::ostringstream ostf;
        std::ostringstream ostf_w;
        std::ostringstream ostf_weights;

        for(int n=min_nodes; n<= max_nodes; n++){

            //Unweighted
            std::ostringstream s1;
            s1 << "storage:" << n << std::endl;
            ClusterState systemState(s1.str());

            Distribution distr(
                    Distribution::getDefaultDistributionConfig(3, n));

            std::vector<std::pair<uint64_t, std::vector<uint16_t> > > _distribution(b);
            std::vector<int> _nodeCount(n, 0);

            for (int i = 0; i < b; i++) {
                _distribution[i].first = i;
                _distribution[i].second = distr.getIdealStorageNodes(
                        systemState, document::BucketId(26, i));
                sort(_distribution[i].second.begin(), _distribution[i].second.end());
                unique(_distribution[i].second.begin(), _distribution[i].second.end());

                for (unsigned j = 0; j < _distribution[i].second.size(); j++) {
                    _nodeCount[_distribution[i].second[j]]++;
                }
            }

            int min = 0;
            int max = 0;
            for(int i=0; i<n; i++){
                if(_nodeCount[i] < _nodeCount[min])
                    min = i;
                if(_nodeCount[i] > _nodeCount[max])
                    max = i;
            }

            double skew = _nodeCount[max] - _nodeCount[min];
            skew /= _nodeCount[max];
            fprintf(stderr, "%d \t skew = %f\n", n, skew);
            CPPUNIT_ASSERT_MESSAGE("Distribution skew too big (> 10%)", skew < 0.1);
        }
    }
}

void
DistributionTest::testSkewWithDown()
{
    const int buckets = 200000;
    const int nodes = 50;
    const size_t copies = 3;

    ClusterState systemState("storage:50 .5.s:d .10.s:d .15.s:d .20.s:d "
                            ".25.s:d .30.s:d .35.s:d .40.s:d .45.s:d");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 50));

    std::vector<std::pair<uint64_t, std::vector<uint16_t> > > _distribution(buckets);
    std::vector<int> _nodeCount(nodes, 0);

    for (int i = 0; i < buckets; i++) {
        _distribution[i].second.reserve(copies);
    }

    for (int i = 0; i < buckets; i++) {
        _distribution[i].first = i * 100;
        _distribution[i].second = distr.getIdealStorageNodes(
                systemState, document::BucketId(26, _distribution[i].first));
        CPPUNIT_ASSERT_EQUAL(copies, _distribution[i].second.size());
        sort(_distribution[i].second.begin(), _distribution[i].second.end());
        unique(_distribution[i].second.begin(), _distribution[i].second.end());
        CPPUNIT_ASSERT_EQUAL(copies, _distribution[i].second.size());

        for (unsigned j = 0; j < _distribution[i].second.size(); j++) {
            _nodeCount[_distribution[i].second[j]]++;
        }
    }
    /*
    // Check distribution
    for (int i = 0; i < nodes; i++) {
        fprintf(stderr, "%d ", _nodeCount[i]);
    }
    */
    sort(_nodeCount.begin(), _nodeCount.end());
    int firstUp = 0;
    while (_nodeCount[firstUp] == 0) {
        firstUp++;
    }
    double skew = _nodeCount[nodes - 1] - _nodeCount[firstUp];
    skew /= _nodeCount[nodes - 1];
    //fprintf(stderr, " skew = %f\n", skew);
    CPPUNIT_ASSERT_MESSAGE("Distribution skew too big (> 5%)", skew < 0.05);
}

void
DistributionTest::testMove()
{
    // This test is quite fragile, it will break if the ideal state algorithm is
    // changed in such a way that Bucket 0x8b4f67ae remains on node 0 and 1 if
    // node 4 is added.
    std::vector<uint16_t> res;
    {
        ClusterState systemState("storage:3");
        document::BucketId bucket(16, 0x8b4f67ae);

        Distribution distr(Distribution::getDefaultDistributionConfig(2, 3));

        res = distr.getIdealStorageNodes(systemState, bucket);
        CPPUNIT_ASSERT_EQUAL(size_t(2), res.size());
    }

    std::vector<uint16_t> res2;
    {
        ClusterState systemState("storage:4");

        Distribution distr(Distribution::getDefaultDistributionConfig(2, 4));

        document::BucketId bucket(16, 0x8b4f67ae);

        res2 = distr.getIdealStorageNodes(systemState, bucket);
        CPPUNIT_ASSERT_EQUAL(size_t(2), res2.size());
    }

    sort(res.begin(), res.end());
    sort(res2.begin(), res2.end());

    std::vector<uint16_t> diff(2);
    std::vector<uint16_t>::iterator it;

    it=set_difference(res.begin(), res.end(), res2.begin(), res2.end(), diff.begin());
    CPPUNIT_ASSERT_EQUAL(1, int(it-diff.begin()));

}

void
DistributionTest::testMoveConstraints()
{
    ClusterState systemState("storage:10");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 12));

    std::vector<std::vector<uint16_t> > initBuckets(10000);
    for (unsigned i = 0; i < initBuckets.size(); i++) {
        initBuckets[i] = distr.getIdealStorageNodes(
                systemState, document::BucketId(16, i));
        sort(initBuckets[i].begin(), initBuckets[i].end());
    }

    {
        // Check that adding a down node has no effect
        std::vector<std::vector<uint16_t> > addedDownBuckets(10000);
        systemState = ClusterState("storage:11 .10.s:d");

        for (unsigned i = 0; i < addedDownBuckets.size(); i++) {
            addedDownBuckets[i] = distr.getIdealStorageNodes(
                    systemState, document::BucketId(16, i));
            sort(addedDownBuckets[i].begin(), addedDownBuckets[i].end());
        }
        for (unsigned i = 0; i < initBuckets.size(); i++) {
            if (initBuckets[i] != addedDownBuckets[i]) {
                std::cerr << i << ": ";
                std::copy(initBuckets[i].begin(), initBuckets[i].end(), std::ostream_iterator<uint32_t>(std::cerr, ","));
                std::cerr << " -> ";
                std::copy(addedDownBuckets[i].begin(), addedDownBuckets[i].end(), std::ostream_iterator<uint32_t>(std::cerr, ","));
                std::cerr << std::endl;

            }
            CPPUNIT_ASSERT(initBuckets[i] == addedDownBuckets[i]);
        }
    }

    {
        // Check that if we disable one node, we're not moving stuff away from
        // any other node
        std::vector<std::vector<uint16_t> > removed0Buckets(10000);
        systemState = ClusterState("storage:10 .0.s:d");

        for (unsigned i = 0; i < removed0Buckets.size(); i++) {
            removed0Buckets[i] = distr.getIdealStorageNodes(
                    systemState, document::BucketId(16, i));
            sort(removed0Buckets[i].begin(), removed0Buckets[i].end());
        }
        for (unsigned i = 0; i < initBuckets.size(); i++) {
            std::vector<uint16_t> movedAway;
            set_difference(initBuckets[i].begin(), initBuckets[i].end(),
                    removed0Buckets[i].begin(), removed0Buckets[i].end(),
                    back_inserter(movedAway));
            if (movedAway.size() > 0) {
                if (movedAway[0] != 0) {
                    std::cerr << i << ": ";
                    copy(initBuckets[i].begin(), initBuckets[i].end(), std::ostream_iterator<uint32_t>(std::cerr, ","));
                    std::cerr << " -> ";
                    copy(removed0Buckets[i].begin(), removed0Buckets[i].end(), std::ostream_iterator<uint32_t>(std::cerr, ","));
                    std::cerr << std::endl;
                }

                CPPUNIT_ASSERT_EQUAL((size_t)1, movedAway.size());
                CPPUNIT_ASSERT_EQUAL((uint16_t)0u, movedAway[0]);
            }
        }
    }

    {
        // Check that if we're adding one node, we're not moving stuff to any
        // other node
        std::vector<std::vector<uint16_t> > added10Buckets(10000);
        systemState = ClusterState("storage:11");

        for (unsigned i = 0; i < added10Buckets.size(); i++) {
            added10Buckets[i] = distr.getIdealStorageNodes(
                    systemState, document::BucketId(16, i));
            sort(added10Buckets[i].begin(), added10Buckets[i].end());
        }
        for (unsigned i = 0; i < initBuckets.size(); i++) {
            std::vector<uint16_t> movedInto;
            std::set_difference(added10Buckets[i].begin(), added10Buckets[i].end(),
                    initBuckets[i].begin(), initBuckets[i].end(),
                    std::inserter(movedInto, movedInto.begin()));
            if (movedInto.size() > 0) {
                CPPUNIT_ASSERT_EQUAL((size_t)1, movedInto.size());
                CPPUNIT_ASSERT_EQUAL((uint16_t)10, movedInto[0]);
            }
        }
    }
}

void
DistributionTest::testDistributionBits()
{
    ClusterState state1("bits:16 distributor:10");
    ClusterState state2("bits:19 distributor:10");

    Distribution distr(Distribution::getDefaultDistributionConfig(1, 10));

    std::ostringstream ost1;
    std::ostringstream ost2;

    for (unsigned i = 0; i < 100; i++) {
        int val = rand();
        uint32_t index = distr.getIdealDistributorNode(
                state1, document::BucketId(19, val), "u");
        ost1 << index << " ";
        index = distr.getIdealDistributorNode(
                state2, document::BucketId(19, val), "u");
        ost2 << index << " ";
    }

    CPPUNIT_ASSERT(ost1.str() != ost2.str());
}

void
DistributionTest::testRedundancyHierarchicalDistribution()
{
    ClusterState state("storage:10 distributor:10");

    Distribution distr1(Distribution::getDefaultDistributionConfig(1, 10));
    Distribution distr2(Distribution::getDefaultDistributionConfig(2, 10));

    for (unsigned i = 0; i < 100; i++) {
        uint16_t d1 = distr1.getIdealDistributorNode(
                state, document::BucketId(16, i), "u");
        uint16_t d2 = distr2.getIdealDistributorNode(
                state, document::BucketId(16, i), "u");
        CPPUNIT_ASSERT_EQUAL(d1, d2);
    }
}
/*
void
DistributionTest::testHierarchicalDistributionPerformance()
{
    std::ostringstream ost;
    ost << "redundancy 2\n"
        "group[62]\n"
        "group[0].name mycluster\n"
        "group[0].index 0\n"
        "group[0].partitions *\n"
        "group[0].nodes[0]\n";

    for (uint32_t i = 0; i < 21; ++i) {
        int idx = (i * 3) + 1;

        ost << "group[" << idx << "].name rack" << i << "\n"
            << "group[" << idx << "].index 0." << i << "\n"
            << "group[" << idx << "].partitions 1|*\n"
            << "group[" << idx << "].nodes[0]\n";

        for (uint32_t j = 0; j < 2; ++j) {
            idx++;

            ost << "group[" << idx << "].name switch" << idx << "\n"
                << "group[" << idx << "].index 0." << i << "." << j << "\n"
                << "group[" << idx << "].partitions 1|*\n"
                << "group[" << idx << "].nodes[50]\n";

            for (uint32_t n = 0; n < 50; ++n) {
                int nIdx = (i * 100 + j * 50 + n);
                ost << "group[" << idx << "].nodes[" << n << "].index " << nIdx << "\n";
            }
        }
    }

    std::cerr << ost.str() << "\n";

    Distribution distr(vespa::config::content::StorDistributionConfig(
                               vespalib::StringTokenizer(ost.str(), "\n", "").getTokens()));
    ClusterState state("distributor:2100 storage:2100");

    uint32_t timeNow = time(NULL);
    uint32_t numBuckets = 1000000;

    std::vector<uint16_t> nodes;
    for (uint32_t i = 0; i < numBuckets; i++) {
        nodes = distr.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
    }

    uint32_t timeSpent = time(NULL) - timeNow;
    std::cerr << "Did " << numBuckets << " in " << timeSpent << " seconds. (" << ((double)numBuckets / (double)timeSpent) << " ops/sec)\n";
}
*/

void
DistributionTest::testHierarchicalDistribution()
{
    std::string distConfig(
            "redundancy 4\n"
            "group[3]\n"
            "group[0].name \"invalid\"\n"
            "group[0].index \"invalid\"\n"
            "group[0].partitions 2|*\n"
            "group[0].nodes[0]\n"
            "group[1].name rack0\n"
            "group[1].index 0\n"
            "group[1].nodes[3]\n"
            "group[1].nodes[0].index 0\n"
            "group[1].nodes[1].index 1\n"
            "group[1].nodes[2].index 2\n"
            "group[2].name rack1\n"
            "group[2].index 1\n"
            "group[2].nodes[3]\n"
            "group[2].nodes[0].index 3\n"
            "group[2].nodes[1].index 4\n"
            "group[2].nodes[2].index 5\n");
    Distribution distr(distConfig);
    ClusterState state("distributor:6 storage:6");

    for (uint32_t i = 0; i < 3; ++i) {
        CPPUNIT_ASSERT_EQUAL(
                vespalib::string("rack0"),
                distr.getNodeGraph().getGroupForNode(i)->getName());
    }
    for (uint32_t i = 3; i < 6; ++i) {
        CPPUNIT_ASSERT_EQUAL(
                vespalib::string("rack1"),
                distr.getNodeGraph().getGroupForNode(i)->getName());
    }

    std::vector<int> mainNode(6);
    for (uint32_t i=0; i<100; ++i) {
        std::vector<uint16_t> nodes = distr.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        CPPUNIT_ASSERT_EQUAL((size_t) 4, nodes.size());
        CPPUNIT_ASSERT(nodes[0] < mainNode.size());
        ++mainNode[nodes[0]];
    }
    std::vector<int> expectedMains(6);
    expectedMains[0] = 9;
    expectedMains[1] = 21;
    expectedMains[2] = 18;
    expectedMains[3] = 16;
    expectedMains[4] = 16;
    expectedMains[5] = 20;
    CPPUNIT_ASSERT_EQUAL(expectedMains, mainNode);
}

void
DistributionTest::testGroupCapacity()
{
    std::string distConfig(
            "redundancy 1\n"
            "group[3]\n"
            "group[0].name \"invalid\"\n"
            "group[0].index \"invalid\"\n"
            "group[0].partitions *\n"
            "group[0].nodes[0]\n"
            "group[1].name rack0\n"
            "group[1].index 0\n"
            "group[1].capacity 1.0\n"
            "group[1].nodes[3]\n"
            "group[1].nodes[0].index 0\n"
            "group[1].nodes[1].index 1\n"
            "group[1].nodes[2].index 2\n"
            "group[2].name rack1\n"
            "group[2].index 1\n"
            "group[2].capacity 4.0\n"
            "group[2].nodes[3]\n"
            "group[2].nodes[0].index 3\n"
            "group[2].nodes[1].index 4\n"
            "group[2].nodes[2].index 5\n");
    Distribution distr(distConfig);
    ClusterState state("distributor:6 storage:6");

    int group0count = 0;
    int group1count = 0;
    for (uint32_t i = 0; i < 1000; i++) {
        std::vector<uint16_t> nodes = distr.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        if (nodes[0] == 0 || nodes[0] == 1 || nodes[0] == 2) {
            group0count++;
        }
        if (nodes[0] == 3 || nodes[0] == 4 || nodes[0] == 5) {
            group1count++;
        }
    }

    //std::cerr << "Group 0 is " << group0count << " 1 is " << group1count << "\n";

    CPPUNIT_ASSERT(group0count > 180 && group0count < 220);
    CPPUNIT_ASSERT_EQUAL(1000 - group0count, group1count);
}

void
DistributionTest::testHierarchicalNoRedistribution()
{
    std::string distConfig(
            "redundancy 2\n"
            "group[5]\n"
            "group[0].name \"invalid\"\n"
            "group[0].index \"invalid\"\n"
            "group[0].partitions *|*\n"
            "group[0].nodes[0]\n"
            "group[1].name switch0\n"
            "group[1].index 0\n"
            "group[1].partitions 1|*\n"
            "group[1].nodes[0]\n"
            "group[2].name rack0\n"
            "group[2].index 0.0\n"
            "group[2].nodes[1]\n"
            "group[2].nodes[0].index 0\n"
            "group[3].name rack1\n"
            "group[3].index 0.1\n"
            "group[3].nodes[1]\n"
            "group[3].nodes[0].index 1\n"
            "group[4].name switch0\n"
            "group[4].index 1\n"
            "group[4].partitions *\n"
            "group[4].nodes[0]\n"
            "group[5].name rack0\n"
            "group[5].index 1.0\n"
            "group[5].nodes[1]\n"
            "group[5].nodes[0].index 2\n"
            "group[6].name rack1\n"
            "group[6].index 1.1\n"
            "group[6].nodes[1]\n"
            "group[6].nodes[0].index 3\n");
    Distribution distribution(distConfig);

    ClusterState state("version:12 storage:4 distributor:4");

    std::vector<uint16_t> nodes;
    std::vector< std::vector<uint16_t> > distr(4);
    int numBuckets = 1000;

    for (int i = 0; i < numBuckets; i++) {
        nodes = distribution.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        for (uint16_t j=0; j<nodes.size(); ++j) {
            distr[nodes[j]].push_back(i);
        }
        nodes.clear();
    }

    std::vector<uint16_t>::iterator it;
    std::vector<uint16_t> v(1000);

    it=set_intersection (distr[0].begin(), distr[0].end(), distr[1].begin(), distr[1].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(0, int(it-v.begin()));
    v.clear();

    it=set_intersection (distr[2].begin(), distr[2].end(), distr[3].begin(), distr[3].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(0, int(it-v.begin()));
    v.clear();

    it=set_union (distr[0].begin(), distr[0].end(), distr[1].begin(), distr[1].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(numBuckets, int(it-v.begin()));
    v.clear();

    it=set_union (distr[2].begin(), distr[2].end(), distr[3].begin(), distr[3].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(numBuckets, int(it-v.begin()));
    v.clear();

    state.setNodeState(Node(NodeType::STORAGE, 0),
                       NodeState(NodeType::STORAGE, State::DOWN));

    std::vector< std::vector<uint16_t> > distr2(4);

    for (int i = 0; i < numBuckets; i++) {
        nodes = distribution.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        for (uint16_t j=0; j<nodes.size(); ++j) {
            CPPUNIT_ASSERT(0 != nodes[j]);
            distr2[nodes[j]].push_back(i);
        }
        nodes.clear();
    }

    CPPUNIT_ASSERT_EQUAL((size_t)0, distr2[0].size());
    v.clear();

    it=set_difference (distr[1].begin(), distr[1].end(), distr2[1].begin(), distr2[1].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(0, int(it-v.begin()));
    v.clear();

    it=set_difference (distr[2].begin(), distr[2].end(), distr2[2].begin(), distr2[2].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(0, int(it-v.begin()));
    v.clear();

    it=set_difference (distr[3].begin(), distr[3].end(), distr2[3].begin(), distr2[3].end(), v.begin());
    CPPUNIT_ASSERT_EQUAL(0, int(it-v.begin()));
    v.clear();

    state = ClusterState(
            "distributor:5 .0.s:d storage:5 .0.s:d .1.s:d .1.m:foo\\x20bar");
    std::ostringstream ost;
    state.printStateGroupwise(ost, distribution, true, "");
    CPPUNIT_ASSERT_EQUAL(std::string("\n"
        "ClusterState(Version: 0, Cluster state: Up, Distribution bits: 16) {\n"
        "  Top group. 2 branches with distribution *|* {\n"
        "    Group 0: switch0. 2 branches with distribution 1|* {\n"
        "      Group 0: rack0. 1 node [0] {\n"
        "        distributor.0: Down\n"
        "        storage.0: Down\n"
        "      }\n"
        "      Group 1: rack1. 1 node [1] {\n"
        "        storage.1: Down: foo bar\n"
        "      }\n"
        "    }\n"
        "    Group 1: switch0. 2 branches with distribution * {\n"
        "      Group 0: rack0. 1 node [2] {\n"
        "        All nodes in group up and available.\n"
        "      }\n"
        "      Group 1: rack1. 1 node [3] {\n"
        "        All nodes in group up and available.\n"
        "      }\n"
        "    }\n"
        "  }\n"
        "}"), "\n" + ost.str());
}

namespace {
    std::string groupConfig("group[3]\n"
                            "group[0].name \"invalid\"\n"
                            "group[0].index \"invalid\"\n"
                            "group[0].partitions 2|*\n"
                            "group[0].nodes[0]\n"
                            "group[1].name rack0\n"
                            "group[1].index 0\n"
                            "group[1].nodes[3]\n"
                            "group[1].nodes[0].index 0\n"
                            "group[1].nodes[1].index 1\n"
                            "group[1].nodes[2].index 2\n"
                            "group[2].name rack1\n"
                            "group[2].index 1\n"
                            "group[2].nodes[3]\n"
                            "group[2].nodes[0].index 3\n"
                            "group[2].nodes[1].index 4\n"
                            "group[2].nodes[2].index 5\n");
}

void
DistributionTest::testActivePerGroup()
{
    typedef Distribution::IndexList IndexList;
        // Disabled feature
    {
        Distribution distr("redundancy 4\n" + groupConfig);
        CPPUNIT_ASSERT_EQUAL(false, distr.activePerGroup());
    }
        // All nodes split
    {
        Distribution distr("redundancy 4\n"
                           "active_per_leaf_group true\n" + groupConfig);
        IndexList global;
        global.push_back(0);
        global.push_back(1);
        global.push_back(2);
        global.push_back(3);
        global.push_back(4);
        global.push_back(5);
        std::vector<IndexList> actual(distr.splitNodesIntoLeafGroups(global));
        std::vector<IndexList> expected;
        expected.push_back(IndexList());
        expected.push_back(IndexList());
        expected[0].push_back(0);
        expected[0].push_back(1);
        expected[0].push_back(2);
        expected[1].push_back(3);
        expected[1].push_back(4);
        expected[1].push_back(5);
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
        // Only nodes in one group
    {
        Distribution distr("redundancy 4\n"
                           "active_per_leaf_group true\n" + groupConfig);
        IndexList global;
        global.push_back(0);
        global.push_back(1);
        global.push_back(2);
        std::vector<IndexList> actual(distr.splitNodesIntoLeafGroups(global));
        std::vector<IndexList> expected;
        expected.push_back(IndexList());
        expected[0].push_back(0);
        expected[0].push_back(1);
        expected[0].push_back(2);
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
        // No nodes
    {
        Distribution distr("redundancy 4\n"
                           "active_per_leaf_group true\n" + groupConfig);
        IndexList global;
        std::vector<IndexList> actual(distr.splitNodesIntoLeafGroups(global));
        std::vector<IndexList> expected;
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
        // Random nodes
    {
        Distribution distr("redundancy 4\n"
                           "active_per_leaf_group true\n" + groupConfig);
        IndexList global;
        global.push_back(5);
        global.push_back(1);
        global.push_back(3);
        std::vector<IndexList> actual(distr.splitNodesIntoLeafGroups(global));
        std::vector<IndexList> expected;
        expected.push_back(IndexList());
        expected.push_back(IndexList());
        expected[0].push_back(1);
        expected[1].push_back(5);
        expected[1].push_back(3);
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
}

void
DistributionTest::testHierarchicalDistributeLessThanRedundancy()
{
    Distribution distr("redundancy 4\nactive_per_leaf_group true\n" + groupConfig);
    ClusterState state("storage:6");
    std::vector<uint16_t> actual;

    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 4);
        std::vector<uint16_t> expected({3, 5, 1, 2});
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 3);
        std::vector<uint16_t> expected({3, 5, 1});
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 2);
        std::vector<uint16_t> expected({3, 1});
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 1);
        std::vector<uint16_t> expected({3});
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
}

void
DistributionTest::testEmptyAndCopy()
{
    Distribution d;
    CPPUNIT_ASSERT(d.getNodeGraph().isLeafGroup());
    CPPUNIT_ASSERT_EQUAL(uint16_t(0), d.getRedundancy());
    CPPUNIT_ASSERT_EQUAL(uint16_t(0), d.getReadyCopies());
    Distribution d2(d.serialize());
    CPPUNIT_ASSERT_EQUAL(uint16_t(0), d2.getRedundancy());
    CPPUNIT_ASSERT_EQUAL(uint16_t(0), d2.getReadyCopies());
    Distribution d3(Distribution::getDefaultDistributionConfig());
    d = d3;
    CPPUNIT_ASSERT_EQUAL(uint16_t(2), d.getRedundancy());
    CPPUNIT_ASSERT_EQUAL(uint16_t(1), d.getReadyCopies());
}

}
