// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributortestutil.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storage/distributor/statecheckers.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct StateCheckersTest : Test, DistributorTestUtil {
    StateCheckersTest() = default;

    void SetUp() override {
        createLinks();
    }

    void TearDown() override {
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

    void enableClusterState(const lib::ClusterState& systemState) {
        _distributor->enableClusterStateBundle(lib::ClusterStateBundle(systemState));
    }

    void insertJoinableBuckets();

    void assertCurrentIdealState(const document::BucketId& bucket,
                                 const std::vector<uint16_t>& expected)
    {
        auto& distributorBucketSpace(getIdealStateManager().getBucketSpaceRepo().get(makeBucketSpace()));
        std::vector<uint16_t> idealNodes(
                distributorBucketSpace
                .getDistribution().getIdealStorageNodes(
                        distributorBucketSpace.getClusterState(),
                        bucket,
                        "ui"));
        ASSERT_EQ(expected, idealNodes);
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
        getBucketDatabase(c.getBucketSpace()).getAll(c.getBucketId(), entries);
        c.siblingEntry = getBucketDatabase(c.getBucketSpace()).get(c.siblingBucket);

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

        getBucketDatabase(c.getBucketSpace()).clear();

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
        std::string _pending_cluster_state;
        std::string _expect;
        document::BucketSpace _bucket_space {document::FixedBucketSpaces::default_space()};
        static const PendingMessage NO_OP_BLOCKER;
        const PendingMessage* _blockerMessage {&NO_OP_BLOCKER};
        uint32_t _redundancy {2};
        uint32_t _splitCount {0};
        uint32_t _splitSize {0};
        uint32_t _minSplitBits {0};
        bool _includeMessagePriority {false};
        bool _includeSchedulingPriority {false};
        bool _merge_operations_disabled {false};
        bool _prioritize_global_bucket_merges {true};
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
        CheckerParams& pending_cluster_state(const std::string& state) {
            _pending_cluster_state = state;
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
        CheckerParams& merge_operations_disabled(bool disabled) noexcept {
            _merge_operations_disabled = disabled;
            return *this;
        }
        CheckerParams& prioritize_global_bucket_merges(bool enabled) noexcept {
            _prioritize_global_bucket_merges = enabled;
            return *this;
        }
        CheckerParams& bucket_space(document::BucketSpace bucket_space) noexcept {
            _bucket_space = bucket_space;
            return *this;
        }
    };

    template <typename CheckerImpl>
    void runAndVerify(const CheckerParams& params) {
        CheckerImpl checker;

        document::BucketId bid(17, 0);
        document::Bucket bucket(params._bucket_space, bid);
        addNodesToBucketDB(bucket, params._bucketInfo);
        setRedundancy(params._redundancy);
        enableDistributorClusterState(params._clusterState);
        getConfig().set_merge_operations_disabled(params._merge_operations_disabled);
        getConfig().set_prioritize_global_bucket_merges(params._prioritize_global_bucket_merges);
        if (!params._pending_cluster_state.empty()) {
            auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(params._pending_cluster_state));
            _distributor->onDown(cmd);
            tick(); // Trigger command processing and pending state setup.
        }
        NodeMaintenanceStatsTracker statsTracker;
        StateChecker::Context c(distributor_component(),
                                getBucketSpaceRepo().get(params._bucket_space),
                                statsTracker,
                                bucket);
        std::string result =  testStateChecker(
                checker, c, false, *params._blockerMessage,
                params._includeMessagePriority,
                params._includeSchedulingPriority);
        ASSERT_EQ(params._expect, result);
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
};

StateCheckersTest::CheckerParams::CheckerParams() = default;
StateCheckersTest::CheckerParams::~CheckerParams() = default;


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
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    getConfig().setSplitSize(splitSize);
    getConfig().setSplitCount(splitCount);
    getConfig().setMinimalBucketSplit(minSplitBits);
    return testStateChecker(checker, c, false, blocker, includePriority);
}

TEST_F(StateCheckersTest, split) {
    setupDistributor(3, 10, "distributor:1 storage:2");

    EXPECT_EQ("[Splitting bucket because its maximum size (2000 b, 10 docs, 10 meta, 2000 b total) "
              "is higher than the configured limit of (1000, 4294967295)]",
              testSplit((uint32_t)-1, 1000, 16, "0=100/10/2000"));

    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, "
              "200 docs, 200 meta, 1000 b total) "
              "is higher than the configured limit of (10000, 100)] "
              "(pri 175)",
              testSplit(100, 10000, 16, "0=100/200/1000", PendingMessage(), true));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testSplit(1000, 1000, 16, "0=100/200/200"));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testSplit(1000, 1000, 16, "0=100/200/200/2000/2000"));

    EXPECT_EQ("[Splitting bucket because the current system size requires "
              "a higher minimum split bit]",
              testSplit((uint32_t)-1, (uint32_t)-1, 24, "0=100/200/1000"));

    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
              "is higher than the configured limit of (10000, 100)]",
              testSplit(100, 10000, 16, "0=100/10/10,1=100/1000/1000"));

    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
              "is higher than the configured limit of (10000, 100)]",
              testSplit(100, 10000, 16, "0=1/0/0,1=100/1000/1000"));

    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
              "is higher than the configured limit of (10000, 100)]",
              testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000"));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testSplit(1000, 1000, 16, "0=100/1/200000"));

    EXPECT_EQ("BLOCKED",
              testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000",
                        PendingMessage(api::MessageType::SPLITBUCKET_ID, 0)));

    // Split on too high meta
    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, 100 docs, 2100 meta, 15000000 b total) "
              "is higher than the configured limit of (10000000, 1000)]",
              testSplit(1000, 10000000, 16, "0=14/100/1000/2100/15000000"));
    // Split on too high file size
    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, 100 docs, 1500 meta, 21000000 b total) "
              "is higher than the configured limit of (10000000, 1000)]",
              testSplit(1000, 10000000, 16, "0=14/100/1000/1500/21000000"));

    // Don't block higher priority splits than what's already pending.
    EXPECT_EQ("[Splitting bucket because its maximum size (1000 b, 1000 docs, 1000 meta, 1000 b total) "
              "is higher than the configured limit of (10000, 100)]",
              testSplit(100, 10000, 16, "0=100/10/10,1=100/1000/1000",
                        PendingMessage(api::MessageType::SPLITBUCKET_ID, 255)));

    // But must block equal priority splits that are already pending, or
    // we'll end up spamming the nodes with splits!
    // NOTE: assuming split priority of 175.
    EXPECT_EQ("BLOCKED",
              testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000",
                        PendingMessage(api::MessageType::SPLITBUCKET_ID, 175)));

    // Don't split if we're already joining, since there's a window of time
    // where the bucket will appear to be inconsistently split when the join
    // is not finished on all the nodes.
    EXPECT_EQ("BLOCKED",
              testSplit(100, 10000, 16, "0=0/0/1,1=100/1000/1000",
                        PendingMessage(api::MessageType::JOINBUCKETS_ID, 175)));
}

