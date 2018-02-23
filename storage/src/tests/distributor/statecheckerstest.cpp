// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storage/distributor/statecheckers.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vdstestlib/cppunit/macros.h>

using namespace std::literals::string_literals;
using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;

namespace storage::distributor {

struct StateCheckersTest : public CppUnit::TestFixture,
                           public DistributorTestUtil
{
    StateCheckersTest() {}

    void setUp() override {
        createLinks();
    }

    void tearDown() override {
        close();
    }

    struct PendingMessage
    {
        uint32_t _msgType;
        uint8_t _pri;

        PendingMessage() : _msgType(UINT32_MAX), _pri(0) {}

        PendingMessage(uint32_t msgType, uint8_t pri)
            : _msgType(msgType), _pri(pri) {}

        bool shouldCheck() const { return _msgType != UINT32_MAX; }
    };

    void testSplit();
    void testInconsistentSplit();
    void splitCanBeScheduledWhenReplicasOnRetiredNodes();
    void testSynchronizeAndMove();
    void testDoNotMergeInconsistentlySplitBuckets();
    void doNotMoveReplicasWithinRetiredNodes();
    void testDeleteExtraCopies();
    void testDoNotDeleteActiveExtraCopies();
    void testConsistentCopiesOnRetiredNodesMayBeDeleted();
    void redundantCopyDeletedEvenWhenAllNodesRetired();
    void testJoin();
    void testDoNotJoinBelowClusterStateBitCount();
    void testAllowInconsistentJoinInDifferingSiblingIdealState();
    void testDoNotAllowInconsistentJoinWhenNotInIdealState();
    void testDoNotAllowInconsistentJoinWhenConfigDisabled();
    void testNoJoinWhenInvalidCopyExists();
    void testNoJoinOnDifferentNodes();
    void testNoJoinWhenCopyCountAboveRedundancyLevelsForLeftSibling();
    void testNoJoinWhenCopyCountAboveRedundancyLevelsForRightSibling();
    void testNoJoinWhenCopyCountAboveRedundancyLevelsForBothSiblings();
    void joinCanBeScheduledWhenReplicasOnRetiredNodes();
    void testBucketState();
    void testDoNotActivateNonReadyCopiesWhenIdealNodeInMaintenance();
    void testDoNotChangeActiveStateForInconsistentlySplitBuckets();
    void testNoActiveChangeForNonIdealCopiesWhenOtherwiseIdentical();
    void testBucketStatePerGroup();
    void allowActivationOfRetiredNodes();
    void inhibitBucketActivationIfDisabledInConfig();
    void inhibitBucketDeactivationIfDisabledInConfig();
    void retiredNodesOutOfSyncAreMerged();
    void testGarbageCollection();
    void gc_ops_are_prioritized_with_low_priority_category();
    void gcInhibitedWhenIdealNodeInMaintenance();
    void testNoRemoveWhenIdealNodeInMaintenance();
    void testStepwiseJoinForSmallBucketsWithoutSiblings();
    void testNoStepwiseJoinWhenDisabledThroughConfig();
    void testNoStepwiseJoinWhenSingleSiblingTooLarge();
    void testStepwiseJoinMaySkipMultipleBitsWhenConsistent();
    void testStepwiseJoinDoesNotSkipBeyondLevelWithSibling();
    void contextPopulatesIdealStateContainers();
    void statsUpdatedWhenMergingDueToMove();
    void statsUpdatedWhenMergingDueToMissingCopy();
    void statsUpdatedWhenMergingDueToOutOfSyncCopies();

    void enableClusterState(const lib::ClusterState& systemState) {
        _distributor->enableClusterState(systemState);
    }

    void insertJoinableBuckets();

    void assertCurrentIdealState(const document::BucketId& bucket,
                                 const std::vector<uint16_t> expected)
    {
        auto &distributorBucketSpace(getIdealStateManager().getBucketSpaceRepo().get(makeBucketSpace()));
        std::vector<uint16_t> idealNodes(
                distributorBucketSpace
                .getDistribution().getIdealStorageNodes(
                        getIdealStateManager().getDistributorComponent()
                                .getClusterState(),
                        bucket,
                        "ui"));
        CPPUNIT_ASSERT_EQUAL(expected, idealNodes);
    }

    void enableInconsistentJoinInConfig(bool enabled);

    std::string testStateChecker(
            StateChecker& checker,
            StateChecker::Context& c,
            bool includeBucketId = false,
            const PendingMessage& blocker = PendingMessage(),
            bool includeMessagePriority = false,
            bool includeSchedulingPriority = false)
    {
        std::ostringstream ost;

        c.siblingBucket = getIdealStateManager().getDistributorComponent()
                          .getSibling(c.getBucketId());

        std::vector<BucketDatabase::Entry> entries;
        getBucketDatabase().getAll(c.getBucketId(), entries);
        c.siblingEntry = getBucketDatabase().get(c.siblingBucket);

        c.entries = entries;
        for (uint32_t j = 0; j < entries.size(); ++j) {
            // Run checking only on this bucketid, but include all buckets
            // owned by it or owners of it, so we can detect inconsistent split.
            if (entries[j].getBucketId() == c.getBucketId()) {
                c.entry = entries[j];

                StateChecker::Result result(checker.check(c));
                IdealStateOperation::UP op(result.createOperation());
                if (op.get()) {
                    if (blocker.shouldCheck()
                        && op->shouldBlockThisOperation(blocker._msgType,
                                                        blocker._pri))
                    {
                        return "BLOCKED";
                    }

                    if (!ost.str().empty()) {
                        ost << ",";
                    }
                    if (includeBucketId) {
                        ost << op->getBucketId() << ": ";
                    }

                    ost << op->getDetailedReason();
                    if (includeMessagePriority) {
                        ost << " (pri "
                            << static_cast<int>(op->getPriority())
                            << ')';
                    }
                    if (includeSchedulingPriority) {
                        ost << " (scheduling pri "
                            << MaintenancePriority::toString(
                                result.getPriority().getPriority())
                            << ")";
                    }
                }
            }
        }

        if (ost.str().empty()) {
            ost << "NO OPERATIONS GENERATED";
        }

        getBucketDatabase().clear();

        return ost.str();
    }

    std::string testGarbageCollection(uint32_t prevTimestamp,
                                      uint32_t nowTimestamp,
                                      uint32_t checkInterval,
                                      uint32_t lastChangeTime = 0,
                                      bool includePriority = false,
                                      bool includeSchedulingPri = false);

    std::string testSplit(uint32_t splitCount,
                          uint32_t splitSize,
                          uint32_t minSplitBits,
                          const std::string& bucketInfo,
                          const PendingMessage& blocker = PendingMessage(),
                          bool includePriority = false);

    std::string testInconsistentSplit(const document::BucketId& bid,
                                      bool includePriority = false);

    std::string testJoin(uint32_t joinCount,
                         uint32_t joinSize,
                         uint32_t minSplitBits,
                         const document::BucketId& bid,
                         const PendingMessage& blocker = PendingMessage(),
                         bool includePriority = false);

    struct CheckerParams {
        std::string _bucketInfo;
        std::string _clusterState {"distributor:1 storage:2"};
        std::string _expect;
        static const PendingMessage NO_OP_BLOCKER;
        const PendingMessage* _blockerMessage {&NO_OP_BLOCKER};
        uint32_t _redundancy {2};
        uint32_t _splitCount {0};
        uint32_t _splitSize {0};
        uint32_t _minSplitBits {0};
        bool _includeMessagePriority {false};
        bool _includeSchedulingPriority {false};
        CheckerParams();
        ~CheckerParams();

        CheckerParams& expect(const std::string& e) {
            _expect = e;
            return *this;
        }
        CheckerParams& bucketInfo(const std::string& info) {
            _bucketInfo = info;
            return *this;
        }
        CheckerParams& clusterState(const std::string& state) {
            _clusterState = state;
            return *this;
        }
        CheckerParams& blockerMessage(const PendingMessage& blocker) {
            _blockerMessage = &blocker;
            return *this;
        }
        CheckerParams& redundancy(uint32_t r) {
            _redundancy = r;
            return *this;
        }
        CheckerParams& includeMessagePriority(bool includePri) {
            _includeMessagePriority = includePri;
            return *this;
        }
        CheckerParams& includeSchedulingPriority(bool includePri) {
            _includeSchedulingPriority = includePri;
            return *this;
        }
    };

    template <typename CheckerImpl>
    void runAndVerify(const CheckerParams& params) {
        CheckerImpl checker;

        document::BucketId bid(17, 0);
        addNodesToBucketDB(bid, params._bucketInfo);
        setRedundancy(params._redundancy);
        _distributor->enableClusterState(
                lib::ClusterState(params._clusterState));
        NodeMaintenanceStatsTracker statsTracker;
        StateChecker::Context c(
                getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
        std::string result =  testStateChecker(
                checker, c, false, *params._blockerMessage,
                params._includeMessagePriority,
                params._includeSchedulingPriority);
        CPPUNIT_ASSERT_EQUAL(params._expect, result);
    }

    std::string testSynchronizeAndMove(
            const std::string& bucketInfo,
            const std::string& clusterState = "distributor:1 storage:2",
            uint32_t redundancy = 2,
            const PendingMessage& blocker = PendingMessage(),
            bool includePriority = false);

    std::string testDeleteExtraCopies(
        const std::string& bucketInfo,
        uint32_t redundancy = 2,
        const PendingMessage& blocker = PendingMessage(),
        const std::string& clusterState = "",
        bool includePriority = false);

    std::string testBucketState(const std::string& bucketInfo,
                                uint32_t redundancy = 2,
                                bool includePriority = false);
    std::string testBucketStatePerGroup(const std::string& bucketInfo,
                                        bool includePriority = false);

    CPPUNIT_TEST_SUITE(StateCheckersTest);
    CPPUNIT_TEST(testSplit);
    CPPUNIT_TEST(testInconsistentSplit);
    CPPUNIT_TEST(splitCanBeScheduledWhenReplicasOnRetiredNodes);
    CPPUNIT_TEST(testSynchronizeAndMove);
    CPPUNIT_TEST(testDoNotMergeInconsistentlySplitBuckets);
    CPPUNIT_TEST(doNotMoveReplicasWithinRetiredNodes);
    CPPUNIT_TEST(retiredNodesOutOfSyncAreMerged);
    CPPUNIT_TEST(testDoNotChangeActiveStateForInconsistentlySplitBuckets);
    CPPUNIT_TEST(testDeleteExtraCopies);
    CPPUNIT_TEST(testDoNotDeleteActiveExtraCopies);
    CPPUNIT_TEST(testConsistentCopiesOnRetiredNodesMayBeDeleted);
    CPPUNIT_TEST(redundantCopyDeletedEvenWhenAllNodesRetired);
    CPPUNIT_TEST(testJoin);
    CPPUNIT_TEST(testDoNotJoinBelowClusterStateBitCount);
    CPPUNIT_TEST(testAllowInconsistentJoinInDifferingSiblingIdealState);
    CPPUNIT_TEST(testDoNotAllowInconsistentJoinWhenNotInIdealState);
    CPPUNIT_TEST(testDoNotAllowInconsistentJoinWhenConfigDisabled);
    CPPUNIT_TEST(testNoJoinWhenInvalidCopyExists);
    CPPUNIT_TEST(testNoJoinOnDifferentNodes);
    CPPUNIT_TEST(testNoJoinWhenCopyCountAboveRedundancyLevelsForLeftSibling);
    CPPUNIT_TEST(testNoJoinWhenCopyCountAboveRedundancyLevelsForRightSibling);
    CPPUNIT_TEST(testNoJoinWhenCopyCountAboveRedundancyLevelsForBothSiblings);
    CPPUNIT_TEST(joinCanBeScheduledWhenReplicasOnRetiredNodes);
    CPPUNIT_TEST(testBucketState);
    CPPUNIT_TEST(testDoNotActivateNonReadyCopiesWhenIdealNodeInMaintenance);
    CPPUNIT_TEST(testNoActiveChangeForNonIdealCopiesWhenOtherwiseIdentical);
    CPPUNIT_TEST(testBucketStatePerGroup);
    CPPUNIT_TEST(allowActivationOfRetiredNodes);
    CPPUNIT_TEST(inhibitBucketActivationIfDisabledInConfig);
    CPPUNIT_TEST(inhibitBucketDeactivationIfDisabledInConfig);
    CPPUNIT_TEST(testGarbageCollection);
    CPPUNIT_TEST(gc_ops_are_prioritized_with_low_priority_category);
    CPPUNIT_TEST(gcInhibitedWhenIdealNodeInMaintenance);
    CPPUNIT_TEST(testNoRemoveWhenIdealNodeInMaintenance);
    CPPUNIT_TEST(testStepwiseJoinForSmallBucketsWithoutSiblings);
    CPPUNIT_TEST(testNoStepwiseJoinWhenDisabledThroughConfig);
    CPPUNIT_TEST(testNoStepwiseJoinWhenSingleSiblingTooLarge);
    CPPUNIT_TEST(testStepwiseJoinMaySkipMultipleBitsWhenConsistent);
    CPPUNIT_TEST(testStepwiseJoinDoesNotSkipBeyondLevelWithSibling);
    CPPUNIT_TEST(contextPopulatesIdealStateContainers);
    CPPUNIT_TEST(statsUpdatedWhenMergingDueToMove);
    CPPUNIT_TEST(statsUpdatedWhenMergingDueToMissingCopy);
    CPPUNIT_TEST(statsUpdatedWhenMergingDueToOutOfSyncCopies);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StateCheckersTest);


StateCheckersTest::CheckerParams::CheckerParams() {}
StateCheckersTest::CheckerParams::~CheckerParams() {}


const StateCheckersTest::PendingMessage
StateCheckersTest::CheckerParams::NO_OP_BLOCKER;

std::string StateCheckersTest::testSplit(uint32_t splitCount,
                                         uint32_t splitSize,
                                         uint32_t minSplitBits,
                                         const std::string& bucketInfo,
                                         const PendingMessage& blocker,
                                         bool includePriority)
{
    document::BucketId bid(17, 0);

    addNodesToBucketDB(bid, bucketInfo);

    SplitBucketStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    getConfig().setSplitSize(splitSize);
    getConfig().setSplitCount(splitCount);
    getConfig().setMinimalBucketSplit(minSplitBits);
    return testStateChecker(checker, c, false, blocker, includePriority);
}



void
StateCheckersTest::testSplit()
{
    setupDistributor(3, 10, "distributor:1 storage:2");

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (2000 b, 10 docs, 10 meta, 2000 b total) "
                        "is higher than the configured limit of (1000, 4294967295)]"),
            testSplit((uint32_t)-1, 1000, 16, "0=100/10/2000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, "
                        "200 docs, 200 meta, 1000 b total) "
                        "is higher than the configured limit of (10000, 100)] "
                        "(pri 175)"),
            testSplit(100, 10000, 16, "0=100/200/1000", PendingMessage(), true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testSplit(1000, 1000, 16, "0=100/200/200"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testSplit(1000, 1000, 16, "0=100/200/200/2000/2000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because the current system size requires "
                        "a higher minimum split bit]"),
            testSplit((uint32_t)-1, (uint32_t)-1, 24, "0=100/200/1000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
                        "is higher than the configured limit of (10000, 100)]"),
            testSplit(100, 10000, 16, "0=100/10/10,1=100/1000/1000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
                        "is higher than the configured limit of (10000, 100)]"),
            testSplit(100, 10000, 16, "0=1/0/0,1=100/1000/1000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
                        "is higher than the configured limit of (10000, 100)]"),
            testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testSplit(1000, 1000, 16, "0=100/1/200000"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BLOCKED"),
            testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000",
                      PendingMessage(api::MessageType::SPLITBUCKET_ID, 0)));

        // Split on too high meta
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, 100 docs, 2100 meta, 15000000 b total) "
                        "is higher than the configured limit of (10000000, 1000)]"),
            testSplit(1000, 10000000, 16, "0=14/100/1000/2100/15000000"));
        // Split on too high file size
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, 100 docs, 1500 meta, 21000000 b total) "
                        "is higher than the configured limit of (10000000, 1000)]"),
            testSplit(1000, 10000000, 16, "0=14/100/1000/1500/21000000"));

    // Don't block higher priority splits than what's already pending.
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
                        "is higher than the configured limit of (10000, 100)]"),
            testSplit(100, 10000, 16, "0=100/10/10,1=100/1000/1000",
                      PendingMessage(api::MessageType::SPLITBUCKET_ID, 255)));

    // But must block equal priority splits that are already pending, or
    // we'll end up spamming the nodes with splits!
    // NOTE: assuming split priority of 175.
    CPPUNIT_ASSERT_EQUAL(
            std::string("BLOCKED"),
            testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000",
                      PendingMessage(api::MessageType::SPLITBUCKET_ID, 175)));

    // Don't split if we're already joining, since there's a window of time
    // where the bucket will appear to be inconsistently split when the join
    // is not finished on all the nodes.
    CPPUNIT_ASSERT_EQUAL(
            std::string("BLOCKED"),
            testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000",
                      PendingMessage(api::MessageType::JOINBUCKETS_ID, 175)));
}

