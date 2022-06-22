// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-stor-distribution.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/config/subscription/configuri.h>
#include <vespa/fastos/file.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/distribution/idealnodecalculator.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/lexical_cast.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <gmock/gmock.h>
#include <chrono>
#include <thread>
#include <fstream>

using namespace ::testing;

namespace storage::lib {

template <typename T>
T readConfig(const config::ConfigUri & uri)
{
    return *config::ConfigGetter<T>::getConfig(uri.getConfigId(), uri.getContext());
}

TEST(DistributionTest, test_verify_java_distributions)
{
    std::vector<std::string> tests;
    tests.push_back("capacity");
    tests.push_back("depth2");
    tests.push_back("depth3");
    tests.push_back("retired");
    for (uint32_t i=0; i<tests.size(); ++i) {
        std::string test = tests[i];
        std::string mystate;
        {
            std::ifstream in("distribution/testdata/java_" + test + ".state");
            in >> mystate;
            in.close();
        }
        ClusterState state(mystate);
        Distribution distr(readConfig<vespa::config::content::StorDistributionConfig>(
                config::ConfigUri("file:distribution/testdata/java_" + test + ".cfg")));
        std::ofstream of("distribution/testdata/cpp_" + test + ".distribution");

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
        cmd << "diff -u " << "distribution/testdata/cpp_" << test << ".distribution "
            << "distribution/testdata/java_" << test << ".distribution";
        int result = system(cmd.str().c_str());
        EXPECT_EQ(0, result) << "Failed distribution sync test: " + test;
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

void
verifyJavaDistribution(const vespalib::string& name,
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
        try {
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
            EXPECT_EQ(results[i].nodes.toString(), nodes.toString()) << testId;
            if (results[i].nodes.size() > 0) {
                EXPECT_EQ(vespalib::string("NONE"), results[i].failure) << testId;
            } else {
                EXPECT_EQ(vespalib::string("NO_DISTRIBUTORS_AVAILABLE"), results[i].failure) << testId;
            }
        } catch (vespalib::Exception& e) {
            EXPECT_EQ(results[i].failure, e.getMessage()) << testId;
        }
    }
}

}

auto readFile(const std::string & filename) {
    vespalib::File file(filename);
    file.open(vespalib::File::READONLY);

    std::vector<char> buf(file.getFileSize());
    off_t read = file.read(&buf[0], buf.size(), 0);

    assert(read == file.getFileSize());
    return buf;
}

TEST(DistributionTest, test_verify_java_distributions_2)
{
    vespalib::DirectoryList files(
            vespalib::listDirectory("distribution/testdata"));
    for (uint32_t i=0, n=files.size(); i<n; ++i) {
        size_t pos = files[i].find(".java.results");
        if (pos == vespalib::string::npos || pos + 13 != files[i].size()) {
            //std::cerr << "Skipping unmatched file '" << files[i] << "'.\n";
            continue;
        }

        vespalib::string name(files[i].substr(0, pos));
        using namespace vespalib::slime;
        vespalib::Slime slime;

        auto buf = readFile("distribution/testdata/" + files[i]);
        auto size = JsonFormat::decode({&buf[0], buf.size()}, slime);

        if (size == 0) {
            std::cerr << "\n\nSize of " << files[i] << " is 0. Maybe is not generated yet? Taking a 5 second nap!";
            std::this_thread::sleep_for(std::chrono::seconds(5));

            buf = readFile("distribution/testdata/" + files[i]);
            size = JsonFormat::decode({&buf[0], buf.size()}, slime);

            if (size == 0) {
                std::cerr << "\n\nError verifying " << files[i] << ". File doesn't exist or is empty";
            }
        }

        ASSERT_TRUE(size != 0);
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
            ASSERT_EQ(int(0), int(*end));
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

TEST(DistributionTest, test_unchanged_distribution)
{
    ClusterState state("distributor:10 storage:10");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    std::ifstream in("distribution/testdata/41-distributordistribution");

    for (unsigned i = 0; i < 64_Ki; i++) {
        uint16_t node = distr.getIdealDistributorNode(
                state, document::BucketId(16, i), "u");

        char buf[100];
        in.getline(buf, 100);

        EXPECT_EQ(atoi(buf), (int)node);
    }
}

namespace {

struct MyTest {
    const NodeType* _nodeType;
    std::string _state;
    std::unique_ptr<Distribution> _distribution;
    uint32_t _bucketsToTest;
    const char* _upStates;
    uint16_t _redundancy;

    MyTest();
    ~MyTest();

    MyTest& state(const std::string& s) {
        _state = s;
        return *this;
    }

    MyTest& upStates(const char* ups) {
        _upStates = ups;
        return *this;
    }

    MyTest& nodeType(const NodeType& type) {
        _nodeType = &type;
        return *this;
    }

    MyTest& distribution(Distribution* d) {
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
};

MyTest::MyTest()
    : _nodeType(&NodeType::STORAGE),
      _state("distributor:10 storage:10"),
      _distribution(new Distribution(Distribution::getDefaultDistributionConfig(3, 10))),
      _bucketsToTest(100),
      _upStates("uir"),
      _redundancy(2)
{ }
MyTest::~MyTest() = default;

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
    EXPECT_EQ(exp123, cnt123); \
}

TEST(DistributionTest, test_down)
{
    ASSERT_BUCKET_NODE_COUNTS(
            MyTest().state("storage:10 .4.s:m .5.s:m .6.s:d .7.s:d .9.s:r")
                    .upStates("u"),
            "0:+ 1:+ 2:+ 3:+ 8:+");

    ASSERT_BUCKET_NODE_COUNTS(
            MyTest().state("storage:10 .4.s:m .5.s:m .6.s:d .7.s:d .9.s:r")
                    .upStates("ur"),
            "0:+ 1:+ 2:+ 3:+ 8:+ 9:+");
}

TEST(DistributionTest, test_serialize_deserialize)
{
    MyTest t1;
    MyTest t2;
    t2.distribution(new Distribution(t1._distribution->serialize()));
    EXPECT_EQ(t1.getNodeCounts(), t2.getNodeCounts());
}

TEST(DistributionTest, test_initializing)
{
    ASSERT_BUCKET_NODE_COUNTS(
            MyTest().state("distributor:3 .0.s:i .1.s:i .2.s:i")
                    .upStates("ui")
                    .nodeType(NodeType::DISTRIBUTOR),
            "0:+ 1:+ 2:+");
}

TEST(DistributionTest, testHighSplitBit)
{
    // Only 3 nodes of 10 are up => all copies should end on the 3 nodes and
    // none on the down nodes
    ClusterState state("storage:100");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 100));

    vespalib::asciistream ost1;
    vespalib::asciistream ost2;

    for (uint32_t bits = 33; bits < 36; ++bits) {
        uint64_t base = 0x23456789;
        base |= (1L << bits);

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

    EXPECT_EQ(ost1.str(), ost2.str());
}

TEST(DistributionTest, test_distribution)
{
    const int min_buckets = 64_Ki;
    const int max_buckets = 64_Ki;
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
            EXPECT_LT(skew, 0.1) << "Distribution skew too big (> 10%)";
        }
    }
}

TEST(DistributionTest, test_move)
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
        EXPECT_EQ(size_t(2), res.size());
    }

    std::vector<uint16_t> res2;
    {
        ClusterState systemState("storage:4");

        Distribution distr(Distribution::getDefaultDistributionConfig(2, 4));

        document::BucketId bucket(16, 0x8b4f67ae);

        res2 = distr.getIdealStorageNodes(systemState, bucket);
        EXPECT_EQ(size_t(2), res2.size());
    }

    sort(res.begin(), res.end());
    sort(res2.begin(), res2.end());

    std::vector<uint16_t> diff(2);
    std::vector<uint16_t>::iterator it;

    it=set_difference(res.begin(), res.end(), res2.begin(), res2.end(), diff.begin());
    EXPECT_EQ(1, int(it-diff.begin()));
}