std::string
StateCheckersTest::testInconsistentSplit(const document::BucketId& bid,
                                         bool includePriority)
{
    SplitInconsistentStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, true,
                            PendingMessage(), includePriority);
}

TEST_F(StateCheckersTest, inconsistent_split) {
    setupDistributor(3, 10, "distributor:1 storage:2");

    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testInconsistentSplit(document::BucketId(16, 1)));

    insertBucketInfo(document::BucketId(17, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);

    EXPECT_EQ("BucketId(0x4000000000000001): [Bucket is inconsistently "
              "split (list includes 0x4000000000000001, 0x4400000000000001) "
              "Splitting it to improve the problem (max used bits 17)]",
              testInconsistentSplit(document::BucketId(16, 1)));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testInconsistentSplit(document::BucketId(17, 1)));

    insertBucketInfo(document::BucketId(17, 1), 0, 0x0, 0, 0);
    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    EXPECT_EQ("BucketId(0x4000000000000001): [Bucket is inconsistently "
              "split (list includes 0x4000000000000001, 0x4400000000000001) "
              "Splitting it to improve the problem (max used bits "
              "17)] (pri 110)",
              testInconsistentSplit(document::BucketId(16, 1), true));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testInconsistentSplit(document::BucketId(17, 1)));
}

TEST_F(StateCheckersTest, split_can_be_scheduled_when_replicas_on_retired_nodes) {
    setupDistributor(Redundancy(2), NodeCount(2),
                     "distributor:1 storage:2, .0.s:r .1.s:r");
    EXPECT_EQ("[Splitting bucket because its maximum size (2000 b, 10 docs, "
              "10 meta, 2000 b total) is higher than the configured limit of "
              "(1000, 4294967295)]",
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
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, true, blocker, includePriority);
}

void
StateCheckersTest::insertJoinableBuckets()
{
    insertBucketInfo(document::BucketId(33, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
}

TEST_F(StateCheckersTest, join) {
    setupDistributor(3, 10, "distributor:1 storage:2");

    insertJoinableBuckets();
    EXPECT_EQ("BucketId(0x8000000000000001): "
              "[Joining buckets BucketId(0x8400000000000001) and "
              "BucketId(0x8400000100000001) because their size "
              "(2 bytes, 2 docs) is less than the configured limit "
              "of (100, 10)",
              testJoin(10, 100, 16, document::BucketId(33, 1)));

    insertJoinableBuckets();
    // Join size is 0, so only look at document count
    EXPECT_EQ("BucketId(0x8000000000000001): "
              "[Joining buckets BucketId(0x8400000000000001) and "
              "BucketId(0x8400000100000001) because their size "
              "(2 bytes, 2 docs) is less than the configured limit "
              "of (0, 3) (pri 155)",
              testJoin(3, 0, 16, document::BucketId(33, 1), PendingMessage(), true));

    insertJoinableBuckets();
    // Should not generate joins for both pairs, just the primary
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 0x100000001)));

    insertJoinableBuckets();
    // Should not generate join if min split bits is higher
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 33, document::BucketId(33, 1)));

    insertJoinableBuckets();
    // Meta data too big, no join
    insertBucketInfo(document::BucketId(33, 1), 1,
                     api::BucketInfo(0x1, 1, 1, 1000, 1000));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 1)));

    insertJoinableBuckets();
    // Bucket recently created
    insertBucketInfo(document::BucketId(33, 1), 1,
                     api::BucketInfo(0x1, 0, 0, 0, 0));
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 1)));

}

/**
 * If distributor config says minsplitcount is 8, but cluster state says that
 * distribution bit count is 16, we should not allow the join to take place.
 * We don't properly handle the "reduce distribution bits" case in general, so
 * the safest is to never violate this and to effectively make distribution
 * bit increases a one-way street.
 */
TEST_F(StateCheckersTest, do_not_join_below_cluster_state_bit_count) {
    setupDistributor(2, 2, "bits:16 distributor:1 storage:2");
    // Insert sibling buckets at 16 bits that are small enough to be joined
    // unless there is special logic for dealing with distribution bits.
    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(16, (1 << 15) | 1), 1, 0x1, 1, 1);
    using ConfiguredMinSplitBits = uint32_t;
    EXPECT_EQ("NO OPERATIONS GENERATED",
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

TEST_F(StateCheckersTest, no_join_when_invalid_copy_exists) {
    setupDistributor(3, 10, "distributor:1 storage:3");

    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
    // No join when there exists an invalid copy
    insertBucketInfo(document::BucketId(33, 1), 1, api::BucketInfo());

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 1)));
}