std::string
StateCheckersTest::testInconsistentSplit(const document::BucketId& bid,
                                         bool includePriority)
{
    SplitInconsistentStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, true,
                            PendingMessage(), includePriority);
}

void
StateCheckersTest::testInconsistentSplit()
{
    setupDistributor(3, 10, "distributor:1 storage:2");

    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testInconsistentSplit(document::BucketId(16, 1)));

    insertBucketInfo(document::BucketId(17, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001): [Bucket is inconsistently "
                        "split (list includes 0x4000000000000001, 0x4400000000000001) "
                        "Splitting it to improve the problem (max used bits 17)]"),
            testInconsistentSplit(document::BucketId(16, 1)));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testInconsistentSplit(document::BucketId(17, 1)));

    insertBucketInfo(document::BucketId(17, 1), 0, 0x0, 0, 0);
    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001): [Bucket is inconsistently "
                        "split (list includes 0x4000000000000001, 0x4400000000000001) "
                        "Splitting it to improve the problem (max used bits "
                        "17)] (pri 110)"),
            testInconsistentSplit(document::BucketId(16, 1), true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testInconsistentSplit(document::BucketId(17, 1)));
}

void
StateCheckersTest::splitCanBeScheduledWhenReplicasOnRetiredNodes()
{
    setupDistributor(Redundancy(2), NodeCount(2),
                     "distributor:1 storage:2, .0.s:r .1.s:r");
    CPPUNIT_ASSERT_EQUAL(
            "[Splitting bucket because its maximum size (2000 b, 10 docs, "
            "10 meta, 2000 b total) is higher than the configured limit of "
            "(1000, 4294967295)]"s,
            testSplit(UINT32_MAX, 1000, 16, "0=100/10/2000"));
}