TEST(DistributionTest, test_move_constraints)
{
    ClusterState clusterState("storage:10");

    Distribution distr(Distribution::getDefaultDistributionConfig(3, 12));

    std::vector<std::vector<uint16_t> > initBuckets(10000);
    for (unsigned i = 0; i < initBuckets.size(); i++) {
        initBuckets[i] = distr.getIdealStorageNodes(
                clusterState, document::BucketId(16, i));
        sort(initBuckets[i].begin(), initBuckets[i].end());
    }

    {
        // Check that adding a down node has no effect
        std::vector<std::vector<uint16_t> > addedDownBuckets(10000);
        ClusterState systemState("storage:11 .10.s:d");

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
            EXPECT_EQ(initBuckets[i], addedDownBuckets[i]);
        }
    }

    {
        // Check that if we disable one node, we're not moving stuff away from
        // any other node
        std::vector<std::vector<uint16_t> > removed0Buckets(10000);
        ClusterState systemState("storage:10 .0.s:d");

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

                ASSERT_EQ((size_t)1, movedAway.size());
                EXPECT_EQ((uint16_t)0u, movedAway[0]);
            }
        }
    }

    {
        // Check that if we're adding one node, we're not moving stuff to any
        // other node
        std::vector<std::vector<uint16_t> > added10Buckets(10000);
        ClusterState systemState("storage:11");

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
                ASSERT_EQ((size_t)1, movedInto.size());
                EXPECT_EQ((uint16_t)10, movedInto[0]);
            }
        }
    }
}