TEST_F(StateCheckersTest, no_join_on_different_nodes) {
    setupDistributor(3, 10, "distributor:1 storage:2");

    insertBucketInfo(document::BucketId(33, 0x000000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

TEST_F(StateCheckersTest, no_join_when_copy_count_above_redundancy_levels_for_left_sibling) {
    setupDistributor(3, 10, "distributor:1 storage:2");
    setRedundancy(1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 0, 0x1, 1, 1);
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

TEST_F(StateCheckersTest, no_join_when_copy_count_above_redundancy_levels_for_right_sibling) {
    setupDistributor(3, 10, "distributor:1 storage:2");
    setRedundancy(1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 16, document::BucketId(33, 0x1)));
}

TEST_F(StateCheckersTest, no_join_when_copy_count_above_redundancy_levels_for_both_siblings) {
    setupDistributor(3, 10, "distributor:1 storage:2");
    setRedundancy(1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x000000001), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 0, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(33, 0x100000001), 1, 0x1, 1, 1);
    EXPECT_EQ("NO OPERATIONS GENERATED",
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
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, blocker, includePriority);
}

TEST_F(StateCheckersTest, synchronize_and_move) {
    // Plus if it was more obvious which nodes were in ideal state for various
    // cluster states. (One possibility to override ideal state function for
    // test)
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams().expect(
                "[Synchronizing buckets with different checksums "
                "node(idx=0,crc=0x1,docs=1/1,bytes=1/1,trusted=false,active=false,ready=false), "
                "node(idx=1,crc=0x2,docs=2/2,bytes=2/2,trusted=false,active=false,ready=false)] "
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
                    "node(idx=0,crc=0x3,docs=3/3,bytes=3/3,trusted=false,active=false,ready=false), "
                    "node(idx=1,crc=0x3,docs=3/3,bytes=3/3,trusted=false,active=false,ready=false), "
                    "node(idx=2,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false)]")
            .bucketInfo("0=3,1=3,2=0")
            .clusterState("distributor:1 storage:3"));

    // Synchronize even when we have >= redundancy trusted copies and ideal
    // nodes are in sync.
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("[Synchronizing buckets with different checksums "
                    "node(idx=0,crc=0x2,docs=3/3,bytes=4/4,trusted=false,active=false,ready=false), "
                    "node(idx=1,crc=0x1,docs=2/2,bytes=3/3,trusted=true,active=false,ready=false), "
                    "node(idx=2,crc=0x1,docs=2/2,bytes=3/3,trusted=true,active=false,ready=false), "
                    "node(idx=3,crc=0x1,docs=2/2,bytes=3/3,trusted=true,active=false,ready=false)] "
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

TEST_F(StateCheckersTest, global_bucket_merges_have_very_high_priority_if_prioritization_enabled) {
    runAndVerify<SynchronizeAndMoveStateChecker>(
            CheckerParams().expect(
                            "[Synchronizing buckets with different checksums "
                            "node(idx=0,crc=0x1,docs=1/1,bytes=1/1,trusted=false,active=false,ready=false), "
                            "node(idx=1,crc=0x2,docs=2/2,bytes=2/2,trusted=false,active=false,ready=false)] "
                            "(pri 115) "
                            "(scheduling pri VERY_HIGH)")
                    .bucketInfo("0=1,1=2")
                    .bucket_space(document::FixedBucketSpaces::global_space())
                    .includeSchedulingPriority(true)
                    .includeMessagePriority(true)
                    .prioritize_global_bucket_merges(true));
}

TEST_F(StateCheckersTest, global_bucket_merges_have_normal_priority_if_prioritization_disabled) {
    runAndVerify<SynchronizeAndMoveStateChecker>(
            CheckerParams().expect(
                            "[Synchronizing buckets with different checksums "
                            "node(idx=0,crc=0x1,docs=1/1,bytes=1/1,trusted=false,active=false,ready=false), "
                            "node(idx=1,crc=0x2,docs=2/2,bytes=2/2,trusted=false,active=false,ready=false)] "
                            "(pri 120) "
                            "(scheduling pri MEDIUM)")
                    .bucketInfo("0=1,1=2")
                    .bucket_space(document::FixedBucketSpaces::global_space())
                    .includeSchedulingPriority(true)
                    .includeMessagePriority(true)
                    .prioritize_global_bucket_merges(false));
}

// Upon entering a cluster state transition edge the distributor will
// prune all replicas from its DB that are on nodes that are unavailable
// in the _pending_ state. As long as this state is pending, the _current_
// state will include these nodes as available. But since replicas for
// the unavailable node(s) have been pruned away, started merges that
// involve these nodes as part of their chain are doomed to fail.
TEST_F(StateCheckersTest, do_not_schedule_merges_when_included_node_is_unavailable_in_pending_state) {
    runAndVerify<SynchronizeAndMoveStateChecker>(
            CheckerParams()
                .expect("NO OPERATIONS GENERATED")
                .redundancy(3)
                .bucketInfo("1=1,2=1") // Node 0 pruned from DB since it's s:m in state 2
                .clusterState("version:1 distributor:2 storage:3")
                // We change the distributor set as well as the content node set. Just setting a node
                // into maintenance does not trigger a pending state since it does not require any
                // bucket info fetches from any of the nodes.
                .pending_cluster_state("version:2 distributor:1 storage:3 .0.s:m"));
}

TEST_F(StateCheckersTest, do_not_merge_inconsistently_split_buckets) {
    // No merge generated if buckets are inconsistently split.
    // This matches the case where a bucket has been split into 2 on one
    // node and is not yet split on another; we should never try to merge
    // either two of the split leaf buckets back onto the first node!
    // Running state checker on a leaf:
    addNodesToBucketDB(document::BucketId(16, 0), "0=2");
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testSynchronizeAndMove("1=1", // 17 bits
                                     "distributor:1 storage:4"));
    // Running state checker on an inner node bucket:
    addNodesToBucketDB(document::BucketId(18, 0), "0=2");
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testSynchronizeAndMove("0=1", // 17 bits
                                     "distributor:1 storage:4"));
}

TEST_F(StateCheckersTest, do_not_move_replicas_within_retired_nodes) {
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

TEST_F(StateCheckersTest, retired_nodes_out_of_sync_are_merged) {
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

TEST_F(StateCheckersTest, no_merge_operation_generated_if_merges_explicitly_config_disabled) {
    runAndVerify<SynchronizeAndMoveStateChecker>(
        CheckerParams()
            .expect("NO OPERATIONS GENERATED") // Would normally generate a merge op
            .bucketInfo("0=1,2=2")
            .clusterState("distributor:1 storage:3")
            .merge_operations_disabled(true));
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
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, blocker, includePriority);
}


TEST_F(StateCheckersTest, delete_extra_copies) {
    setupDistributor(2, 100, "distributor:1 storage:4");

    {
        auto& distributorBucketSpace(getIdealStateManager().getBucketSpaceRepo().get(makeBucketSpace()));
        std::vector<uint16_t> idealNodes(
                distributorBucketSpace
                .getDistribution().getIdealStorageNodes(
                        distributorBucketSpace.getClusterState(),
                        document::BucketId(17, 0),
                        "ui"));
        std::vector<uint16_t> wanted = {1, 3};
        ASSERT_EQ(wanted, idealNodes);
    }

    EXPECT_EQ("[Removing all copies since bucket is empty:node(idx=0,crc=0x0,"
              "docs=0/0,bytes=0/0,trusted=false,active=false,ready=false)]"
              " (pri 100)",
              testDeleteExtraCopies("0=0", 2, PendingMessage(), "", true)) << "Remove empty buckets";

    EXPECT_EQ("[Removing redundant in-sync copy from node 2]",
              testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t")) << "Remove extra trusted copy";

    EXPECT_EQ("[Removing redundant in-sync copy from node 2]",
             testDeleteExtraCopies("3=3/3/3,1=3/3/3/t,2=3/3/3/t"))
             << "Redundant copies in sync can be removed without trusted being a "
                "factor of consideration. Ideal state copy not removed.";

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=3,1=3")) << "Need redundancy number of copies";

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=0/0/1,1=3,2=3"))
              << "Do not remove extra copies without enough trusted copies";

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=0/0/1,1=0/0/1"))
              << "Do not remove buckets that have meta entries";

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=1/0/0/t,1=1/0/0/t,2=1/0/0/t"))
              << "Do not remove any recently created copies";

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=2/3/4,1=1/2/3/t,2=1/2/3/t"))
              << "Do not remove untrusted copy that is out of sync";

    EXPECT_EQ("NO OPERATIONS GENERATED",
             testDeleteExtraCopies("0=2/3/4,1=1/2/3/t,2=1/2/3/t,3=1/2/3/t"))
             << "Do not remove out of sync copies, even if we have more than #"
                "redundancy trusted copies";

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=2/3/4,1=1/2/3,2=2/3/4,3=1/2/3"))
              << "Don't remove unless we have enough trusted "
                 "copies to satisfy redundancy";

    EXPECT_EQ("[Removing empty copy from node 4]",
             testDeleteExtraCopies("0=2/3/4,1=1/2/3,2=2/3/4,3=1/2/3,4=0/0/0"))
             << "Only remove empty copies unless all other copies are in sync";

    EXPECT_EQ("[Removing empty copy from node 0]",
              testDeleteExtraCopies("1=2/3,3=1/2/3,0=0/0/0")) << "Remove redundant empty copy";

    EXPECT_EQ("[Removing all copies since bucket is empty:"
              "node(idx=0,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false), "
              "node(idx=1,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false), "
              "node(idx=2,crc=0x0,docs=0/0,bytes=0/0,trusted=false,active=false,ready=false)]",
              testDeleteExtraCopies("0=0/0/0,1=0/0/0,2=0/0/0")) << "Remove empty bucket with multiple copies";

    EXPECT_EQ("BLOCKED",
              testDeleteExtraCopies("0=0/0/0,1=1/2/3/t,2=1/2/3/t",
                                    2,
                                    PendingMessage(api::MessageType::PUT_ID, 255)))
              << "Pending persistence operation blocks delete";
}

TEST_F(StateCheckersTest, do_not_delete_active_extra_copies) {
    setupDistributor(2, 100, "distributor:1 storage:4");

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t/a"))
              << "Do not delete redundant copy if it is marked active";
}

TEST_F(StateCheckersTest, consistent_copies_on_retired_nodes_may_be_deleted) {
    setupDistributor(2, 100, "distributor:1 storage:4 .1.s:r");

    EXPECT_EQ("[Removing redundant in-sync copy from node 1]",
              testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t"))
              << "Remove in-sync copy on node that is retired";
}

TEST_F(StateCheckersTest, redundant_copy_deleted_even_when_all_nodes_retired) {
    setupDistributor(2, 100, "distributor:1 storage:4 "
                     ".0.s:r .1.s:r .2.s:r .3.s:r");

    EXPECT_EQ("[Removing redundant in-sync copy from node 2]",
              testDeleteExtraCopies("3=3/3/3/t,1=3/3/3/t,2=3/3/3/t"))
              << "Remove in-sync copy on node that is retired";
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
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, PendingMessage(),
                            includePriority);
}

TEST_F(StateCheckersTest, bucket_state) {
    setupDistributor(2, 100, "distributor:1 storage:4");

    {
        // Set config explicitly so we can compare priorities for differing
        // cases.
        DistributorConfiguration::MaintenancePriorities mp;
        mp.activateNoExistingActive = 90;
        mp.activateWithExistingActive = 120;
        getConfig().setMaintenancePriorities(mp);
    }

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState(""));

    // Node 1 is in ideal state
    EXPECT_EQ("[Setting node 1 as active: copy has 3 docs and ideal state priority 0] (pri 90)",
              testBucketState("1=2/3/4", 2, true));

    // Node 3 is in ideal state
    EXPECT_EQ("[Setting node 3 as active: copy has 3 docs and ideal state priority 1]",
              testBucketState("3=2/3/4"));

    // No ready replicas. Node 1 is first in ideal state but node 2 has
    // more docs and should remain active.
    // Also check bad case where more than 1 node is set as active just
    // to ensure we can get out of that situation if it should ever happen.
    // Nothing done with node 3 since it's not active and shouldn't be.
    EXPECT_EQ("[Setting node 0 as inactive] (pri 90)",
              testBucketState("0=3/4/5/u/a,1=3,2=4/5/6/u/a,3=3", 2, true));

    // Test setting active when only node available is not contained
    // within the resolved ideal state.
    EXPECT_EQ("[Setting node 0 as active: copy has 3 docs]",
              testBucketState("0=2/3/4"));

    // A replica with more documents should be preferred over one with fewer.
    EXPECT_EQ("[Setting node 3 as active: copy has 6 docs and ideal state priority 1]"
              "[Setting node 1 as inactive]",
              testBucketState("1=2/3/4/u/a,3=5/6/7/t"));

    // Replica 2 has most documents and should be activated
    EXPECT_EQ("[Setting node 2 as active: copy has 9 docs]",
              testBucketState("1=2/3/4,3=5/6/7/,2=8/9/10/t"));

    // Make sure bucket db ordering does not matter
    EXPECT_EQ("[Setting node 2 as active: copy has 9 docs]",
              testBucketState("1=2/3/4,3=5/6/7,2=8/9/10/t"));

    // If copy is already active, we shouldn't generate operations
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=2/3/4/t/a"));
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=2/3/4,3=5/6/7/t/a"));
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("2=8/9/10/t/a,1=2/3/4,3=5/6/7"));

    // If multiple buckets are active, deactive all but one
    EXPECT_EQ("[Setting node 2 as inactive]"
              "[Setting node 3 as inactive]",
              testBucketState("1=1/2/3/t/a,2=1/2/3/t/a,3=1/2/3/t/a"));

    // Invalid buckets should not be included
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=0/0/1,3=0/0/1"));

    // Ready preferred over ideal state
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("2=8/9/10/t/i/u,1=2/3/4/u/a/r,3=5/6/7"));
    EXPECT_EQ("[Setting node 2 as active: copy is ready with 9 docs]"
              "[Setting node 1 as inactive]",
              testBucketState("2=8/9/10/u/i/r,1=2/3/4/u/a/u,3=5/6/7/u/i/u"));

    // Prefer in ideal state if multiple copies ready
    EXPECT_EQ("[Setting node 3 as active: copy is ready, has 9 docs and ideal state priority 1]"
              "[Setting node 1 as inactive]",
              testBucketState("2=8/9/10/u/i/r,1=2/3/4/u/a/u,3=8/9/10/u/i/r"));

    // Prefer ideal state if all ready
    EXPECT_EQ("[Setting node 1 as active: copy is ready, has 9 docs and ideal state priority 0]",
              testBucketState("2=8/9/10/u/i/r,1=8/9/10/u/i/r,3=8/9/10/u/i/r"));

    // Ready with more documents is preferred over ideal state or trusted
    EXPECT_EQ("[Setting node 2 as active: copy is ready with 9 docs]"
              "[Setting node 1 as inactive]",
              testBucketState("2=8/9/10/u/i/r,1=2/3/4/u/a/r,3=5/6/7/u/i/r"));
}