std::string
StateCheckersTest::testJoin(uint32_t joinCount,
                            uint32_t joinSize,
                            uint32_t minSplitBits,
                            const document::BucketId& bid,
                            const PendingMessage& blocker,
                            bool includePriority)
{
    JoinBucketsStateChecker checker;
    getConfig().setJoinSize(joinSize);
    getConfig().setJoinCount(joinCount);
    getConfig().setMinimalBucketSplit(minSplitBits);

    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, true, blocker, includePriority);
}

void
StateCheckersTest::insertJoinableBuckets()
{
    insertBucketInfo(document::BucketId(33, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
}

void
StateCheckersTest::testJoin()
{
    setupDistributor(3, 10, "distributor:1 storage:2");

    insertJoinableBuckets();
    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x8000000000000001): "
            "[Joining buckets BucketId(0x8400000000000001) and "
            "BucketId(0x8400000100000001) because their size "
            "(2 bytes, 2 docs) is less than the configured limit "
            "of (100, 10)"),
            testJoin(10, 100, 16, document::BucketId(33, 1)));

    insertJoinableBuckets();
    // Join size is 0, so only look at document count
    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x8000000000000001): "
            "[Joining buckets BucketId(0x8400000000000001) and "
            "BucketId(0x8400000100000001) because their size "
            "(2 bytes, 2 docs) is less than the configured limit "
            "of (0, 3) (pri 155)"),
            testJoin(3, 0, 16, document::BucketId(33, 1), PendingMessage(), true));

    insertJoinableBuckets();
    // Should not generate joins for both pairs, just the primary
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 16, document::BucketId(33, 0x100000001)));

    insertJoinableBuckets();
    // Should not generate join if min split bits is higher
    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
                         testJoin(10, 100, 33, document::BucketId(33, 1)));

    insertJoinableBuckets();
    // Meta data too big, no join
    insertBucketInfo(document::BucketId(33, 1), 1,
                     api::BucketInfo(0x1, 1, 1, 1000, 1000));

    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
                         testJoin(10, 100, 16, document::BucketId(33, 1)));

    insertJoinableBuckets();
    // Bucket recently created
    insertBucketInfo(document::BucketId(33, 1), 1,
                     api::BucketInfo(0x1, 0, 0, 0, 0));
    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
                         testJoin(10, 100, 16, document::BucketId(33, 1)));

}

/**
 * If distributor config says minsplitcount is 8, but cluster state says that
 * distribution bit count is 16, we should not allow the join to take place.
 * We don't properly handle the "reduce distribution bits" case in general, so
 * the safest is to never violate this and to effectively make distribution
 * bit increases a one-way street.
 */
void
StateCheckersTest::testDoNotJoinBelowClusterStateBitCount()
{
    setupDistributor(2, 2, "bits:16 distributor:1 storage:2");
    // Insert sibling buckets at 16 bits that are small enough to be joined
    // unless there is special logic for dealing with distribution bits.
    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(16, (1 << 15) | 1), 1, 0x1, 1, 1);
    using ConfiguredMinSplitBits = uint32_t;
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testJoin(100, 100, ConfiguredMinSplitBits(8),
                     document::BucketId(16, 1)));
}

void
StateCheckersTest::enableInconsistentJoinInConfig(bool enabled)
{
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableInconsistentJoin = enabled;
    getConfig().configure(config);
}

void
StateCheckersTest::testAllowInconsistentJoinInDifferingSiblingIdealState()
{
    // Normally, bucket siblings have an ideal state on the same node in order
    // to enable joining these back together. However, the ideal disks assigned
    // may differ and it's sufficient for a sibling bucket's ideal disk to be
    // down on the node of its other sibling for it to be assigned a different
    // node. In this case, there's no other way to get buckets joined back
    // together than if we allow bucket replicas to get temporarily out of sync
    // by _forcing_ a join across all replicas no matter their placement.
    // This will trigger a merge to reconcile and move the new bucket copies to
    // their ideal location.
    setupDistributor(2, 3, "distributor:1 storage:3 .0.d:20 .0.d.14.s:d .2.d:20");
    document::BucketId sibling1(33, 0x000000001); // ideal disk 14 on node 0
    document::BucketId sibling2(33, 0x100000001); // ideal disk 1 on node 0

    // Full node sequence sorted by score for sibling(1|2) is [0, 2, 1].
    // Node 0 cannot be used, so use 1 instead.
    assertCurrentIdealState(sibling1, {2, 1});
    assertCurrentIdealState(sibling2, {0, 2});

    insertBucketInfo(sibling1, 2, 0x1, 2, 3);
    insertBucketInfo(sibling1, 1, 0x1, 2, 3);
    insertBucketInfo(sibling2, 0, 0x1, 2, 3);
    insertBucketInfo(sibling2, 2, 0x1, 2, 3);

    enableInconsistentJoinInConfig(true);

    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x8000000000000001): "
            "[Joining buckets BucketId(0x8400000000000001) and "
            "BucketId(0x8400000100000001) because their size "
            "(6 bytes, 4 docs) is less than the configured limit "
            "of (100, 10)"),
            testJoin(10, 100, 16, sibling1));
}

