// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/config/helper/configgetter.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/operationtargetresolverimpl.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct OperationTargetResolverTest : Test, DistributorStripeTestUtil {
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    const document::DocumentType* _html_type;
    std::unique_ptr<Operation> op;

    BucketInstanceList getInstances(const BucketId& bid,
                                    bool stripToRedundancy);

    void SetUp() override {
        _repo.reset(new document::DocumentTypeRepo(
                *config::ConfigGetter<DocumenttypesConfig>::getConfig(
                    "config-doctypes",
                    config::FileSpec("../config-doctypes.cfg"))));
        _html_type = _repo->getDocumentType("text/html");
        createLinks();
    };

    void TearDown() override {
        close();
    }
};


namespace {

// Create assertion that makes it easy to write tests, and report correct
// line for problem at command line
#define MY_ASSERT_THAT(id) \
{ \
    struct MyAsserter : public Asserter { \
        void assertEqualMsg(std::string t1, OperationTargetList t2, \
                            OperationTargetList t3) override { \
            ASSERT_EQ(t2, t3) << t1; \
        } \
    }; \
    _asserters.push_back(new MyAsserter); \
} \
TestTargets::createTest(id, *this, *_asserters.back())

struct Asserter {
    virtual ~Asserter() {}
    virtual void assertEqualMsg(std::string t1,
                        OperationTargetList t2,
                        OperationTargetList t3) = 0;
};
std::vector<Asserter*> _asserters;
struct TestTargets {
    const BucketId& _id;
    OperationTargetList _expected;
    OperationTargetResolverTest& _test;
    Asserter& _asserter;

    TestTargets(const BucketId& id,
                OperationTargetResolverTest& test,
                Asserter& asserter)
        : _id(id), _test(test), _asserter(asserter) {}

    ~TestTargets() {
        BucketInstanceList result(_test.getInstances(_id, true));
        BucketInstanceList all(_test.getInstances(_id, false));
        _asserter.assertEqualMsg(
                all.toString(), _expected, result.createTargets(makeBucketSpace()));
        delete _asserters.back();
        _asserters.pop_back();
    }

    TestTargets& sendsTo(const BucketId& id, uint16_t node) {
        _expected.push_back(OperationTarget(
                makeDocumentBucket(id), lib::Node(lib::NodeType::STORAGE, node), false));
        return *this;
    }
    TestTargets& createsAt(const BucketId& id, uint16_t node) {
        _expected.push_back(OperationTarget(
                makeDocumentBucket(id), lib::Node(lib::NodeType::STORAGE, node), true));
        return *this;
    }

    static TestTargets createTest(const BucketId& id,
                                  OperationTargetResolverTest& test,
                                  Asserter& asserter)
    {
        return TestTargets(id, test, asserter);
    }
};
    
} // anonymous

BucketInstanceList
OperationTargetResolverTest::getInstances(const BucketId& id,
                                          bool stripToRedundancy)
{
    lib::IdealNodeCalculatorImpl idealNodeCalc;
    auto &bucketSpaceRepo(operation_context().bucket_space_repo());
    auto &distributorBucketSpace(bucketSpaceRepo.get(makeBucketSpace()));
    idealNodeCalc.setDistribution(distributorBucketSpace.getDistribution());
    idealNodeCalc.setClusterState(distributorBucketSpace.getClusterState());
    OperationTargetResolverImpl resolver(
            distributorBucketSpace, distributorBucketSpace.getBucketDatabase(), 16,
            distributorBucketSpace.getDistribution().getRedundancy(),
            makeBucketSpace());
    if (stripToRedundancy) {
        return resolver.getInstances(OperationTargetResolver::PUT, id);
    } else {
        return resolver.getAllInstances(OperationTargetResolver::PUT, id);
    }
}

/*
 * Test basic case with no inconsistencies
 */
TEST_F(OperationTargetResolverTest, simple) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0,1=0");

    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(16, 0), 1)
                                   .sendsTo(BucketId(16, 0), 0);
}

TEST_F(OperationTargetResolverTest, multiple_nodes) {
    setup_stripe(1, 2, "storage:2 distributor:1");

    auto &bucketSpaceRepo(operation_context().bucket_space_repo());
    auto &distributorBucketSpace(bucketSpaceRepo.get(makeBucketSpace()));
    for (int i = 0; i < 100; ++i) {
        addNodesToBucketDB(BucketId(16, i), "0=0,1=0");

        lib::IdealNodeCalculatorImpl idealNodeCalc;
        idealNodeCalc.setDistribution(distributorBucketSpace.getDistribution());
        idealNodeCalc.setClusterState(distributorBucketSpace.getClusterState());
        lib::IdealNodeList idealNodes(
                idealNodeCalc.getIdealStorageNodes(BucketId(16, i)));
        uint16_t expectedNode = idealNodes[0].getIndex();
        MY_ASSERT_THAT(BucketId(32, i)).sendsTo(BucketId(16, i), expectedNode);
    }
}