/**
 * Users assume that setting nodes into maintenance will not cause extra load
 * on the cluster, but activating non-ready copies because the active copy went
 * into maintenance violates that assumption. See bug 6833209 for context and
 * details.
 */
TEST_F(StateCheckersTest, do_not_activate_non_ready_copies_when_ideal_node_in_maintenance) {
    setupDistributor(2, 100, "distributor:1 storage:4 .1.s:m");
    // Ideal node 1 is in maintenance and no ready copy available.
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("2=8/9/10/t/i/u,3=5/6/7"));
    // But we should activate another copy iff there's another ready copy.
    EXPECT_EQ("[Setting node 2 as active: copy is ready with 9 docs]",
              testBucketState("2=8/9/10/u/i/r,3=5/6/7/u/i/u"));
}

/**
 * We really do not want to activate buckets when they are inconsistent.
 * See bug 6395693 for a set of reasons why.
 */
TEST_F(StateCheckersTest, do_not_change_active_state_for_inconsistently_split_buckets) {
    setupDistributor(2, 100, "distributor:1 storage:4");
    // Running state checker on a leaf:
    addNodesToBucketDB(document::BucketId(16, 0), "0=2");
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=1")); // 17 bits
    // Running state checker on an inner node bucket:
    addNodesToBucketDB(document::BucketId(18, 0), "0=2");
    EXPECT_EQ("NO OPERATIONS GENERATED",
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
TEST_F(StateCheckersTest, no_active_change_for_non_ideal_copies_when_otherwise_identical) {
    setupDistributor(2, 100, "distributor:1 storage:50");
    // 1 is more ideal than 3 in this state, but since they're both not part
    // of the #redundancy ideal set, activation should not change hands.
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=2/3/4/t/i/r,3=2/3/4/t/a/r"));
    // Same applies if the copies aren't ready, since if a copy has been marked
    // as active it will already have started background indexing. No need in
    // undoing that if we don't have any better candidates going anyway.
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=2/3/4/t,3=2/3/4/t/a"));
}

std::string StateCheckersTest::testBucketStatePerGroup(
        const std::string& bucketInfo, bool includePriority)
{
    document::BucketId bid(17, 0);
    addNodesToBucketDB(bid, bucketInfo);

    BucketStateStateChecker checker;
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket(bid));
    return testStateChecker(checker, c, false, PendingMessage(),
                            includePriority);
}

std::shared_ptr<lib::Distribution> make_3x3_group_config() {
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
    return std::make_shared<lib::Distribution>(config);
}

TEST_F(StateCheckersTest, bucket_state_per_group) {
    setupDistributor(6, 20, "distributor:1 storage:12 .2.s:d .4.s:d .7.s:d");
    triggerDistributionChange(make_3x3_group_config());

    {
        DistributorConfiguration::MaintenancePriorities mp;
        mp.activateNoExistingActive = 90;
        mp.activateWithExistingActive = 120;
        getConfig().setMaintenancePriorities(mp);
    }

    // Node 1 and 8 is is ideal state
    EXPECT_EQ("[Setting node 1 as active: copy has 3 docs and ideal state priority 4]"
              "[Setting node 6 as active: copy has 3 docs and ideal state priority 0] (pri 90)",
              testBucketStatePerGroup("0=2/3/4/t, 1=2/3/4/t, 3=2/3/4/t, "
                                      "5=2/3/4/t, 6=2/3/4/t, 8=2/3/4/t", true));

    // Data differ between groups
    EXPECT_EQ("[Setting node 1 as active: copy has 3 docs and ideal state priority 4]"
              "[Setting node 6 as active: copy has 6 docs and ideal state priority 0] (pri 90)",
              testBucketStatePerGroup("0=2/3/4/t, 1=2/3/4/t, 3=2/3/4/t, "
                                      "5=5/6/7, 6=5/6/7, 8=5/6/7", true));

    // Disable too
    EXPECT_EQ("[Setting node 0 as inactive]"
              "[Setting node 3 as inactive]"
              "[Setting node 5 as inactive]"
              "[Setting node 8 as inactive] (pri 90)",
              testBucketStatePerGroup("0=2/3/4/t/a, 1=2/3/4/t/a, 3=2/3/4/t/a, "
                                      "5=2/3/4/t/a, 6=2/3/4/t/a, 8=2/3/4/t/a",
                                      true));

    // Node 1 and 8 is is ideal state
    EXPECT_EQ("[Setting node 1 as active: copy has 3 docs and ideal state priority 4]"
              "[Setting node 6 as active: copy has 3 docs and ideal state priority 0]"
              "[Setting node 9 as active: copy has 3 docs and ideal state priority 2] (pri 90)",
              testBucketStatePerGroup("0=2/3/4/t, 1=2/3/4/t, 3=2/3/4/t, "
                                      "5=2/3/4/t, 6=2/3/4/t, 8=2/3/4/t, "
                                      "9=2/3/4/t, 10=2/3/4/t, 11=2/3/4/t",
                                      true));
}

TEST_F(StateCheckersTest, do_not_activate_replicas_that_are_out_of_sync_with_majority) {
    // TODO why this strange distribution...
    // groups: [0, 1, 3] [5, 6, 8] [9, 10, 11]
    setupDistributor(6, 12, "distributor:1 storage:12 .2.s:d .4.s:d .7.s:d");
    triggerDistributionChange(make_3x3_group_config());
    getConfig().set_max_activation_inhibited_out_of_sync_groups(3);

    // 5 is out of sync with 0 and 9 and will NOT be activated.
    EXPECT_EQ("[Setting node 0 as active: copy has 3 docs]"
              "[Setting node 9 as active: copy has 3 docs and ideal state priority 2]",
              testBucketStatePerGroup("0=2/3/4, 5=3/4/5, 9=2/3/4"));

    // We also try the other indices:...
    // 0 out of sync, 5 and 9 in sync (one hopes..!)
    EXPECT_EQ("[Setting node 5 as active: copy has 3 docs]"
              "[Setting node 9 as active: copy has 3 docs and ideal state priority 2]",
              testBucketStatePerGroup("0=4/5/6, 5=2/3/4, 9=2/3/4"));

    // 9 out of sync, 0 and 5 in sync
    EXPECT_EQ("[Setting node 0 as active: copy has 3 docs]"
              "[Setting node 5 as active: copy has 3 docs]",
              testBucketStatePerGroup("0=2/3/4, 5=2/3/4, 9=5/3/4"));

    // If there's no majority, we activate everything because there's really nothing
    // better we can do.
    EXPECT_EQ("[Setting node 0 as active: copy has 3 docs]"
              "[Setting node 5 as active: copy has 6 docs]"
              "[Setting node 9 as active: copy has 9 docs and ideal state priority 2]",
              testBucketStatePerGroup("0=2/3/4, 5=5/6/7, 9=8/9/10"));

    // However, if a replica is _already_ active, we will not deactivate it.
    EXPECT_EQ("[Setting node 0 as active: copy has 3 docs]"
              "[Setting node 9 as active: copy has 3 docs and ideal state priority 2]",
              testBucketStatePerGroup("0=2/3/4, 5=3/4/5/u/a, 9=2/3/4"));
}

TEST_F(StateCheckersTest, replica_activation_inhibition_can_be_limited_to_max_n_groups) {
    // groups: [0, 1, 3] [5, 6, 8] [9, 10, 11]
    setupDistributor(6, 12, "distributor:1 storage:12 .2.s:d .4.s:d .7.s:d");
    triggerDistributionChange(make_3x3_group_config());
    getConfig().set_max_activation_inhibited_out_of_sync_groups(1);

    // We count metadata majorities independent of groups. Let there be 3 in-sync replicas in
    // group 0, 1 out of sync in group 1 and 1 out of sync in group 2. Unless we have
    // mechanisms in place to limit the number of affected groups, both groups 1 and 2 would
    // be inhibited for activation. Since we limit to 1, only group 1 should be affected.
    EXPECT_EQ("[Setting node 1 as active: copy has 3 docs and ideal state priority 4]"
              "[Setting node 9 as active: copy has 6 docs and ideal state priority 2]",
              testBucketStatePerGroup("0=2/3/4, 1=2/3/4, 3=2/3/4, 5=3/4/5, 9=5/6/7"));
}

TEST_F(StateCheckersTest, activate_replicas_that_are_out_of_sync_with_majority_if_inhibition_config_disabled) {
    // groups: [0, 1, 3] [5, 6, 8] [9, 10, 11]
    setupDistributor(6, 12, "distributor:1 storage:12 .2.s:d .4.s:d .7.s:d");
    triggerDistributionChange(make_3x3_group_config());
    getConfig().set_max_activation_inhibited_out_of_sync_groups(0);

    // 5 is out of sync with 0 and 9 but will still be activated since the config is false.
    EXPECT_EQ("[Setting node 0 as active: copy has 3 docs]"
              "[Setting node 5 as active: copy has 4 docs]"
              "[Setting node 9 as active: copy has 3 docs and ideal state priority 2]",
              testBucketStatePerGroup("0=2/3/4, 5=3/4/5, 9=2/3/4"));
}

TEST_F(StateCheckersTest, allow_activation_of_retired_nodes) {
    // All nodes in retired state implies that the ideal state is empty. But
    // we still want to be able to shuffle bucket activations around in order
    // to preserve coverage.
    setupDistributor(2, 2, "distributor:1 storage:2 .0.s:r .1.s:r");
    EXPECT_EQ("[Setting node 1 as active: copy has 6 docs]"
              "[Setting node 0 as inactive]",
              testBucketState("0=2/3/4/u/a,1=5/6/7/t"));
}

TEST_F(StateCheckersTest, inhibit_bucket_activation_if_disabled_in_config) {
    setupDistributor(2, 4, "distributor:1 storage:4");
    disableBucketActivationInConfig(true);

    // Node 1 is in ideal state and only replica and should be activated in
    // an indexed cluster context (but not here).
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testBucketState("1=2/3/4", 2, true));
}

