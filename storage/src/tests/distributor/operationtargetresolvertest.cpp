// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/operationtargetresolverimpl.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/config/helper/configgetter.hpp>

using document::BucketId;
using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

struct OperationTargetResolverTest : public CppUnit::TestFixture,
                                     public DistributorTestUtil
{

    document::DocumentTypeRepo::SP _repo;
    const document::DocumentType* _html_type;
    std::unique_ptr<Operation> op;

    void testSimple();
    void testMultipleNodes();
    void testChooseIdealStateWhenManyCopies();
    void testChooseHighestSplitBucket();
    void testChooseHighestSplitBucketPerNode();
    void testChooseHighestSplitBucketWithTrusted();
    void testInconsistentBucketsAreNotExplicitlyCreated();
    void testNoTrustedOrIdealStateCopyAvailable();
    void testCreateMissingCopies();
    void testNoExistingCopies();
    void testCountMaintenanceNodesAsDown();
    void testResolvingDoesNotMutateDatabase();
    void testTrustedOverIdealState();

    BucketInstanceList getInstances(const BucketId& bid,
                                    bool stripToRedundancy);

    void setUp() override {
        _repo.reset(new document::DocumentTypeRepo(
                *config::ConfigGetter<document::DocumenttypesConfig>::getConfig(
                    "config-doctypes",
                    config::FileSpec(TEST_PATH("config-doctypes.cfg")))));
        _html_type = _repo->getDocumentType("text/html");
        createLinks();
    };

    void tearDown() override {
        close();
    }

    CPPUNIT_TEST_SUITE(OperationTargetResolverTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testMultipleNodes);
    CPPUNIT_TEST(testChooseIdealStateWhenManyCopies);
    CPPUNIT_TEST(testChooseHighestSplitBucket);
    CPPUNIT_TEST(testChooseHighestSplitBucketPerNode);
    CPPUNIT_TEST(testChooseHighestSplitBucketWithTrusted);
    CPPUNIT_TEST(testNoTrustedOrIdealStateCopyAvailable);
    CPPUNIT_TEST(testInconsistentBucketsAreNotExplicitlyCreated);
    CPPUNIT_TEST(testCreateMissingCopies);
    CPPUNIT_TEST(testNoExistingCopies);
    CPPUNIT_TEST(testCountMaintenanceNodesAsDown);
    CPPUNIT_TEST(testResolvingDoesNotMutateDatabase);
    CPPUNIT_TEST(testTrustedOverIdealState);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(OperationTargetResolverTest);

namespace {

    // Create assertion that makes it easy to write tests, and report correct
    // line for problem at command line
#define ASSERT_THAT(id) \
    { \
        struct MyAsserter : public Asserter { \
            void assertEqualMsg(std::string t1, OperationTargetList t2, \
                                OperationTargetList t3) override { \
                CPPUNIT_ASSERT_EQUAL_MSG(t1, t2, t3); \
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
    auto &bucketSpaceRepo(getExternalOperationHandler().getBucketSpaceRepo());
    auto &distributorBucketSpace(bucketSpaceRepo.get(makeBucketSpace()));
    idealNodeCalc.setDistribution(distributorBucketSpace.getDistribution());
    idealNodeCalc.setClusterState(distributorBucketSpace.getClusterState());
    OperationTargetResolverImpl resolver(
            distributorBucketSpace.getBucketDatabase(), idealNodeCalc, 16,
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
void
OperationTargetResolverTest::testSimple()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0,1=0");
    
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(16, 0), 1)
                                .sendsTo(BucketId(16, 0), 0);
}

void
OperationTargetResolverTest::testMultipleNodes()
{
    setupDistributor(1, 2, "storage:2 distributor:1");

    auto &bucketSpaceRepo(getExternalOperationHandler().getBucketSpaceRepo());
    auto &distributorBucketSpace(bucketSpaceRepo.get(makeBucketSpace()));
    for (int i = 0; i < 100; ++i) {
        addNodesToBucketDB(BucketId(16, i), "0=0,1=0");

        lib::IdealNodeCalculatorImpl idealNodeCalc;
        idealNodeCalc.setDistribution(distributorBucketSpace.getDistribution());
        idealNodeCalc.setClusterState(distributorBucketSpace.getClusterState());
        lib::IdealNodeList idealNodes(
                idealNodeCalc.getIdealStorageNodes(BucketId(16, i)));
        uint16_t expectedNode = idealNodes[0].getIndex();
        ASSERT_THAT(BucketId(32, i)).sendsTo(BucketId(16, i), expectedNode);
    }
}

void
OperationTargetResolverTest::testChooseIdealStateWhenManyCopies()
{
    setupDistributor(2, 4, "storage:4 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0,1=0,2=0,3=0"); // ideal nodes: 1, 3
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(16, 0), 1)
                                .sendsTo(BucketId(16, 0), 3);
}

void
OperationTargetResolverTest::testTrustedOverIdealState()
{
    setupDistributor(2, 4, "storage:4 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0/0/0/t,1=0,2=0/0/0/t,3=0");
        // ideal nodes: 1, 3
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(16, 0), 0)
                                .sendsTo(BucketId(16, 0), 2);
}

void
OperationTargetResolverTest::testChooseHighestSplitBucket()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    // 0, 1 are both in ideal state for both buckets.
    addNodesToBucketDB(BucketId(16, 0), "0=0,1=0");
    addNodesToBucketDB(BucketId(17, 0), "0=0,1=0");
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(17, 0), 1)
                                .sendsTo(BucketId(17, 0), 0);
}