TEST_F(OperationTargetResolverTest, choose_ideal_state_when_many_copies) {
    setup_stripe(2, 4, "storage:4 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0,1=0,2=0,3=0"); // ideal nodes: 1, 3
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(16, 0), 1)
                                   .sendsTo(BucketId(16, 0), 3);
}

TEST_F(OperationTargetResolverTest, trusted_over_ideal_state) {
    setup_stripe(2, 4, "storage:4 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0/0/0/t,1=0,2=0/0/0/t,3=0");
    // ideal nodes: 1, 3
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(16, 0), 0)
                                   .sendsTo(BucketId(16, 0), 2);
}

TEST_F(OperationTargetResolverTest, choose_highest_split_bucket) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    // 0, 1 are both in ideal state for both buckets.
    addNodesToBucketDB(BucketId(16, 0), "0=0,1=0");
    addNodesToBucketDB(BucketId(17, 0), "0=0,1=0");
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(17, 0), 1)
                                   .sendsTo(BucketId(17, 0), 0);
}

TEST_F(OperationTargetResolverTest, choose_highest_split_bucket_per_node) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "1=0");
    addNodesToBucketDB(BucketId(17, 0), "0=0");
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(17, 0), 0)
                                   .sendsTo(BucketId(16, 0), 1);
}

TEST_F(OperationTargetResolverTest, choose_highest_split_bucket_with_trusted) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    // Unfinished split scenario: split done on 0, not on 1.
    // Copy on 1 is only remaining for (16, 0), so always trusted.
    addNodesToBucketDB(BucketId(16, 0), "1=1/2/3/t");
    addNodesToBucketDB(BucketId(17, 0), "0=2/3/4/t");
    addNodesToBucketDB(BucketId(17, 1ULL << 16), "0=3/4/5/t");
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(17, 0), 0)
                                   .sendsTo(BucketId(16, 0), 1);
}

TEST_F(OperationTargetResolverTest, inconsistent_buckets_are_not_explicitly_created) {
    setup_stripe(2, 2, "bits:8 storage:2 distributor:1");
    addNodesToBucketDB(BucketId(15, 0), "1=9/9/9/t");
    addNodesToBucketDB(BucketId(16, 1 << 15), "0=9/9/9/t");
    // (32, 0) belongs in (16, 0) subtree, but it does not exist. We cannot
    // create a bucket on (15, 0) node 0 since that will explicitly introduce
    // an inconsistent bucket in its local state. Note that we still _send_ to
    // the inconsistent (15, 0) bucket since it already exists and will be
    // split out very soon anyway. This is predominantly to avoid making things
    // even worse than they are and to avoid the edge case in bug 7296087.
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(15, 0), 1)
                                   .createsAt(BucketId(16, 0), 0);
}

TEST_F(OperationTargetResolverTest, no_trusted_or_ideal_state_copy_available) {
    setup_stripe(2, 4, "storage:4 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0,2=0");
    addNodesToBucketDB(BucketId(18, 0), "0=0"); // ideal nodes: 1, 3
    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(18, 0), 0)
                                   .sendsTo(BucketId(16, 0), 2);
}

TEST_F(OperationTargetResolverTest, create_missing_copies) {
    setup_stripe(4, 10, "storage:10 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "6=0");
    addNodesToBucketDB(BucketId(18, 0), "4=0"); // ideal nodes: 6, 8, 7, 1

    MY_ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(18, 0), 4)
                                   .sendsTo(BucketId(16, 0), 6)
                                   .createsAt(BucketId(18, 0), 8)
                                   .createsAt(BucketId(18, 0), 7);
}

TEST_F(OperationTargetResolverTest, no_existing_copies) {
    setup_stripe(2, 5, "storage:5 distributor:1");

    MY_ASSERT_THAT(BucketId(32, 0)).createsAt(BucketId(16, 0), 1)
                                   .createsAt(BucketId(16, 0), 3);
}

TEST_F(OperationTargetResolverTest, count_maintenance_nodes_as_down) {
    setup_stripe(2, 5, "storage:5 .1.s:m distributor:1");

    MY_ASSERT_THAT(BucketId(32, 0)).createsAt(BucketId(16, 0), 3)
                                   .createsAt(BucketId(16, 0), 2);
}

TEST_F(OperationTargetResolverTest, resolving_does_not_mutate_database) {
    setup_stripe(2, 5, "storage:5 distributor:1");

    MY_ASSERT_THAT(BucketId(32, 0)).createsAt(BucketId(16, 0), 1)
                                   .createsAt(BucketId(16, 0), 3);

    EXPECT_EQ("NONEXISTING", dumpBucket(BucketId(0x4000000000000000)));
}

} // storage::distributor