TEST_F(StateCheckersTest, inhibit_bucket_deactivation_if_disabled_in_config) {
    setupDistributor(2, 4, "distributor:1 storage:4");
    disableBucketActivationInConfig(true);

    // Multiple replicas which would have been deactivated. This test is mostly
    // for the sake of completion; a scenario where buckets are active while
    // having no indexed documents configured should not happen.
    EXPECT_EQ("NO OPERATIONS GENERATED",
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
    getConfig().setGarbageCollection("music", std::chrono::seconds(checkInterval));
    getConfig().setLastGarbageCollectionChangeTime(vespalib::steady_time(std::chrono::seconds(lastChangeTime)));
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker,
                            makeDocumentBucket(e.getBucketId()));
    getClock().setAbsoluteTimeInSeconds(nowTimestamp);
    return testStateChecker(checker, c, false, PendingMessage(),
                            includePriority, includeSchedulingPri);
}

TEST_F(StateCheckersTest, garbage_collection) {
    // BucketId(17, 0) has id (and thus 'hash') 0x4400000000000000. With a
    // check interval modulo of 3600, this implies a start point of 848.

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testGarbageCollection(900, 3600 + 847, 3600));

    EXPECT_EQ("[Needs garbage collection: Last check at 900, current time 4448, "
              "configured interval 3600]",
              testGarbageCollection(900, 3600 + 848, 3600));

    EXPECT_EQ("[Needs garbage collection: Last check at 3, current time 4000, "
              "configured interval 3600]",
              testGarbageCollection(3, 4000, 3600));

    // GC start point 3648.
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testGarbageCollection(3, 3647, 8000));

    EXPECT_EQ("[Needs garbage collection: Last check at 3, current time 4000, "
              "configured interval 3600]",
              testGarbageCollection(3, 4000, 3600));

    // GC explicitly disabled.
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testGarbageCollection(3, 4000, 0));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testGarbageCollection(3, 3, 1));

    EXPECT_EQ("[Needs garbage collection: Last check at 3, current time 4000, "
              "configured interval 300] (pri 200)",
              testGarbageCollection(3, 4000, 300, 1, true));

    EXPECT_EQ("NO OPERATIONS GENERATED",
              testGarbageCollection(3850, 4000, 300, 1));
}