TEST(DistributionTest, test_distribution_bits)
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

    EXPECT_NE(ost1.str(), ost2.str());
}

TEST(DistributionTest, test_redundancy_hierarchical_distribution)
{
    ClusterState state("storage:10 distributor:10");

    Distribution distr1(Distribution::getDefaultDistributionConfig(1, 10));
    Distribution distr2(Distribution::getDefaultDistributionConfig(2, 10));

    for (unsigned i = 0; i < 100; i++) {
        uint16_t d1 = distr1.getIdealDistributorNode(
                state, document::BucketId(16, i), "u");
        uint16_t d2 = distr2.getIdealDistributorNode(
                state, document::BucketId(16, i), "u");
        EXPECT_EQ(d1, d2);
    }
}

TEST(DistributionTest, test_hierarchical_distribution)
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
        EXPECT_EQ(
                vespalib::string("rack0"),
                distr.getNodeGraph().getGroupForNode(i)->getName());
    }
    for (uint32_t i = 3; i < 6; ++i) {
        EXPECT_EQ(
                vespalib::string("rack1"),
                distr.getNodeGraph().getGroupForNode(i)->getName());
    }

    std::vector<int> mainNode(6);
    for (uint32_t i=0; i<100; ++i) {
        std::vector<uint16_t> nodes = distr.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        ASSERT_EQ((size_t) 4, nodes.size());
        EXPECT_LT(nodes[0], mainNode.size());
        ++mainNode[nodes[0]];
    }
    std::vector<int> expectedMains(6);
    expectedMains[0] = 9;
    expectedMains[1] = 21;
    expectedMains[2] = 18;
    expectedMains[3] = 16;
    expectedMains[4] = 16;
    expectedMains[5] = 20;
    EXPECT_EQ(expectedMains, mainNode);
}

TEST(DistributionTest, test_group_capacity)
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

    EXPECT_TRUE(group0count > 180 && group0count < 220);
    EXPECT_EQ(1000 - group0count, group1count);
}