void
OperationTargetResolverTest::testChooseHighestSplitBucketPerNode()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "1=0");
    addNodesToBucketDB(BucketId(17, 0), "0=0");
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(17, 0), 0)
                                .sendsTo(BucketId(16, 0), 1);
}

void
OperationTargetResolverTest::testChooseHighestSplitBucketWithTrusted()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    // Unfinished split scenario: split done on 0, not on 1.
    // Copy on 1 is only remaining for (16, 0), so always trusted.
    addNodesToBucketDB(BucketId(16, 0), "1=1/2/3/t");
    addNodesToBucketDB(BucketId(17, 0), "0=2/3/4/t");
    addNodesToBucketDB(BucketId(17, 1ULL << 16), "0=3/4/5/t");
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(17, 0), 0)
                                .sendsTo(BucketId(16, 0), 1);
}

void
OperationTargetResolverTest::testInconsistentBucketsAreNotExplicitlyCreated()
{
    setupDistributor(2, 2, "bits:8 storage:2 distributor:1");
    addNodesToBucketDB(BucketId(15, 0), "1=9/9/9/t");
    addNodesToBucketDB(BucketId(16, 1 << 15), "0=9/9/9/t");
    // (32, 0) belongs in (16, 0) subtree, but it does not exist. We cannot
    // create a bucket on (15, 0) node 0 since that will explicitly introduce
    // an inconsistent bucket in its local state. Note that we still _send_ to
    // the inconsistent (15, 0) bucket since it already exists and will be
    // split out very soon anyway. This is predominantly to avoid making things
    // even worse than they are and to avoid the edge case in bug 7296087.
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(15, 0), 1)
                                .createsAt(BucketId(16, 0), 0);
}

void
OperationTargetResolverTest::testNoTrustedOrIdealStateCopyAvailable()
{
    setupDistributor(2, 4, "storage:4 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "0=0,2=0");
    addNodesToBucketDB(BucketId(18, 0), "0=0"); // ideal nodes: 1, 3
    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(18, 0), 0)
                                .sendsTo(BucketId(16, 0), 2);
}

void
OperationTargetResolverTest::testCreateMissingCopies()
{
    setupDistributor(4, 10, "storage:10 distributor:1");
    addNodesToBucketDB(BucketId(16, 0), "6=0");
    addNodesToBucketDB(BucketId(18, 0), "4=0"); // ideal nodes: 6, 8, 7, 1

    ASSERT_THAT(BucketId(32, 0)).sendsTo(BucketId(18, 0), 4)
                                .sendsTo(BucketId(16, 0), 6)
                                .createsAt(BucketId(18, 0), 8)
                                .createsAt(BucketId(18, 0), 7);
}

void
OperationTargetResolverTest::testNoExistingCopies()
{
    setupDistributor(2, 5, "storage:5 distributor:1");

    ASSERT_THAT(BucketId(32, 0)).createsAt(BucketId(16, 0), 1)
                                .createsAt(BucketId(16, 0), 3);
}

void
OperationTargetResolverTest::testCountMaintenanceNodesAsDown()
{
    setupDistributor(2, 5, "storage:5 .1.s:m distributor:1");

    ASSERT_THAT(BucketId(32, 0)).createsAt(BucketId(16, 0), 3)
                                .createsAt(BucketId(16, 0), 2);
}

void
OperationTargetResolverTest::testResolvingDoesNotMutateDatabase()
{
    setupDistributor(2, 5, "storage:5 distributor:1");

    ASSERT_THAT(BucketId(32, 0)).createsAt(BucketId(16, 0), 1)
                                .createsAt(BucketId(16, 0), 3);

    CPPUNIT_ASSERT_EQUAL(std::string("NONEXISTING"),
                         dumpBucket(BucketId(0x4000000000000000)));
}

} // distributor
} // storage