TEST_F(StateCheckersTest, gc_ops_are_prioritized_with_low_priority_category) {
    EXPECT_EQ("[Needs garbage collection: Last check at 3, current time 4000, "
              "configured interval 300] (scheduling pri VERY_LOW)",
              testGarbageCollection(3, 4000, 300, 1, false, true));
}

/**
 * When a node is in maintenance, we want to do our best to avoid any unneeded
 * changes to the bucket replicas' states, as this will require re-syncing of
 * the replicas when the node out of maintenance. Consequently we should not
 * trigger GC for buckets when this is the case.
 */
TEST_F(StateCheckersTest, gc_inhibited_when_ideal_node_in_maintenance) {
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
    getConfig().setGarbageCollection("music", 3600s);
    getConfig().setLastGarbageCollectionChangeTime(vespalib::steady_time(vespalib::duration::zero()));
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker,
                            makeDocumentBucket(bucket));
    getClock().setAbsoluteTimeInSeconds(4000);
    // Would normally (in a non-maintenance case) trigger GC due to having
    // overshot the GC check cycle.
    auto result = testStateChecker(checker, c, false, PendingMessage(), false);

    EXPECT_EQ("NO OPERATIONS GENERATED", result);
}

/*
 * Bug 6656726, comment #25. Merge state checker does not execute if an ideal
 * node is in maintenance, so for symmetry we need to do the same for deletes
 * (it's bad mojo to potentially delete something that would've been merged
 * had it not been for a node being in maintenance).
 */