TEST(DistributionTest, test_hierarchical_no_redistribution)
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
    size_t numBuckets = 1000;

    for (size_t i = 0; i < numBuckets; i++) {
        nodes = distribution.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        for (uint16_t j=0; j<nodes.size(); ++j) {
            distr[nodes[j]].push_back(i);
        }
        nodes.clear();
    }

    std::vector<uint16_t> v;

    set_intersection (distr[0].begin(), distr[0].end(), distr[1].begin(), distr[1].end(), back_inserter(v));
    EXPECT_EQ(0, v.size());
    v.clear();

    set_intersection (distr[2].begin(), distr[2].end(), distr[3].begin(), distr[3].end(), back_inserter(v));
    EXPECT_EQ(0, v.size());
    v.clear();

    set_union (distr[0].begin(), distr[0].end(), distr[1].begin(), distr[1].end(), back_inserter(v));
    EXPECT_EQ(numBuckets, v.size());
    v.clear();

    set_union (distr[2].begin(), distr[2].end(), distr[3].begin(), distr[3].end(), back_inserter(v));
    EXPECT_EQ(numBuckets, v.size());
    v.clear();

    state.setNodeState(Node(NodeType::STORAGE, 0),
                       NodeState(NodeType::STORAGE, State::DOWN));

    std::vector< std::vector<uint16_t> > distr2(4);

    for (size_t i = 0; i < numBuckets; i++) {
        nodes = distribution.getIdealStorageNodes(
                state, document::BucketId(16, i), "u");
        for (uint16_t j=0; j<nodes.size(); ++j) {
            ASSERT_TRUE(0 != nodes[j]);
            distr2[nodes[j]].push_back(i);
        }
        nodes.clear();
    }

    ASSERT_EQ((size_t)0, distr2[0].size());
    v.clear();

    set_difference (distr[1].begin(), distr[1].end(), distr2[1].begin(), distr2[1].end(), back_inserter(v));
    EXPECT_EQ(0, v.size());
    v.clear();

    set_difference (distr[2].begin(), distr[2].end(), distr2[2].begin(), distr2[2].end(), back_inserter(v));
    EXPECT_EQ(0, v.size());
    v.clear();

    set_difference (distr[3].begin(), distr[3].end(), distr2[3].begin(), distr2[3].end(), back_inserter(v));
    EXPECT_EQ(0, v.size());
    v.clear();

    ClusterState state2("distributor:5 .0.s:d storage:5 .0.s:d .1.s:d .1.m:foo\\x20bar");
    std::ostringstream ost;
    state2.printStateGroupwise(ost, distribution, true, "");
    EXPECT_EQ(std::string("\n"
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

TEST(DistributionTest, test_active_per_group)
{
    typedef Distribution::IndexList IndexList;
        // Disabled feature
    {
        Distribution distr("redundancy 4\n" + groupConfig);
        EXPECT_FALSE(distr.activePerGroup());
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
        EXPECT_EQ(expected, actual);
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
        EXPECT_EQ(expected, actual);
    }
        // No nodes
    {
        Distribution distr("redundancy 4\n"
                           "active_per_leaf_group true\n" + groupConfig);
        IndexList global;
        std::vector<IndexList> actual(distr.splitNodesIntoLeafGroups(global));
        std::vector<IndexList> expected;
        EXPECT_EQ(expected, actual);
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
        EXPECT_EQ(expected, actual);
    }
}

TEST(DistributionTest, test_hierarchical_distribute_less_than_redundancy)
{
    Distribution distr("redundancy 4\nactive_per_leaf_group true\n" + groupConfig);
    ClusterState state("storage:6");
    std::vector<uint16_t> actual;

    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 4);
        std::vector<uint16_t> expected({3, 5, 1, 2});
        EXPECT_EQ(expected, actual);
    }
    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 3);
        std::vector<uint16_t> expected({3, 5, 1});
        EXPECT_EQ(expected, actual);
    }
    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 2);
        std::vector<uint16_t> expected({3, 1});
        EXPECT_EQ(expected, actual);
    }
    {
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, 0), actual, "uim", 1);
        std::vector<uint16_t> expected({3});
        EXPECT_EQ(expected, actual);
    }
}