void
StateCheckersTest::testDoNotAllowInconsistentJoinWhenNotInIdealState()
{
    setupDistributor(2, 4, "distributor:1 storage:4 .0.d:20 .0.d.14.s:d .2.d:20 .3.d:20");
    document::BucketId sibling1(33, 0x000000001);
    document::BucketId sibling2(33, 0x100000001);

    assertCurrentIdealState(sibling1, {3, 2});
    assertCurrentIdealState(sibling2, {3, 0});

    insertBucketInfo(sibling1, 3, 0x1, 2, 3);
    insertBucketInfo(sibling1, 2, 0x1, 2, 3);
    insertBucketInfo(sibling2, 3, 0x1, 2, 3);
    insertBucketInfo(sibling2, 1, 0x1, 2, 3); // not in ideal state

    enableInconsistentJoinInConfig(true);

    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
                         testJoin(10, 100, 16, sibling1));
}

void
StateCheckersTest::testDoNotAllowInconsistentJoinWhenConfigDisabled()
{
    setupDistributor(2, 3, "distributor:1 storage:3 .0.d:20 .0.d.14.s:d .2.d:20");
    document::BucketId sibling1(33, 0x000000001); // ideal disk 14 on node 0
    document::BucketId sibling2(33, 0x100000001); // ideal disk 1 on node 0

    // Full node sequence sorted by score for sibling(1|2) is [0, 2, 1].
    // Node 0 cannot be used, so use 1 instead.
    assertCurrentIdealState(sibling1, {2, 1});
    assertCurrentIdealState(sibling2, {0, 2});

    insertBucketInfo(sibling1, 2, 0x1, 2, 3);
    insertBucketInfo(sibling1, 1, 0x1, 2, 3);
    insertBucketInfo(sibling2, 0, 0x1, 2, 3);
    insertBucketInfo(sibling2, 2, 0x1, 2, 3);

    enableInconsistentJoinInConfig(false);

    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
                         testJoin(10, 100, 16, sibling1));
}

void
StateCheckersTest::testNoJoinWhenInvalidCopyExists()
{
    setupDistributor(3, 10, "distributor:1 storage:3");

    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
    // No join when there exists an invalid copy
    insertBucketInfo(document::BucketId(33, 1), 1, api::BucketInfo());

    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
                         testJoin(10, 100, 16, document::BucketId(33, 1)));    
}

void
StateCheckersTest::testNoJoinOnDifferentNodes()
{
    setupDistributor(3, 10, "distributor:1 storage:2");

    insertBucketInfo(document::BucketId(33, 0x000000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

void
StateCheckersTest::testNoJoinWhenCopyCountAboveRedundancyLevelsForLeftSibling()
{
    setupDistributor(3, 10, "distributor:1 storage:2");
    setRedundancy(1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 0, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

void
StateCheckersTest::testNoJoinWhenCopyCountAboveRedundancyLevelsForRightSibling()
{
    setupDistributor(3, 10, "distributor:1 storage:2");
    setRedundancy(1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

void
StateCheckersTest::testNoJoinWhenCopyCountAboveRedundancyLevelsForBothSiblings()
{
    setupDistributor(3, 10, "distributor:1 storage:2");
    setRedundancy(1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

std::string
StateCheckersTest::testSynchronizeAndMove(const std::string& bucketInfo,
                                          const std::string& clusterState,
                                          uint32_t redundancy,
                                          const PendingMessage& blocker,
                                          bool includePriority)
{
    document::BucketId bid(17, 0);

    addNodesToBucketDB(bid, bucketInfo);

    SynchronizeAndMoveStateChecker checker;
    setRedundancy(redundancy);

    enableDistributorClusterState(clusterState);
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, blocker, includePriority);
}

void
StateCheckersTest::testSynchronizeAndMove()
{
    // Plus if it was more obvious which nodes were in ideal state for various
    // cluster states. (One possibility to override ideal state function for
    // test)
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams().expect(
                "[Synchronizing buckets with different checksums "
                "node(idx=0,crc=0x1,docs=1/1,bytes=1/1,trusted=false,"
                    "active=false,ready=false), "
                "node(idx=1,crc=0x2,docs=2/2,bytes=2/2,trusted=false,"
                    "active=false,ready=false)] "
                "(scheduling pri MEDIUM)")
            .bucketInfo("0=1,1=2")
            .includeSchedulingPriority(true));

    // If 1+ nodes in ideal state is in maintenance, do nothing
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED")
            .bucketInfo("0=1,2=2")
            .clusterState("distributor:1 storage:3 .1.s:m"));

    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Moving bucket to ideal node 3] "
                    "(scheduling pri LOW)")
            .bucketInfo("0=1,1=1,2=1")
            .clusterState("distributor:1 storage:4")
            .includeSchedulingPriority(true));

    // Not doing anything in ideal state
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED")
            .bucketInfo("0=1,1=1,3=1")
            .clusterState("distributor:1 storage:4"));

    // Both copies out of ideal state
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Moving bucket to ideal node 1]"
                    "[Moving bucket to ideal node 3] (pri 165) "
                    "(scheduling pri LOW)")
            .clusterState("distributor:1 storage:5")
            .bucketInfo("0=1,4=1,5=1")
            .includeMessagePriority(true)
            .includeSchedulingPriority(true));

    // Too little redundancy and out of ideal state. Note that in this case,
    // the non-ideal node is reported as a missing node and not with a "Moving
    // bucket to ideal node" reason.
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Adding missing node 1]"
                    "[Adding missing node 3] (pri 120) "
                    "(scheduling pri MEDIUM)")
            .bucketInfo("0=1")
            .clusterState("distributor:1 storage:5")
            .includeMessagePriority(true)
            .includeSchedulingPriority(true));

    // Synchronizing even when ideal state is in sync
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Synchronizing buckets with different checksums "
                    "node(idx=0,crc=0x3,docs=3/3,bytes=3/3,trusted=false,"
                        "active=false,ready=false), "
                    "node(idx=1,crc=0x3,docs=3/3,bytes=3/3,trusted=false,"
                        "active=false,ready=false), "
                    "node(idx=2,crc=0x0,docs=0/0,bytes=0/0,trusted=false,"
                        "active=false,ready=false)]")
            .bucketInfo("0=3,1=3,2=0")
            .clusterState("distributor:1 storage:3"));

    // Synchronize even when we have >= redundancy trusted copies and ideal
    // nodes are in sync.
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Synchronizing buckets with different checksums "
                    "node(idx=0,crc=0x2,docs=3/3,bytes=4/4,trusted=false,"
                        "active=false,ready=false), "
                    "node(idx=1,crc=0x1,docs=2/2,bytes=3/3,trusted=true,"
                        "active=false,ready=false), "
                    "node(idx=2,crc=0x1,docs=2/2,bytes=3/3,trusted=true,"
                        "active=false,ready=false), "
                    "node(idx=3,crc=0x1,docs=2/2,bytes=3/3,trusted=true,"
                        "active=false,ready=false)] "
                    "(pri 120) (scheduling pri MEDIUM)")
            .bucketInfo("0=2/3/4,1=1/2/3/t,2=1/2/3/t,3=1/2/3/t")
            .clusterState("distributor:1 storage:5")
            .includeMessagePriority(true)
            .includeSchedulingPriority(true));

    // Not doing anything if one of the buckets in ideal state is invalid
    // but we have redundancy coverage otherwise
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED")
            .bucketInfo("1=0/0/1,3=1")
            .clusterState("distributor:1 storage:4"));

    // Not doing anything if all copies we have are invalid
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED")
            .bucketInfo("1=0/0/1,3=0/0/1")
            .clusterState("distributor:1 storage:4"));

    // Not doing anything if we have < redundancy copies but all existing
    // copies are invalid.
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED")
            .bucketInfo("1=0/0/1")
            .clusterState("distributor:1 storage:4"));
}