TEST_F(StateCheckersTest, no_remove_when_ideal_node_in_maintenance) {
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testDeleteExtraCopies("0=10/100/1/true,"
                                    "1=10/100/1/true,"
                                    "2=10/100/1/true",
                                    2, PendingMessage(),
                                    "distributor:1 storage:3 .1.s:m"))
              << "Do not remove when ideal node is in maintenance mode";
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
TEST_F(StateCheckersTest, stepwise_join_for_small_buckets_without_siblings) {
    setupDistributor(3, 10, "distributor:1 storage:2 bits:1");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);
    // Buckets without siblings but that should be step-wise joined back
    // into bucket (2, 1).
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    EXPECT_EQ("BucketId(0x0800000000000001): "
              "[Joining buckets BucketId(0x0c00000000000001) and "
              "BucketId(0x0c00000000000001) because their size "
              "(1 bytes, 1 docs) is less than the configured limit "
              "of (100, 10)",
              testJoin(10, 100, 2, document::BucketId(3, 1)));

    // Other bucket should be joined as well. Together the two join targets
    // will transform into a mighty sibling pair that can rule the galaxy
    // (and also be joined together afterwards)!
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    EXPECT_EQ("BucketId(0x0800000000000003): "
              "[Joining buckets BucketId(0x0c00000000000003) and "
              "BucketId(0x0c00000000000003) because their size "
              "(1 bytes, 1 docs) is less than the configured limit "
              "of (100, 10)",
              testJoin(10, 100, 2, document::BucketId(3, 0x3)));
}

TEST_F(StateCheckersTest, no_stepwise_join_when_disabled_through_config) {
    setupDistributor(3, 10, "distributor:1 storage:2 bits:1");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = false;
    getConfig().configure(config);

    // Buckets without siblings but that would have been step-wise joined back
    // into bucket 1 if it had been config-enabled.
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 1, 1);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 1, document::BucketId(3, 1)));
}