TEST(DistributionTest, wildcard_top_level_distribution_gives_expected_node_results) {
    std::string raw_config = R"(redundancy 2
initial_redundancy 2
ensure_primary_persisted true
ready_copies 2
active_per_leaf_group false
distributor_auto_ownership_transfer_on_whole_group_down true
group[0].index "invalid"
group[0].name "invalid"
group[0].capacity 5
group[0].partitions "*"
group[1].index "0"
group[1].name "switch0"
group[1].capacity 3
group[1].partitions ""
group[1].nodes[0].index 0
group[1].nodes[0].retired false
group[1].nodes[1].index 1
group[1].nodes[1].retired false
group[1].nodes[2].index 2
group[1].nodes[2].retired false
group[2].index "1"
group[2].name "switch1"
group[2].capacity 2
group[2].partitions ""
group[2].nodes[0].index 3
group[2].nodes[0].retired false
group[2].nodes[1].index 4
group[2].nodes[1].retired false
)";
    Distribution distr(raw_config);
    ClusterState state("version:1 distributor:5 storage:5");

    auto nodes_of = [&](uint32_t bucket){
        std::vector<uint16_t> actual;
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, bucket), actual);
        return actual;
    };

    EXPECT_THAT(nodes_of(1), ElementsAre(0, 2));
    EXPECT_THAT(nodes_of(2), ElementsAre(2, 0));
    EXPECT_THAT(nodes_of(3), ElementsAre(4, 3));
    EXPECT_THAT(nodes_of(4), ElementsAre(3, 4));
    EXPECT_THAT(nodes_of(5), ElementsAre(4, 3));
    EXPECT_THAT(nodes_of(6), ElementsAre(1, 0));
    EXPECT_THAT(nodes_of(7), ElementsAre(2, 0));
}

namespace {

std::string generate_config_with_n_1node_groups(int n_groups) {
    std::ostringstream config_os;
    std::ostringstream partition_os;
    for (int i = 0; i < n_groups - 1; ++i) {
        partition_os << "1|";
    }
    partition_os << '*';
    config_os << "redundancy " << n_groups << "\n"
              << "initial_redundancy " << n_groups << "\n"
              << "ensure_primary_persisted true\n"
              << "ready_copies " << n_groups << "\n"
              << "active_per_leaf_group true\n"
              << "distributor_auto_ownership_transfer_on_whole_group_down true\n"
              << "group[0].index \"invalid\"\n"
              << "group[0].name \"invalid\"\n"
              << "group[0].capacity " << n_groups << "\n"
              << "group[0].partitions \"" << partition_os.str() << "\"\n";

    for (int i = 0; i < n_groups; ++i) {
        int g = i + 1;
        config_os << "group[" << g << "].index \"" << i << "\"\n"
                  << "group[" << g << "].name \"group" << g << "\"\n"
                  << "group[" << g << "].capacity 1\n"
                  << "group[" << g << "].partitions \"\"\n"
                  << "group[" << g << "].nodes[0].index \"" << i << "\"\n"
                  << "group[" << g << "].nodes[0].retired false\n";
    }
    return config_os.str();
}

std::string generate_state_with_n_nodes_up(int n_nodes) {
    std::ostringstream state_os;
    state_os << "version:1 bits:8 distributor:" << n_nodes << " storage:" << n_nodes;
    return state_os.str();
}

}

TEST(DistributionTest, DISABLED_benchmark_ideal_state_for_many_groups) {
    const int n_groups = 150;
    Distribution distr(generate_config_with_n_1node_groups(n_groups));
    ClusterState state(generate_state_with_n_nodes_up(n_groups));

    std::vector<uint16_t> actual;
    uint32_t bucket = 0;
    auto min_time = vespalib::BenchmarkTimer::benchmark([&]{
        distr.getIdealNodes(NodeType::STORAGE, state, document::BucketId(16, (bucket++ & 0xffffU)), actual);
    }, 5.0);
    fprintf(stderr, "%.10f seconds\n", min_time);
}

}