void
StateCheckersTest::testDoNotMergeInconsistentlySplitBuckets()
{
    // No merge generated if buckets are inconsistently split.
    // This matches the case where a bucket has been split into 2 on one
    // node and is not yet split on another; we should never try to merge
    // either two of the split leaf buckets back onto the first node!
    // Running state checker on a leaf:
    addNodesToBucketDB(document::BucketId(16, 0), "0=2");
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testSynchronizeAndMove("1=1", // 17 bits
                                   "distributor:1 storage:4"));
    // Running state checker on an inner node bucket:
    addNodesToBucketDB(document::BucketId(18, 0), "0=2");
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testSynchronizeAndMove("0=1", // 17 bits
                                   "distributor:1 storage:4"));
}

void
StateCheckersTest::doNotMoveReplicasWithinRetiredNodes()
{
    // Nodes 1 and 3 would be in ideal state if the nodes were not retired.
    // Here, all nodes are retired and we should thus not do any sort of
    // moving.
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED")
            .bucketInfo("0=2,1=2")
            .clusterState("distributor:1 storage:4 "
                          ".0.s:r .1.s:r .2.s:r .3.s:r"));
}

void
StateCheckersTest::retiredNodesOutOfSyncAreMerged()
{
    // Normally, we'd do a merge that'd move the bucket to new nodes, leaving
    // the out of sync retired nodes as source-only replicas. But here we
    // don't have that choice and thus try to do the most useful thing we can
    // with what we have available to us (which is to try to get things in
    // sync).
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Synchronizing buckets with different checksums "
                    "node(idx=0,crc=0x1,docs=1/1,bytes=1/1,trusted=false,"
                        "active=false,ready=false), "
                    "node(idx=1,crc=0x2,docs=2/2,bytes=2/2,trusted=false,"
                        "active=false,ready=false)]")
            .bucketInfo("0=1,1=2")
            .clusterState("distributor:1 storage:4 "
                          ".0.s:r .1.s:r .2.s:r .3.s:r"));
}

std::string
StateCheckersTest::testDeleteExtraCopies(
        const std::string& bucketInfo, uint32_t redundancy,
        const PendingMessage& blocker,
        const std::string& clusterState,
        bool includePriority)
{
    document::BucketId bid(17, 0);

    addNodesToBucketDB(bid, bucketInfo);
    setRedundancy(redundancy);

    if (!clusterState.empty()) {
        enableDistributorClusterState(clusterState);
    }
    DeleteExtraCopiesStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, blocker, includePriority);
}


void
StateCheckersTest::testDeleteExtraCopies()
{
    setupDistributor(2, 100, "distributor:1 storage:4");

    {
        auto &distributorBucketSpace(getIdealStateManager().getBucketSpaceRepo().get(makeBucketSpace()));
        std::vector<uint16_t> idealNodes(
                distributorBucketSpace
                .getDistribution().getIdealStorageNodes(
                        getIdealStateManager().getDistributorComponent().getClusterState(),
                        document::BucketId(17, 0),
                        "ui"));
        std::vector<uint16_t> wanted;
        wanted.push_back(1);
        wanted.push_back(3);
        CPPUNIT_ASSERT_EQUAL(wanted, idealNodes);
    }

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Remove empty buckets",
            std::string("[Removing all copies since bucket is empty:node(idx=0,crc=0x0,"
                        "docs=0/0,bytes=0/0,trusted=false,active=false,ready=false)]"
                        " (pri 100)"),
            testDeleteExtraCopies("0=0", 2, PendingMessage(), "", true));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Remove extra trusted copy",
            std::string("[Removing redundant in-sync copy from node 2]"),
            testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Redundant copies in sync can be removed without trusted being a "
            "factor of consideration. Ideal state copy not removed.",
            std::string("[Removing redundant in-sync copy from node 2]"),
            testDeleteExtraCopies("3=3/3/3,1=3/3/3/t,2=3/3/3/t"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Need redundancy number of copies",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=3,1=3"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not remove extra copies without enough trusted copies",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=0/0/1,1=3,2=3"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not remove buckets that have meta entries",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=0/0/1,1=0/0/1"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not remove any recently created copies",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=1/0/0/t,1=1/0/0/t,2=1/0/0/t"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not remove untrusted copy that is out of sync",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=2/3/4,1=1/2/3/t,2=1/2/3/t"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not remove out of sync copies, even if we have more than #"
            "redundancy trusted copies",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=2/3/4,1=1/2/3/t,2=1/2/3/t,3=1/2/3/t"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Don't remove unless we have enough trusted "
            "copies to satisfy redundancy",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=2/3/4,1=1/2/3,2=2/3/4,3=1/2/3"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Only remove empty copies unless all other copies are in sync",
            std::string("[Removing empty copy from node 4]"),
            testDeleteExtraCopies("0=2/3/4,1=1/2/3,2=2/3/4,3=1/2/3,4=0/0/0"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Remove redundant empty copy",
            std::string("[Removing empty copy from node 0]"),
            testDeleteExtraCopies("1=2/3,3=1/2/3,0=0/0/0"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Remove empty bucket with multiple copies",
            std::string(
                    "[Removing all copies since bucket is empty:"
                    "node(idx=0,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false), "
                    "node(idx=1,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false), "
                    "node(idx=2,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false)]"),
            testDeleteExtraCopies("0=0/0/0,1=0/0/0,2=0/0/0"));

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Pending persistence operation blocks delete",
            std::string("BLOCKED"),
            testDeleteExtraCopies("0=0/0/0,1=1/2/3/t,2=1/2/3/t",
                                  2,
                                  PendingMessage(api::MessageType::PUT_ID, 255)));
}

void
StateCheckersTest::testDoNotDeleteActiveExtraCopies()
{
    setupDistributor(2, 100, "distributor:1 storage:4");

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not delete redundant copy if it is marked active",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t/a"));
}

void
StateCheckersTest::testConsistentCopiesOnRetiredNodesMayBeDeleted()
{
    setupDistributor(2, 100, "distributor:1 storage:4 .1.s:r");

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Remove in-sync copy on node that is retired",
            std::string("[Removing redundant in-sync copy from node 1]"),
            testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t"));
}

void
StateCheckersTest::redundantCopyDeletedEvenWhenAllNodesRetired()
{
    setupDistributor(2, 100, "distributor:1 storage:4 "
                     ".0.s:r .1.s:r .2.s:r .3.s:r");

    CPPUNIT_ASSERT_EQUAL_MSG(
            "Remove in-sync copy on node that is retired",
            "[Removing redundant in-sync copy from node 2]"s,
            testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t"));
}

std::string StateCheckersTest::testBucketState(
        const std::string& bucketInfo, uint32_t redundancy,
        bool includePriority)
{
    document::BucketId bid(17, 0);
    setRedundancy(redundancy);
    addNodesToBucketDB(bid, bucketInfo);

    BucketStateStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, PendingMessage(),
                            includePriority);
}