TEST_F(StateCheckersTest, no_stepwise_join_when_single_sibling_too_large) {
    setupDistributor(3, 10, "distributor:1 storage:2 bits:1");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);

    // Bucket is exactly at the boundary where it's too big.
    insertBucketInfo(document::BucketId(3, 1), 1, 0x1, 10, 100);
    insertBucketInfo(document::BucketId(3, 0x3), 1, 0x1, 1, 1);
    EXPECT_EQ("NO OPERATIONS GENERATED",
              testJoin(10, 100, 1, document::BucketId(3, 1)));
}

TEST_F(StateCheckersTest, stepwise_join_may_skip_multiple_bits_when_consistent) {
    setupDistributor(2, 10, "distributor:1 storage:2 bits:8");
    vespa::config::content::core::StorDistributormanagerConfigBuilder config;
    config.enableJoinForSiblingLessBuckets = true;
    getConfig().configure(config);

    insertBucketInfo(document::BucketId(16, 1), 1, 0x1, 1, 1);
    // No buckets further up in the tree, can join up to the distribution bit
    // limit at 8.
    EXPECT_EQ("BucketId(0x2000000000000001): "
              "[Joining buckets BucketId(0x4000000000000001) and "
              "BucketId(0x4000000000000001) because their size "
              "(1 bytes, 1 docs) is less than the configured limit "
              "of (100, 10)",
              testJoin(10, 100, 8, document::BucketId(16, 1)));
}

TEST_F(StateCheckersTest, stepwise_join_does_not_skip_beyond_level_with_sibling) {
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
    EXPECT_EQ("BucketId(0x2c00000000000000): "
              "[Joining buckets BucketId(0x4000000000000000) and "
              "BucketId(0x4000000000000000) because their size "
              "(1 bytes, 1 docs) is less than the configured limit "
              "of (100, 10)",
              testJoin(10, 100, 8, document::BucketId(16, 0)));
}

TEST_F(StateCheckersTest, join_can_be_scheduled_when_replicas_on_retired_nodes) {
    setupDistributor(1, 1, "distributor:1 storage:1 .0.s.:r");
    insertJoinableBuckets();
    EXPECT_EQ("BucketId(0x8000000000000001): "
              "[Joining buckets BucketId(0x8400000000000001) and "
              "BucketId(0x8400000100000001) because their size "
              "(2 bytes, 2 docs) is less than the configured limit "
              "of (100, 10)",
              testJoin(10, 100, 16, document::BucketId(33, 1)));
}

TEST_F(StateCheckersTest, context_populates_ideal_state_containers) {
    // 1 and 3 are ideal nodes for bucket {17, 0}
    setupDistributor(2, 100, "distributor:1 storage:4");

    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(distributor_component(), getDistributorBucketSpace(), statsTracker, makeDocumentBucket({17, 0}));

    ASSERT_THAT(c.idealState, ElementsAre(1, 3));
    // TODO replace with UnorderedElementsAre once we can build gmock without issues
    std::vector<uint16_t> ideal_state(c.unorderedIdealState.begin(), c.unorderedIdealState.end());
    std::sort(ideal_state.begin(), ideal_state.end());
    ASSERT_THAT(ideal_state, ElementsAre(1, 3));
}

namespace {

template <typename Checker>
class StateCheckerRunner
{
    StateCheckersTest& _fixture;
    NodeMaintenanceStatsTracker _statsTracker;
    std::string _result;
public:
    explicit StateCheckerRunner(StateCheckersTest& fixture);
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
        StateChecker::Context c(_fixture.distributor_component(), _fixture.getDistributorBucketSpace(), _statsTracker, makeDocumentBucket(bid));
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
StateCheckerRunner<Checker>::~StateCheckerRunner() = default;

} // anon ns

TEST_F(StateCheckersTest, stats_updated_when_merging_due_to_move) {
    StateCheckerRunner<SynchronizeAndMoveStateChecker> runner(*this);
    // Ideal state for bucket {17,0} in given cluster state is [1, 3]
    runner.addToDb({17, 0}, "0=1,1=1,2=1")
          .clusterState("distributor:1 storage:4")
          .runFor({17, 0});
    // Node 1 treated as copy source, but not as move source.
    {
        NodeMaintenanceStats wanted;
        wanted.copyingOut = 1;
        EXPECT_EQ(wanted, runner.stats().forNode(1, makeBucketSpace()));
    }
    // Moving 1 bucket from nodes {0, 2} into 3.
    // Note that we do not at this point in time distinguish _which_ of these
    // will do the actual data movement to node 3.
    {
        NodeMaintenanceStats wanted;
        wanted.copyingIn = 1;
        EXPECT_EQ(wanted, runner.stats().forNode(3, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.movingOut = 1;
        EXPECT_EQ(wanted, runner.stats().forNode(0, makeBucketSpace()));
        EXPECT_EQ(wanted, runner.stats().forNode(2, makeBucketSpace()));
    }
}

TEST_F(StateCheckersTest, stats_updated_when_merging_due_to_missing_copy) {
    StateCheckerRunner<SynchronizeAndMoveStateChecker> runner(*this);
    // Ideal state for bucket {17,0} in given cluster state is [1, 3]
    runner.addToDb({17, 0}, "1=1")
          .clusterState("distributor:1 storage:4")
          .runFor({17, 0});

    {
        NodeMaintenanceStats wanted;
        wanted.copyingIn = 1;
        EXPECT_EQ(wanted, runner.stats().forNode(3, makeBucketSpace()));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.copyingOut = 1;
        EXPECT_EQ(wanted, runner.stats().forNode(1, makeBucketSpace()));
    }
}

TEST_F(StateCheckersTest, stats_updated_when_merging_due_to_out_of_sync_copies) {
    StateCheckerRunner<SynchronizeAndMoveStateChecker> runner(*this);
    runner.addToDb({17, 0}, "1=1,3=2")
          .clusterState("distributor:1 storage:4")
          .runFor({17, 0});
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        EXPECT_EQ(wanted, runner.stats().forNode(1, makeBucketSpace()));
        EXPECT_EQ(wanted, runner.stats().forNode(3, makeBucketSpace()));
    }
}

}