void
StateCheckersTest::testBucketState()
{
    setupDistributor(2, 100, "distributor:1 storage:4");

    {
        // Set config explicitly so we can compare priorities for differing
        // cases.
        DistributorConfiguration::MaintenancePriorities mp;
        mp.activateNoExistingActive = 90;
        mp.activateWithExistingActive = 120;
        getConfig().setMaintenancePriorities(mp);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState(""));

    // Node 1 is in ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 1 as active:"
                        " copy is ideal state priority 0] (pri 90)"),
            testBucketState("1=2/3/4", 2, true));

    // Node 3 is in ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 3 as active:"
                        " copy is ideal state priority 1]"),
            testBucketState("3=2/3/4"));

    // No trusted nodes, but node 1 is first in ideal state.
    // Also check bad case where more than 1 node is set as active just
    // to ensure we can get out of that situation if it should ever happen.
    // Nothing done with node 3 since is't not active and shouldn't be.
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 1 as active:"
                        " copy is ideal state priority 0]"
                        "[Setting node 0 as inactive]"
                        "[Setting node 2 as inactive] (pri 120)"),
            testBucketState("0=3/4/5/u/a,1=3,2=4/5/6/u/a,3=3", 2, true));

    // Test setting active when only node available is not contained
    // within the resolved ideal state.
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 0 as active: first available copy]"),
            testBucketState("0=2/3/4"));

    // A trusted ideal state copy should be set active rather than a non-trusted
    // ideal state copy
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 3 as active:"
                        " copy is trusted and ideal state priority 1]"
                        "[Setting node 1 as inactive]"),
            testBucketState("1=2/3/4/u/a,3=5/6/7/t"));

    // None of the ideal state copies are trusted but a non-ideal copy is.
    // The trusted copy should be active.
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 2 as active: copy is trusted]"),
            testBucketState("1=2/3/4,3=5/6/7/,2=8/9/10/t"));

    // Make sure bucket db ordering does not matter
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 2 as active: copy is trusted]"),
            testBucketState("2=8/9/10/t,1=2/3/4,3=5/6/7"));

    // If copy is already active, we shouldn't generate operations
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=2/3/4/t/a"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=2/3/4,3=5/6/7/t/a"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("2=8/9/10/t/a,1=2/3/4,3=5/6/7"));

    // If multiple buckets are active, deactive all but one
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 2 as inactive]"
                        "[Setting node 3 as inactive]"),
            testBucketState("1=1/2/3/t/a,2=1/2/3/t/a,3=1/2/3/t/a"));

    // Invalid buckets should not be included
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=0/0/1,3=0/0/1"));

    // Ready preferred over trusted & ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("2=8/9/10/t/i/u,1=2/3/4/u/a/r,3=5/6/7"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 2 as active: copy is ready]"
                        "[Setting node 1 as inactive]"),
            testBucketState("2=8/9/10/u/i/r,1=2/3/4/u/a/u,3=5/6/7/u/i/u"));

    // Prefer in ideal state if multiple copies ready
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 3 as active: copy is ready]"
                        "[Setting node 1 as inactive]"),
            testBucketState("2=8/9/10/u/i/r,1=2/3/4/u/a/u,3=5/6/7/u/i/r"));

    // Prefer ideal state if all ready but no trusted
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 1 as active: copy is ready]"),
            testBucketState("2=8/9/10/u/i/r,1=2/3/4/u/i/r,3=5/6/7/u/i/r"));

    // Prefer trusted over ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 2 as active: copy is ready and trusted]"
                        "[Setting node 1 as inactive]"),
            testBucketState("2=8/9/10/t/i/r,1=2/3/4/u/a/r,3=5/6/7"));
}

/**
 * Users assume that setting nodes into maintenance will not cause extra load
 * on the cluster, but activating non-ready copies because the active copy went
 * into maintenance violates that assumption. See bug 6833209 for context and
 * details.
 */
void
StateCheckersTest::testDoNotActivateNonReadyCopiesWhenIdealNodeInMaintenance()
{
    setupDistributor(2, 100, "distributor:1 storage:4 .1.s:m");
    // Ideal node 1 is in maintenance and no ready copy available.
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("2=8/9/10/t/i/u,3=5/6/7"));
    // But we should activate another copy iff there's another ready copy.
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 2 as active: copy is ready]"),
            testBucketState("2=8/9/10/u/i/r,3=5/6/7/u/i/u"));
}

/**
 * We really do not want to activate buckets when they are inconsistent.
 * See bug 6395693 for a set of reasons why.
 */
void
StateCheckersTest::testDoNotChangeActiveStateForInconsistentlySplitBuckets()
{
    setupDistributor(2, 100, "distributor:1 storage:4");
    // Running state checker on a leaf:
    addNodesToBucketDB(document::BucketId(16, 0), "0=2");
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=1")); // 17 bits
    // Running state checker on an inner node bucket:
    addNodesToBucketDB(document::BucketId(18, 0), "0=2");
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testSynchronizeAndMove("0=1")); // 17 bits
}

/**
 * If all existing copies are outside the ideal state, e.g. if the set of nodes
 * in the cluster has changed significantly, we do not want to change the active
 * state of copies needlessly iff the copies are otherwise equally scored in
 * terms of activation eligibility. If we do not prioritize existing active
 * copies higher in this case, it's possible that their ideal order has been
 * permutated, causing another copy to rank higher in the ideal state node
 * sequence. This would in turn activate the newly higher ranked copy and
 * deactivate the previously active copy, causing transient search duplicates
 * and uneeded work in the cluster; new copies will be created and indexed
 * soon anyway.
 *
 * See bug 7278932.
 */
void
StateCheckersTest::testNoActiveChangeForNonIdealCopiesWhenOtherwiseIdentical()
{
    setupDistributor(2, 100, "distributor:1 storage:50");
    // 1 is more ideal than 3 in this state, but since they're both not part
    // of the #redundancy ideal set, activation should not change hands.
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=2/3/4/t/i/r,3=2/3/4/t/a/r"));
    // Same applies if the copies aren't ready, since if a copy has been marked
    // as active it will already have started background indexing. No need in
    // undoing that if we don't have any better candidates going anyway.
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=2/3/4/t,3=2/3/4/t/a"));
}

std::string StateCheckersTest::testBucketStatePerGroup(
        const std::string& bucketInfo, bool includePriority)
{
    document::BucketId bid(17, 0);
    addNodesToBucketDB(bid, bucketInfo);

    BucketStateStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, PendingMessage(),
                            includePriority);
}

void
StateCheckersTest::testBucketStatePerGroup()
{
    setupDistributor(6, 20, "distributor:1 storage:12 .2.s:d .4.s:d .7.s:d");
    vespa::config::content::StorDistributionConfigBuilder config;
    config.activePerLeafGroup = true;
    config.redundancy = 6;
    config.group.resize(4);
    config.group[0].index = "invalid";
    config.group[0].name = "invalid";
    config.group[0].partitions = "2|2|*";
    config.group[1].index = "0";
    config.group[1].name = "left";
    config.group[1].nodes.resize(3);
    config.group[1].nodes[0].index = 0;
    config.group[1].nodes[1].index = 1;
    config.group[1].nodes[2].index = 3;
    config.group[2].index = "1";
    config.group[2].name = "right";
    config.group[2].nodes.resize(3);
    config.group[2].nodes[0].index = 5;
    config.group[2].nodes[1].index = 6;
    config.group[2].nodes[2].index = 8;
    config.group[3].index = "2";
    config.group[3].name = "middle";
    config.group[3].nodes.resize(3);
    config.group[3].nodes[0].index = 9;
    config.group[3].nodes[1].index = 10;
    config.group[3].nodes[2].index = 11;
    lib::Distribution::SP distr(new lib::Distribution(config));
    triggerDistributionChange(std::move(distr));

    {
        DistributorConfiguration::MaintenancePriorities mp;
        mp.activateNoExistingActive = 90;
        mp.activateWithExistingActive = 120;
        getConfig().setMaintenancePriorities(mp);
    }

    // Node 1 and 8 is is ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 1 as active: "
                        "copy is trusted and ideal state priority 4]"
                        "[Setting node 6 as active: "
                        "copy is trusted and ideal state priority 0] (pri 90)"),
            testBucketStatePerGroup("0=2/3/4/t, 1=2/3/4/t, 3=2/3/4/t, "
                                    "5=2/3/4/t, 6=2/3/4/t, 8=2/3/4/t", true));

    // Data differ between groups
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 1 as active: "
                        "copy is trusted and ideal state priority 4]"
                        "[Setting node 6 as active: "
                        "copy is ideal state priority 0] (pri 90)"),
            testBucketStatePerGroup("0=2/3/4/t, 1=2/3/4/t, 3=2/3/4/t, "
                                    "5=5/6/7, 6=5/6/7, 8=5/6/7", true));

    // Disable too
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 0 as inactive]"
                        "[Setting node 3 as inactive]"
                        "[Setting node 5 as inactive]"
                        "[Setting node 8 as inactive] (pri 90)"),
            testBucketStatePerGroup("0=2/3/4/t/a, 1=2/3/4/t/a, 3=2/3/4/t/a, "
                                    "5=2/3/4/t/a, 6=2/3/4/t/a, 8=2/3/4/t/a",
                                    true));

    // Node 1 and 8 is is ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Setting node 1 as active: "
                        "copy is trusted and ideal state priority 4]"
                        "[Setting node 6 as active: "
                        "copy is trusted and ideal state priority 0]"
                        "[Setting node 9 as active: "
                        "copy is trusted and ideal state priority 2] (pri 90)"),
            testBucketStatePerGroup("0=2/3/4/t, 1=2/3/4/t, 3=2/3/4/t, "
                                    "5=2/3/4/t, 6=2/3/4/t, 8=2/3/4/t, "
                                    "9=2/3/4/t, 10=2/3/4/t, 11=2/3/4/t",
                                    true));
}

void
StateCheckersTest::allowActivationOfRetiredNodes()
{
    // All nodes in retired state implies that the ideal state is empty. But
    // we still want to be able to shuffle bucket activations around in order
    // to preserve coverage.
    setupDistributor(2, 2, "distributor:1 storage:2 .0.s:r .1.s:r");
    CPPUNIT_ASSERT_EQUAL(
            "[Setting node 1 as active: copy is trusted]"
            "[Setting node 0 as inactive]"s,
            testBucketState("0=2/3/4/u/a,1=5/6/7/t"));
}

void
StateCheckersTest::inhibitBucketActivationIfDisabledInConfig()
{
    setupDistributor(2, 4, "distributor:1 storage:4");
    disableBucketActivationInConfig(true);

    // Node 1 is in ideal state and only replica and should be activated in
    // an indexed cluster context (but not here).
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=2/3/4", 2, true));
}

void
StateCheckersTest::inhibitBucketDeactivationIfDisabledInConfig()
{
    setupDistributor(2, 4, "distributor:1 storage:4");
    disableBucketActivationInConfig(true);

    // Multiple replicas which would have been deactivated. This test is mostly
    // for the sake of completion; a scenario where buckets are active while
    // having no indexed documents configured should not happen.
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testBucketState("1=1/2/3/t/a,2=1/2/3/t/a,3=1/2/3/t/a"));
}

std::string StateCheckersTest::testGarbageCollection(
        uint32_t prevTimestamp, uint32_t nowTimestamp,
        uint32_t checkInterval, uint32_t lastChangeTime,
        bool includePriority, bool includeSchedulingPri)
{
    BucketDatabase::Entry e(document::BucketId(17, 0));
    e.getBucketInfo().addNode(BucketCopy(prevTimestamp, 0,
                                         api::BucketInfo(3,3,3)),
                              toVector((uint16_t)0));
    e.getBucketInfo().setLastGarbageCollectionTime(prevTimestamp);
    getBucketDatabase().update(e);

    GarbageCollectionStateChecker checker;
    getConfig().setGarbageCollection("music", checkInterval);
    getConfig().setLastGarbageCollectionChangeTime(lastChangeTime);
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker,
                            makeDocumentBucket(e.getBucketId()));
    getClock().setAbsoluteTimeInSeconds(nowTimestamp);
    return testStateChecker(checker, c, false, PendingMessage(),
                            includePriority, includeSchedulingPri);
}

void
StateCheckersTest::testGarbageCollection()
{
    // BucketId(17, 0) has id (and thus 'hash') 0x4400000000000000. With a
    // check interval modulo of 3600, this implies a start point of 848.

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testGarbageCollection(900, 3600 + 847, 3600));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Needs garbage collection: Last check at 900, current time 4448, "
                        "configured interval 3600]"),
            testGarbageCollection(900, 3600 + 848, 3600));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Needs garbage collection: Last check at 3, current time 4000, "
                        "configured interval 3600]"),
            testGarbageCollection(3, 4000, 3600));

    // GC start point 3648.
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testGarbageCollection(3, 3647, 8000));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Needs garbage collection: Last check at 3, current time 4000, "
                        "configured interval 3600]"),
            testGarbageCollection(3, 4000, 3600));

    // GC explicitly disabled.
    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testGarbageCollection(3, 4000, 0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testGarbageCollection(3, 3, 1));

    CPPUNIT_ASSERT_EQUAL(
            std::string("[Needs garbage collection: Last check at 3, current time 4000, "
                        "configured interval 300] (pri 200)"),
            testGarbageCollection(3, 4000, 300, 1, true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("NO OPERATIONS GENERATED"),
            testGarbageCollection(3850, 4000, 300, 1));
}

void StateCheckersTest::gc_ops_are_prioritized_with_low_priority_category() {
    CPPUNIT_ASSERT_EQUAL(
            std::string("[Needs garbage collection: Last check at 3, current time 4000, "
                        "configured interval 300] (scheduling pri VERY_LOW)"),
            testGarbageCollection(3, 4000, 300, 1, false, true));
}

/**
 * When a node is in maintenance, we want to do our best to avoid any unneeded
 * changes to the bucket replicas' states, as this will require re-syncing of
 * the replicas when the node out of maintenance. Consequently we should not
 * trigger GC for buckets when this is the case.
 */
void
StateCheckersTest::gcInhibitedWhenIdealNodeInMaintenance()
{
    // Redundancy is 3, so with only 3 nodes, node 1 is guaranteed to be part of
    // the ideal state of any bucket in the system.
    setupDistributor(3, 3, "distributor:1 storage:3 .1.s:m");
    document::BucketId bucket(17, 0);
    addNodesToBucketDB(bucket, "0=10/100/1/true,"
                               "1=10/100/1/true,"
                               "2=10/100/1/true");
    BucketDatabase::Entry e(getBucketDatabase().get(bucket));
    e.getBucketInfo().setLastGarbageCollectionTime(3);
    getBucketDatabase().update(e);

    GarbageCollectionStateChecker checker;
    getConfig().setGarbageCollection("music", 3600);
    getConfig().setLastGarbageCollectionChangeTime(0);
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker,
                            makeDocumentBucket(bucket));
    getClock().setAbsoluteTimeInSeconds(4000);
    // Would normally (in a non-maintenance case) trigger GC due to having
    // overshot the GC check cycle.
    auto result = testStateChecker(checker, c, false, PendingMessage(), false);

    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"), result);
}

/*
 * Bug 6656726, comment #25. Merge state checker does not execute if an ideal
 * node is in maintenance, so for symmetry we need to do the same for deletes
 * (it's bad mojo to potentially delete something that would've been merged
 * had it not been for a node being in maintenance).
 */
void
StateCheckersTest::testNoRemoveWhenIdealNodeInMaintenance()
{
    CPPUNIT_ASSERT_EQUAL_MSG(
            "Do not remove when ideal node is in maintenance mode",
            std::string("NO OPERATIONS GENERATED"),
            testDeleteExtraCopies("0=10/100/1/true,"
                                  "1=10/100/1/true,"
                                  "2=10/100/1/true",
                                  2, PendingMessage(),
                                  "distributor:1 storage:3 .1.s:m"));
}

/*
 * Just joining buckets where both children are present is not enough to
 * ensure any system can compact its bucket tree. We must therefore
 * gradually hoist buckets higher into the tree when possible in order
 * to converge in a state where as many buckets as possible have siblings
 * on the same level.
 *
 * See bug 6768991 for context.
 */
void
StateCheckersTest::testStepwiseJoinForSmallBucketsWithoutSiblings()
{
    setupDistributor(3, 10, "distributor:1 storage:2 bits:1");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);
    // Buckets without siblings but that should be step-wise joined back
    // into bucket (2, 1).
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x0800000000000001): "
            "[Joining buckets BucketId(0x0c00000000000001) and "
            "BucketId(0x0c00000000000001) because their size "
            "(1 bytes, 1 docs) is less than the configured limit "
            "of (100, 10)"),
            testJoin(10, 100, 2, document::BucketId(3, 1)));

    // Other bucket should be joined as well. Together the two join targets
    // will transform into a mighty sibling pair that can rule the galaxy
    // (and also be joined together afterwards)!
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x0800000000000003): "
            "[Joining buckets BucketId(0x0c00000000000003) and "
            "BucketId(0x0c00000000000003) because their size "
            "(1 bytes, 1 docs) is less than the configured limit "
            "of (100, 10)"),
            testJoin(10, 100, 2, document::BucketId(3, 0x3)));
}

void
StateCheckersTest::testNoStepwiseJoinWhenDisabledThroughConfig()
{
    setupDistributor(3, 10, "distributor:1 storage:2 bits:1");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = false;
    getConfig().configure(config);

    // Buckets without siblings but that would have been step-wise joined back
    // into bucket 1 if it had been config-enabled.
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 1, document::BucketId(3, 1)));
}

void
StateCheckersTest::testNoStepwiseJoinWhenSingleSiblingTooLarge()
{
    setupDistributor(3, 10, "distributor:1 storage:2 bits:1");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);

    // Bucket is exactly at the boundary where it's too big.
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 10, 100);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(std::string("NO OPERATIONS GENERATED"),
            testJoin(10, 100, 1, document::BucketId(3, 1)));
}

void
StateCheckersTest::testStepwiseJoinMaySkipMultipleBitsWhenConsistent()
{
    setupDistributor(2, 10, "distributor:1 storage:2 bits:8");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);

    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    // No buckets further up in the tree, can join up to the distribution bit
    // limit at 8.
    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x2000000000000001): "
            "[Joining buckets BucketId(0x4000000000000001) and "
            "BucketId(0x4000000000000001) because their size "
            "(1 bytes, 1 docs) is less than the configured limit "
            "of (100, 10)"),
            testJoin(10, 100, 8, document::BucketId(16, 1)));
}

void
StateCheckersTest::testStepwiseJoinDoesNotSkipBeyondLevelWithSibling()
{
    setupDistributor(2, 10, "distributor:1 storage:2 bits:8");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);

    // All 0-branch children
    insertBucketInfo(document::BucketId(16, 0), 1, 0x1, 1, 1);
    // 0-branches down to level 10, then 1-branch down to level 11. This means
    // the (16, 0) bucket cannot be moved further up than level 11 as it has a
    // sibling there (0x2c00000000000400 sibling of 0x2c00000000000000).
    insertBucketInfo(document::BucketId(11, 1 << 10), 1, 0x1, 1, 1);
    CPPUNIT_ASSERT_EQUAL(std::string(
            "BucketId(0x2c00000000000000): "
            "[Joining buckets BucketId(0x4000000000000000) and "
            "BucketId(0x4000000000000000) because their size "
            "(1 bytes, 1 docs) is less than the configured limit "
            "of (100, 10)"),
            testJoin(10, 100, 8, document::BucketId(16, 0)));
}

void
StateCheckersTest::joinCanBeScheduledWhenReplicasOnRetiredNodes()
{
    setupDistributor(1, 1, "distributor:1 storage:1 .0.s.:r");
    insertJoinableBuckets();
    CPPUNIT_ASSERT_EQUAL(
            "BucketId(0x8000000000000001): "
            "[Joining buckets BucketId(0x8400000000000001) and "
            "BucketId(0x8400000100000001) because their size "
            "(2 bytes, 2 docs) is less than the configured limit "
            "of (100, 10)"s,
            testJoin(10, 100, 16, document::BucketId(33, 1)));
}

void
StateCheckersTest::contextPopulatesIdealStateContainers()
{
    // 1 and 3 are ideal nodes for bucket {17, 0}
    setupDistributor(2, 100, "distributor:1 storage:4");

    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(getExternalOperationHandler(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket({17, 0}));

    CPPUNIT_ASSERT_EQUAL((std::vector<uint16_t>{1, 3}), c.idealState);
    CPPUNIT_ASSERT_EQUAL(size_t(2), c.unorderedIdealState.size());
    CPPUNIT_ASSERT(c.unorderedIdealState.find(1)
                   != c.unorderedIdealState.end());
    CPPUNIT_ASSERT(c.unorderedIdealState.find(3)
                   != c.unorderedIdealState.end());
}

namespace {

template <typename Checker>
class StateCheckerRunner
{
    StateCheckersTest& _fixture;
    NodeMaintenanceStatsTracker _statsTracker;
    std::string _result;
public:
    StateCheckerRunner(StateCheckersTest& fixture);
    ~StateCheckerRunner();


    StateCheckerRunner& addToDb(const document::BucketId& bid,
                                const std::string& bucketInfo)
    {
        _fixture.addNodesToBucketDB(bid, bucketInfo);
        return *this;
    }

    StateCheckerRunner& redundancy(uint32_t red) {
        _fixture.setRedundancy(red);
        return *this;
    }

    StateCheckerRunner& clusterState(const std::string& state) {
        _fixture.enableClusterState(lib::ClusterState(state));
        return *this;
    }

    // Run the templated state checker with the provided parameters, updating
    // _result with the ideal state operations triggered.
    // NOTE: resets the bucket database!
    void runFor(const document::BucketId& bid) {
        Checker checker;
        StateChecker::Context c(_fixture.getExternalOperationHandler(), _fixture.getDistributorBucketSpace(), _statsTracker, makeDocumentBucket(bid));
        _result = _fixture.testStateChecker(
                checker, c, false, StateCheckersTest::PendingMessage(), false);
    }

    const std::string& result() const { return _result; }
    const NodeMaintenanceStatsTracker& stats() const {
        return _statsTracker;
    }
};

template <typename Checker>
StateCheckerRunner<Checker>::StateCheckerRunner(StateCheckersTest& fixture)
    : _fixture(fixture)
{}
template <typename Checker>
StateCheckerRunner<Checker>::~StateCheckerRunner() {}

} // anon ns

void
StateCheckersTest::statsUpdatedWhenMergingDueToMove()
{
    StateCheckerRunner<SynchronizeAndMoveStateChecker> runner(*this);
    // Ideal state for bucket {17,0} in given cluster state is [1, 3]
    runner.addToDb({17, 0}, "0=1,1=1,2=1")
          .clusterState("distributor:1 storage:4")
          .runFor({17, 0});
    // Node 1 treated as copy source, but not as move source.
    {
        NodeMaintenanceStats wanted;
        wanted.copyingOut = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(1, makeBucketSpace()));
    }
    // Moving 1 bucket from nodes {0, 2} into 3.
    // Note that we do not at this point in time distinguish _which_ of these
    // will do the actual data movement to node 3.
    {
        NodeMaintenanceStats wanted;
        wanted.copyingIn = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(3, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.movingOut = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(0, makeBucketSpace()));
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(2, makeBucketSpace()));
    }
}

void
StateCheckersTest::statsUpdatedWhenMergingDueToMissingCopy()
{
    StateCheckerRunner<SynchronizeAndMoveStateChecker> runner(*this);
    // Ideal state for bucket {17,0} in given cluster state is [1, 3]
    runner.addToDb({17, 0}, "1=1")
          .clusterState("distributor:1 storage:4")
          .runFor({17, 0});

    {
        NodeMaintenanceStats wanted;
        wanted.copyingIn = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(3, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.copyingOut = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(1, makeBucketSpace()));
    }
}

void
StateCheckersTest::statsUpdatedWhenMergingDueToOutOfSyncCopies()
{
    StateCheckerRunner<SynchronizeAndMoveStateChecker> runner(*this);
    runner.addToDb({17, 0}, "1=1,3=2")
          .clusterState("distributor:1 storage:4")
          .runFor({17, 0});
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(1, makeBucketSpace()));
        CPPUNIT_ASSERT_EQUAL(wanted, runner.stats().forNode(3, makeBucketSpace()));
    }
}

}
