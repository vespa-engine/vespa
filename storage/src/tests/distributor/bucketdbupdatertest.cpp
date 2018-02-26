// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <iomanip>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/pending_bucket_space_db_transition.h>
#include <vespa/storage/distributor/outdated_nodes_map.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storage/storageutil/distributorstatecache.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/simpleclusterinformation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <sstream>

using namespace storage::api;
using namespace storage::lib;
using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using document::BucketSpace;
using document::FixedBucketSpaces;

namespace storage::distributor {

namespace {

std::string
getStringList(std::string s, uint32_t count)
{
    std::ostringstream ost;
    for (uint32_t i = 0; i < count; ++i) {
        if (i > 0) {
            ost << ",";
        }
        ost << s;
    }
   return ost.str();
}

std::string
getRequestBucketInfoStrings(uint32_t count)
{
    return getStringList("Request bucket info", count);
}

}

class BucketDBUpdaterTest : public CppUnit::TestFixture,
                            public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(BucketDBUpdaterTest);
    CPPUNIT_TEST(testNormalUsage); // Make sure that bucketdbupdater sends requests to nodes, send responses back for 3 nodes, check that bucketdb is in correct state
    CPPUNIT_TEST(testDistributorChange);
    CPPUNIT_TEST(testDistributorChangeWithGrouping);
    CPPUNIT_TEST(testNormalUsageInitializing); // Check that we send request bucket info when storage node is initializing, and send another when it's up.
    CPPUNIT_TEST(testFailedRequestBucketInfo);
    CPPUNIT_TEST(testEncodeErrorHandling);
    CPPUNIT_TEST(testBitChange); // Check what happens when distribution bits change
    CPPUNIT_TEST(testNodeDown);
    CPPUNIT_TEST(testStorageNodeInMaintenanceClearsBucketsForNode);
    CPPUNIT_TEST(testNodeDownCopiesGetInSync);
    CPPUNIT_TEST(testDownWhileInit);
    CPPUNIT_TEST(testInitializingWhileRecheck);
    CPPUNIT_TEST(testRecheckNode);
    CPPUNIT_TEST(testRecheckNodeWithFailure);
    CPPUNIT_TEST(testNotifyBucketChange);
    CPPUNIT_TEST(testNotifyBucketChangeFromNodeDown);
    CPPUNIT_TEST(testNotifyChangeWithPendingStateQueuesBucketInfoRequests);
    CPPUNIT_TEST(testMergeReply);
    CPPUNIT_TEST(testMergeReplyNodeDown);
    CPPUNIT_TEST(testMergeReplyNodeDownAfterRequestSent);
    CPPUNIT_TEST(testFlush);
    CPPUNIT_TEST(testPendingClusterStateSendMessages);
    CPPUNIT_TEST(testPendingClusterStateReceive);
    CPPUNIT_TEST(testPendingClusterStateMerge);
    CPPUNIT_TEST(testPendingClusterStateMergeReplicaChanged);
    CPPUNIT_TEST(testPendingClusterStateWithGroupDown);
    CPPUNIT_TEST(testPendingClusterStateWithGroupDownAndNoHandover);
    CPPUNIT_TEST(testNoDbResurrectionForBucketNotOwnedInCurrentState);
    CPPUNIT_TEST(testNoDbResurrectionForBucketNotOwnedInPendingState);
    CPPUNIT_TEST(testClusterStateAlwaysSendsFullFetchWhenDistributionChangePending);
    CPPUNIT_TEST(testChangedDistributionConfigTriggersRecoveryMode);
    CPPUNIT_TEST(testNewlyAddedBucketsHaveCurrentTimeAsGcTimestamp);
    CPPUNIT_TEST(testNewerMutationsNotOverwrittenByEarlierBucketFetch);
    CPPUNIT_TEST(preemptedDistrChangeCarriesNodeSetOverToNextStateFetch);
    CPPUNIT_TEST(preemptedStorChangeCarriesNodeSetOverToNextStateFetch);
    CPPUNIT_TEST(preemptedStorageNodeDownMustBeReFetched);
    CPPUNIT_TEST(outdatedNodeSetClearedAfterSuccessfulStateCompletion);
    CPPUNIT_TEST(doNotSendToPreemptedNodeNowInDownState);
    CPPUNIT_TEST(doNotSendToPreemptedNodeNotPartOfNewState);
    CPPUNIT_TEST_DISABLED(clusterConfigDownsizeOnlySendsToAvailableNodes);
    CPPUNIT_TEST(changedDiskSetTriggersReFetch);
    CPPUNIT_TEST(nodeMissingFromConfigIsTreatedAsNeedingOwnershipTransfer);
    CPPUNIT_TEST(changed_distributor_set_implies_ownership_transfer);
    CPPUNIT_TEST(unchanged_distributor_set_implies_no_ownership_transfer);
    CPPUNIT_TEST(changed_distribution_config_implies_ownership_transfer);
    CPPUNIT_TEST(transition_time_tracked_for_single_state_change);
    CPPUNIT_TEST(transition_time_reset_across_non_preempting_state_changes);
    CPPUNIT_TEST(transition_time_tracked_for_distribution_config_change);
    CPPUNIT_TEST(transition_time_tracked_across_preempted_transitions);
    CPPUNIT_TEST(batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted);
    CPPUNIT_TEST(batch_add_of_new_diverging_replicas_does_not_mark_any_as_trusted);
    CPPUNIT_TEST(batch_add_with_single_resulting_replica_implicitly_marks_as_trusted);
    CPPUNIT_TEST(identity_update_of_single_replica_does_not_clear_trusted);
    CPPUNIT_TEST(identity_update_of_diverging_untrusted_replicas_does_not_mark_any_as_trusted);
    CPPUNIT_TEST(adding_diverging_replica_to_existing_trusted_does_not_remove_trusted);
    CPPUNIT_TEST(batch_update_from_distributor_change_does_not_mark_diverging_replicas_as_trusted);
    CPPUNIT_TEST_SUITE_END();

public:
    BucketDBUpdaterTest();

protected:
    void testNormalUsage();
    void testDistributorChange();
    void testDistributorChangeWithGrouping();
    void testNormalUsageInitializing();
    void testFailedRequestBucketInfo();
    void testEncodeErrorHandling();
    void testNoResponses();
    void testBitChange();
    void testInconsistentChecksum();
    void testAddEmptyNode();
    void testNodeDown();
    void testStorageNodeInMaintenanceClearsBucketsForNode();
    void testNodeDownCopiesGetInSync();
    void testDownWhileInit();
    void testInitializingWhileRecheck();
    void testRecheckNode();
    void testRecheckNodeWithFailure();
    void testNotifyBucketChange();
    void testNotifyBucketChangeFromNodeDown();
    void testNotifyChangeWithPendingStateQueuesBucketInfoRequests();
    void testMergeReply();
    void testMergeReplyNodeDown();
    void testMergeReplyNodeDownAfterRequestSent();
    void testFlush();
    void testPendingClusterStateSendMessages();
    void testPendingClusterStateReceive();
    void testPendingClusterStateMerge();
    void testPendingClusterStateMergeReplicaChanged();
    void testPendingClusterStateWithGroupDown();
    void testPendingClusterStateWithGroupDownAndNoHandover();
    void testNoDbResurrectionForBucketNotOwnedInCurrentState();
    void testNoDbResurrectionForBucketNotOwnedInPendingState();
    void testClusterStateAlwaysSendsFullFetchWhenDistributionChangePending();
    void testChangedDistributionConfigTriggersRecoveryMode();
    void testNewlyAddedBucketsHaveCurrentTimeAsGcTimestamp();
    void testNewerMutationsNotOverwrittenByEarlierBucketFetch();
    void preemptedDistrChangeCarriesNodeSetOverToNextStateFetch();
    void preemptedStorChangeCarriesNodeSetOverToNextStateFetch();
    void preemptedStorageNodeDownMustBeReFetched();
    void outdatedNodeSetClearedAfterSuccessfulStateCompletion();
    void doNotSendToPreemptedNodeNowInDownState();
    void doNotSendToPreemptedNodeNotPartOfNewState();
    void clusterConfigDownsizeOnlySendsToAvailableNodes();
    void changedDiskSetTriggersReFetch();
    void nodeMissingFromConfigIsTreatedAsNeedingOwnershipTransfer();
    void changed_distributor_set_implies_ownership_transfer();
    void unchanged_distributor_set_implies_no_ownership_transfer();
    void changed_distribution_config_implies_ownership_transfer();
    void transition_time_tracked_for_single_state_change();
    void transition_time_reset_across_non_preempting_state_changes();
    void transition_time_tracked_for_distribution_config_change();
    void transition_time_tracked_across_preempted_transitions();
    void batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted();
    void batch_add_of_new_diverging_replicas_does_not_mark_any_as_trusted();
    void batch_add_with_single_resulting_replica_implicitly_marks_as_trusted();
    void identity_update_of_single_replica_does_not_clear_trusted();
    void identity_update_of_diverging_untrusted_replicas_does_not_mark_any_as_trusted();
    void adding_diverging_replica_to_existing_trusted_does_not_remove_trusted();
    void batch_update_from_distributor_change_does_not_mark_diverging_replicas_as_trusted();

    auto &defaultDistributorBucketSpace() { return getBucketSpaceRepo().get(makeBucketSpace()); }

    bool bucketExistsThatHasNode(int bucketCount, uint16_t node) const;

    ClusterInformation::CSP createClusterInfo(const std::string& clusterState) {
        ClusterInformation::CSP clusterInfo(
                new SimpleClusterInformation(
                        getBucketDBUpdater().getDistributorComponent().getIndex(),
                        lib::ClusterState(clusterState),
                        "ui"));
        return clusterInfo;
    }

    static std::string getNodeList(std::vector<uint16_t> nodes, size_t count);

    std::string getNodeList(std::vector<uint16_t> nodes);

    std::vector<uint16_t>
    expandNodeVec(const std::vector<uint16_t> &nodes);

    std::vector<document::BucketSpace> _bucketSpaces;

    size_t messageCount(size_t messagesPerBucketSpace) const {
        return messagesPerBucketSpace * _bucketSpaces.size();
    }

public:
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;
    void setUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
    };

    void tearDown() override {
        close();
    }

    std::shared_ptr<RequestBucketInfoReply> getFakeBucketReply(
            const lib::ClusterState& state,
            const RequestBucketInfoCommand& cmd,
            int storageIndex,
            uint32_t bucketCount,
            uint32_t invalidBucketCount = 0)
    {
        RequestBucketInfoReply* sreply = new RequestBucketInfoReply(cmd);
        sreply->setAddress(storageAddress(storageIndex));

        api::RequestBucketInfoReply::EntryVector &vec = sreply->getBucketInfo();

        for (uint32_t i=0; i<bucketCount + invalidBucketCount; i++) {
            if (!getBucketDBUpdater().getDistributorComponent()
                .ownsBucketInState(state, makeDocumentBucket(document::BucketId(16, i)))) {
                continue;
            }

            std::vector<uint16_t> nodes;
            defaultDistributorBucketSpace().getDistribution().getIdealNodes(
                    lib::NodeType::STORAGE,
                    state,
                    document::BucketId(16, i),
                    nodes);

            for (uint32_t j=0; j<nodes.size(); j++) {
                if (nodes[j] == storageIndex) {
                    if (i >= bucketCount) {
                        vec.push_back(api::RequestBucketInfoReply::Entry(
                                              document::BucketId(16, i),
                                              api::BucketInfo()));
                    } else {
                        vec.push_back(api::RequestBucketInfoReply::Entry(
                                              document::BucketId(16, i),
                                              api::BucketInfo(10,1,1)));
                    }
                }
            }
        }

        return std::shared_ptr<api::RequestBucketInfoReply>(sreply);
    }

    void fakeBucketReply(const lib::ClusterState &state,
                         const api::StorageCommand &cmd,
                         uint32_t bucketCount,
                         uint32_t invalidBucketCount = 0)
    {
        CPPUNIT_ASSERT(cmd.getType() == MessageType::REQUESTBUCKETINFO);
        const api::StorageMessageAddress &address(*cmd.getAddress());
        getBucketDBUpdater().onRequestBucketInfoReply(
                getFakeBucketReply(state,
                                   dynamic_cast<const RequestBucketInfoCommand &>(cmd),
                                   address.getIndex(),
                                   bucketCount,
                                   invalidBucketCount));
    }

    void sendFakeReplyForSingleBucketRequest(
            const api::RequestBucketInfoCommand& rbi)
    {
        CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
        const document::BucketId& bucket(rbi.getBuckets()[0]);

        std::shared_ptr<api::RequestBucketInfoReply> reply(
                new api::RequestBucketInfoReply(rbi));
        reply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(bucket,
                        api::BucketInfo(20, 10, 12, 50, 60, true, true)));
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
    }

    std::string verifyBucket(document::BucketId id, const lib::ClusterState& state) {
        BucketDatabase::Entry entry = getBucketDatabase().get(id);
        if (!entry.valid()) {
            return vespalib::make_string("%s doesn't exist in DB",
                                         id.toString().c_str());
        }

        std::vector<uint16_t> nodes;
        defaultDistributorBucketSpace().getDistribution().getIdealNodes(
                lib::NodeType::STORAGE,
                state,
                document::BucketId(id),
                nodes);

        if (nodes.size() != entry->getNodeCount()) {
            return vespalib::make_string("Bucket Id %s has %d nodes in "
                                         "ideal state, but has only %d in DB",
                                         id.toString().c_str(),
                                         (int)nodes.size(),
                                         (int)entry->getNodeCount());
        }

        for (uint32_t i = 0; i<nodes.size(); i++) {
            bool found = false;

            for (uint32_t j = 0; j<entry->getNodeCount(); j++) {
                if (nodes[i] == entry->getNodeRef(j).getNode()) {
                    found = true;
                }
            }

            if (!found) {
                return vespalib::make_string(
                        "Bucket Id %s has no copy from node %d",
                        id.toString().c_str(),
                        nodes[i]);
            }
        }

        return "";
    }


    void verifyInvalid(document::BucketId id, int storageNode) {
        BucketDatabase::Entry entry = getBucketDatabase().get(id);

        CPPUNIT_ASSERT(entry.valid());

        bool found = false;
        for (uint32_t j = 0; j<entry->getNodeCount(); j++) {
            if (entry->getNodeRef(j).getNode() == storageNode) {
                CPPUNIT_ASSERT(!entry->getNodeRef(j).valid());
                found = true;
            }
        }

        CPPUNIT_ASSERT(found);
    }

    struct OrderByIncreasingNodeIndex {
        template <typename T>
        bool operator()(const T& lhs, const T& rhs) {
            return (lhs->getAddress()->getIndex()
                    < rhs->getAddress()->getIndex());
        }
    };

    void sortSentMessagesByIndex(MessageSenderStub& sender,
                                 size_t sortFromOffset = 0)
    {
        std::sort(sender.commands.begin() + sortFromOffset,
                  sender.commands.end(),
                  OrderByIncreasingNodeIndex());
    }

    void setSystemState(const lib::ClusterState& state) {
        const size_t sizeBeforeState = _sender.commands.size();
        getBucketDBUpdater().onSetSystemState(
                std::shared_ptr<api::SetSystemStateCommand>(
                        new api::SetSystemStateCommand(state)));
        // A lot of test logic has the assumption that all messages sent as a
        // result of cluster state changes will be in increasing index order
        // (for simplicity, not because this is required for correctness).
        // Only sort the messages that arrived as a result of the state, don't
        // jumble the sorting with any existing messages.
        sortSentMessagesByIndex(_sender, sizeBeforeState);
    }

    void completeBucketInfoGathering(const lib::ClusterState& state,
                                     size_t expectedMsgs,
                                     uint32_t bucketCount = 1,
                                     uint32_t invalidBucketCount = 0)
    {
        CPPUNIT_ASSERT_EQUAL(expectedMsgs, _sender.commands.size());

        for (uint32_t i = 0; i < _sender.commands.size(); i++) {
            fakeBucketReply(state, *_sender.commands[i],
                            bucketCount, invalidBucketCount);
        }
    }

    void setAndEnableClusterState(const lib::ClusterState& state,
                                  uint32_t expectedMsgs,
                                  uint32_t nBuckets)
    {
        _sender.clear();
        setSystemState(state);
        completeBucketInfoGathering(state, expectedMsgs, nBuckets);
    }

    void completeStateTransitionInSeconds(const std::string& stateStr,
                                          uint32_t seconds,
                                          uint32_t expectedMsgs)
    {
        _sender.clear();
        lib::ClusterState state(stateStr);
        setSystemState(state);
        getClock().addSecondsToTime(seconds);
        completeBucketInfoGathering(state, expectedMsgs);
    }

    uint64_t lastTransitionTimeInMillis() {
        return uint64_t(getDistributor().getMetrics().stateTransitionTime.getLast());
    }

    void setStorageNodes(uint32_t numStorageNodes) {
        _sender.clear();

       lib::ClusterState newState(
                vespalib::make_string("distributor:1 storage:%d", numStorageNodes));

        setSystemState(newState);

        for (uint32_t i=0; i< messageCount(numStorageNodes); i++) {
            CPPUNIT_ASSERT(_sender.commands[i]->getType() ==
                           MessageType::REQUESTBUCKETINFO);

            const api::StorageMessageAddress *address = _sender.commands[i]->getAddress();
            CPPUNIT_ASSERT_EQUAL((uint32_t)(i / _bucketSpaces.size()), (uint32_t)address->getIndex());
        }
    }

    void initializeNodesAndBuckets(uint32_t numStorageNodes,
                                   uint32_t numBuckets)
    {
        setStorageNodes(numStorageNodes);

        vespalib::string state(vespalib::make_string(
                "distributor:1 storage:%d", numStorageNodes));
        lib::ClusterState newState(state);

        for (uint32_t i=0; i< messageCount(numStorageNodes); i++) {
            fakeBucketReply(newState, *_sender.commands[i], numBuckets);
        }
        assertCorrectBuckets(numBuckets, state);
    }

    bool bucketHasNode(document::BucketId id, uint16_t node) const {
        BucketDatabase::Entry entry = getBucket(id);
        CPPUNIT_ASSERT(entry.valid());

        for (uint32_t j=0; j<entry->getNodeCount(); j++) {
            if (entry->getNodeRef(j).getNode() == node) {
                return true;
            }
        }

        return false;
    }

    api::StorageMessageAddress storageAddress(uint16_t node) {
        return api::StorageMessageAddress("storage", lib::NodeType::STORAGE, node);
    }

    std::string getSentNodes(const std::string& oldClusterState,
                             const std::string& newClusterState);

    std::string getSentNodesDistributionChanged(
            const std::string& oldClusterState);

    std::vector<uint16_t> getSentNodesWithPreemption(
            const std::string& oldClusterState,
            uint32_t expectedOldStateMessages,
            const std::string& preemptedClusterState,
            const std::string& newClusterState);

    std::vector<uint16_t> getSendSet() const;

    std::string mergeBucketLists(
            const lib::ClusterState& oldState,
            const std::string& existingData,
            const lib::ClusterState& newState,
            const std::string& newData,
            bool includeBucketInfo = false);

    std::string mergeBucketLists(
            const std::string& existingData,
            const std::string& newData,
            bool includeBucketInfo = false);

    void assertCorrectBuckets(int numBuckets, const std::string& stateStr) {
        lib::ClusterState state(stateStr);
        for (int i=0; i<numBuckets; i++) {
            CPPUNIT_ASSERT_EQUAL(
                    getIdealStr(document::BucketId(16, i), state),
                    getNodes(document::BucketId(16, i)));
        }
    }

    void setDistribution(const std::string& distConfig) {
        triggerDistributionChange(
                std::make_shared<lib::Distribution>(distConfig));
    }

    std::string getDistConfig6Nodes3Groups() const {
        return ("redundancy 2\n"
                "group[3]\n"
                "group[0].name \"invalid\"\n"
                "group[0].index \"invalid\"\n"
                "group[0].partitions 1|*\n"
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

    std::string getDistConfig6Nodes4Groups() const {
        return ("redundancy 2\n"
                "group[4]\n"
                "group[0].name \"invalid\"\n"
                "group[0].index \"invalid\"\n"
                "group[0].partitions 1|*\n"
                "group[0].nodes[0]\n"
                "group[1].name rack0\n"
                "group[1].index 0\n"
                "group[1].nodes[2]\n"
                "group[1].nodes[0].index 0\n"
                "group[1].nodes[1].index 1\n"
                "group[2].name rack1\n"
                "group[2].index 1\n"
                "group[2].nodes[2]\n"
                "group[2].nodes[0].index 2\n"
                "group[2].nodes[1].index 3\n"
                "group[3].name rack2\n"
                "group[3].index 2\n"
                "group[3].nodes[2]\n"
                "group[3].nodes[0].index 4\n"
                "group[3].nodes[1].index 5\n");
    }

    std::string getDistConfig3Nodes1Group() const {
        return ("redundancy 2\n"
                "group[2]\n"
                "group[0].name \"invalid\"\n"
                "group[0].index \"invalid\"\n"
                "group[0].partitions 1|*\n"
                "group[0].nodes[0]\n"
                "group[1].name rack0\n"
                "group[1].index 0\n"
                "group[1].nodes[3]\n"
                "group[1].nodes[0].index 0\n"
                "group[1].nodes[1].index 1\n"
                "group[1].nodes[2].index 2\n");
    }

    struct PendingClusterStateFixture {
        MessageSenderStub sender;
        framework::defaultimplementation::FakeClock clock;
        std::unique_ptr<PendingClusterState> state;

        PendingClusterStateFixture(
                BucketDBUpdaterTest& owner,
                const std::string& oldClusterState,
                const std::string& newClusterState)
        {
            std::shared_ptr<api::SetSystemStateCommand> cmd(
                    new api::SetSystemStateCommand(
                        lib::ClusterState(newClusterState)));

            ClusterInformation::CSP clusterInfo(
                    owner.createClusterInfo(oldClusterState));

            OutdatedNodesMap outdatedNodesMap;
            state = PendingClusterState::createForClusterStateChange(
                    clock, clusterInfo, sender, owner.getBucketSpaceRepo(), cmd, outdatedNodesMap,
                    api::Timestamp(1));
        }

        PendingClusterStateFixture(
                BucketDBUpdaterTest& owner,
                const std::string& oldClusterState)
        {
            ClusterInformation::CSP clusterInfo(
                    owner.createClusterInfo(oldClusterState));

            state = PendingClusterState::createForDistributionChange(
                    clock, clusterInfo, sender, owner.getBucketSpaceRepo(), api::Timestamp(1));
        }
    };

    auto createPendingStateFixtureForStateChange(
            const std::string& oldClusterState,
            const std::string& newClusterState)
    {
        return std::make_unique<PendingClusterStateFixture>(
                *this, oldClusterState, newClusterState);
    }

    auto createPendingStateFixtureForDistributionChange(
            const std::string& oldClusterState)
    {
        return std::make_unique<PendingClusterStateFixture>(
                *this, oldClusterState);
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketDBUpdaterTest);

BucketDBUpdaterTest::BucketDBUpdaterTest()
    : CppUnit::TestFixture(),
    DistributorTestUtil(),
    _bucketSpaces()
{
}

void
BucketDBUpdaterTest::testNormalUsage()
{
    setSystemState(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"));

    CPPUNIT_ASSERT_EQUAL(messageCount(3), _sender.commands.size());

    // Ensure distribution hash is set correctly
    CPPUNIT_ASSERT_EQUAL(
            defaultDistributorBucketSpace().getDistribution()
            .getNodeGraph().getDistributionConfigHash(),
            dynamic_cast<const RequestBucketInfoCommand&>(
                    *_sender.commands[0]).getDistributionHash());

    fakeBucketReply(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
                    *_sender.commands[0], 10);

    _sender.clear();

    // Optimization for not refetching unneeded data after cluster state
    // change is only implemented after completion of previous cluster state
    setSystemState(lib::ClusterState("distributor:2 .0.s:i storage:3"));

    CPPUNIT_ASSERT_EQUAL(messageCount(3), _sender.commands.size());
    // Expect reply of first set SystemState request.
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size());

    completeBucketInfoGathering(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
                                messageCount(3), 10);
    assertCorrectBuckets(10, "distributor:2 storage:3");
}

void
BucketDBUpdaterTest::testDistributorChange()
{
    int numBuckets = 100;

    // First sends request
    setSystemState(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"));
    CPPUNIT_ASSERT_EQUAL(messageCount(3), _sender.commands.size());
    completeBucketInfoGathering(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
                                messageCount(3), numBuckets);
    _sender.clear();

    // No change from initializing to up (when done with last job)
    setSystemState(lib::ClusterState("distributor:2 storage:3"));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());
    _sender.clear();

    // Adding node. No new read requests, but buckets thrown
    setSystemState(lib::ClusterState("distributor:3 storage:3"));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());
    assertCorrectBuckets(numBuckets, "distributor:3 storage:3");
    _sender.clear();

    // Removing distributor. Need to refetch new data from all nodes.
    setSystemState(lib::ClusterState("distributor:2 storage:3"));
    CPPUNIT_ASSERT_EQUAL(messageCount(3), _sender.commands.size());
    completeBucketInfoGathering(lib::ClusterState("distributor:2 storage:3"),
                                messageCount(3), numBuckets);
    _sender.clear();
    assertCorrectBuckets(numBuckets, "distributor:2 storage:3");
}

void
BucketDBUpdaterTest::testDistributorChangeWithGrouping()
{
    std::string distConfig(getDistConfig6Nodes3Groups());
    setDistribution(distConfig);
    int numBuckets = 100;

    setSystemState(lib::ClusterState("distributor:6 storage:6"));
    CPPUNIT_ASSERT_EQUAL(messageCount(6), _sender.commands.size());
    completeBucketInfoGathering(lib::ClusterState("distributor:6 storage:6"),
                                messageCount(6), numBuckets);
    _sender.clear();

    // Distributor going down in other group, no change
    setSystemState(lib::ClusterState("distributor:6 .5.s:d storage:6"));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());
    _sender.clear();

    setSystemState(lib::ClusterState("distributor:6 storage:6"));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());
    assertCorrectBuckets(numBuckets, "distributor:6 storage:6");
    _sender.clear();

    // Unchanged grouping cause no change.
    setDistribution(distConfig);
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());

    // Changed grouping cause change
    setDistribution(getDistConfig6Nodes4Groups());

    CPPUNIT_ASSERT_EQUAL(messageCount(6), _sender.commands.size());
}

void
BucketDBUpdaterTest::testNormalUsageInitializing()
{
    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1 .0.s:i"));

    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    // Not yet passing on system state.
    CPPUNIT_ASSERT_EQUAL(size_t(0), _senderDown.commands.size());

    completeBucketInfoGathering(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                _bucketSpaces.size(), 10, 10);

    assertCorrectBuckets(10, "distributor:1 storage:1");

    for (int i=10; i<20; i++) {
        verifyInvalid(document::BucketId(16, i), 0);
    }

    // Pass on cluster state and recheck buckets now.
    CPPUNIT_ASSERT_EQUAL(size_t(1), _senderDown.commands.size());

    _sender.clear();
    _senderDown.clear();

    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1"));

    // Send a new request bucket info up.
    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    completeBucketInfoGathering(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                _bucketSpaces.size(), 20);

    // Pass on cluster state and recheck buckets now.
    CPPUNIT_ASSERT_EQUAL(size_t(1), _senderDown.commands.size());

    assertCorrectBuckets(20, "distributor:1 storage:1");
}

void
BucketDBUpdaterTest::testFailedRequestBucketInfo()
{
    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1"));

    // 2 messages sent up: 1 to the nodes, and one reply to the setsystemstate.
    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    {
        for (uint32_t i = 0; i < _bucketSpaces.size(); ++i) {
            std::shared_ptr<api::RequestBucketInfoReply> reply =
                getFakeBucketReply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                   *((RequestBucketInfoCommand*)_sender.commands[i].get()),
                                   0,
                                   10);
            reply->setResult(api::ReturnCode::NOT_CONNECTED);
            getBucketDBUpdater().onRequestBucketInfoReply(reply);
        }

        // Trigger that delayed message is sent
        getClock().addSecondsToTime(10);
        getBucketDBUpdater().resendDelayedMessages();
    }

    // Should be resent.
    CPPUNIT_ASSERT_EQUAL(getRequestBucketInfoStrings(messageCount(2)),
                         _sender.getCommands());

    CPPUNIT_ASSERT_EQUAL(size_t(0), _senderDown.commands.size());

    for (uint32_t i = 0; i < _bucketSpaces.size(); ++i) {
        fakeBucketReply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                        *_sender.commands[_bucketSpaces.size() + i], 10);
    }

    for (int i=0; i<10; i++) {
        CPPUNIT_ASSERT_EQUAL(
                std::string(""),
                verifyBucket(document::BucketId(16, i),
                            lib::ClusterState("distributor:1 storage:1")));
    }

    // Set system state should now be passed on
    CPPUNIT_ASSERT_EQUAL(std::string("Set system state"),
                         _senderDown.getCommands());
}

void
BucketDBUpdaterTest::testEncodeErrorHandling()
{
    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1"));

    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    // Not yet passing on system state.
    CPPUNIT_ASSERT_EQUAL(size_t(0), _senderDown.commands.size());
    for (uint32_t i = 0; i < _bucketSpaces.size(); ++i) {
        std::shared_ptr<api::RequestBucketInfoReply> reply =
            getFakeBucketReply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                               *((RequestBucketInfoCommand*)_sender.commands[i].get()),
                               0,
                               10);

        reply->setResult(api::ReturnCode::ENCODE_ERROR);
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
    }
    CPPUNIT_ASSERT_EQUAL(std::string("Set system state"),
                         _senderDown.getCommands());
}

void
BucketDBUpdaterTest::testDownWhileInit()
{
    setStorageNodes(3);

    fakeBucketReply(lib::ClusterState("distributor:1 storage:3"),
                    *_sender.commands[0], 5);

    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:d"));

    fakeBucketReply(lib::ClusterState("distributor:1 storage:3"),
                    *_sender.commands[2], 5);

    fakeBucketReply(lib::ClusterState("distributor:1 storage:3"),
                    *_sender.commands[1], 5);
}

bool
BucketDBUpdaterTest::bucketExistsThatHasNode(int bucketCount, uint16_t node) const
{
   for (int i=1; i<bucketCount; i++) {
       if (bucketHasNode(document::BucketId(16, i), node)) {
           return true;
       }
   }

   return false;
}

std::string
BucketDBUpdaterTest::getNodeList(std::vector<uint16_t> nodes, size_t count)
{
    std::ostringstream ost;
    bool first = true;
    for (const auto &node : nodes) {
        for (uint32_t i = 0; i < count; ++i) {
            if (!first) {
                ost << ",";
            }
            ost << node;
            first = false;
        }
    }
    return ost.str();
}

std::string
BucketDBUpdaterTest::getNodeList(std::vector<uint16_t> nodes)
{
    return getNodeList(std::move(nodes), _bucketSpaces.size());
}

std::vector<uint16_t>
BucketDBUpdaterTest::expandNodeVec(const std::vector<uint16_t> &nodes)
{
    std::vector<uint16_t> res;
    size_t count = _bucketSpaces.size();
    for (const auto &node : nodes) {
        for (uint32_t i = 0; i < count; ++i) {
            res.push_back(node);
        }
    }
    return res;
}

void
BucketDBUpdaterTest::testNodeDown()
{
    setStorageNodes(3);
    enableDistributorClusterState("distributor:1 storage:3");

    for (int i=1; i<100; i++) {
        addIdealNodes(document::BucketId(16, i));
    }

    CPPUNIT_ASSERT(bucketExistsThatHasNode(100, 1));
                   
    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:d"));
    
    CPPUNIT_ASSERT(!bucketExistsThatHasNode(100, 1));
}

void
BucketDBUpdaterTest::testStorageNodeInMaintenanceClearsBucketsForNode()
{
    setStorageNodes(3);
    enableDistributorClusterState("distributor:1 storage:3");

    for (int i=1; i<100; i++) {
        addIdealNodes(document::BucketId(16, i));
    }

    CPPUNIT_ASSERT(bucketExistsThatHasNode(100, 1));
                   
    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:m"));
    
    CPPUNIT_ASSERT(!bucketExistsThatHasNode(100, 1));
}

void
BucketDBUpdaterTest::testNodeDownCopiesGetInSync()
{
    setStorageNodes(3);

   lib::ClusterState systemState("distributor:1 storage:3");
    document::BucketId bid(16, 1);

    addNodesToBucketDB(bid, "0=3,1=2,2=3");

    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:d"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=0,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false), "
                        "node(idx=2,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false)"),
            dumpBucket(bid));
}

void
BucketDBUpdaterTest::testInitializingWhileRecheck()
{
   lib::ClusterState systemState("distributor:1 storage:2 .0.s:i .0.i:0.1");
    setSystemState(systemState);

    CPPUNIT_ASSERT_EQUAL(messageCount(2), _sender.commands.size());
    CPPUNIT_ASSERT_EQUAL(size_t(0), _senderDown.commands.size());

    getBucketDBUpdater().recheckBucketInfo(1, makeDocumentBucket(document::BucketId(16, 3)));

    for (uint32_t i = 0; i < messageCount(2); ++i) {
        fakeBucketReply(systemState, *_sender.commands[i], 100);
    }

    // Now we can pass on system state.
    CPPUNIT_ASSERT_EQUAL(size_t(1), _senderDown.commands.size());

    CPPUNIT_ASSERT_EQUAL(MessageType::SETSYSTEMSTATE,
                         _senderDown.commands[0]->getType());
}

void
BucketDBUpdaterTest::testBitChange()
{

    std::vector<document::BucketId> bucketlist;

    {
        setSystemState(lib::ClusterState("bits:14 storage:1 distributor:2"));

        CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

        for (uint32_t bsi = 0; bsi < _bucketSpaces.size(); ++bsi) {
            CPPUNIT_ASSERT(_sender.commands[bsi]->getType() == MessageType::REQUESTBUCKETINFO);
            const auto &req = dynamic_cast<const RequestBucketInfoCommand &>(*_sender.commands[bsi]);
            RequestBucketInfoReply* sreply = new RequestBucketInfoReply(req);
            sreply->setAddress(storageAddress(0));
            api::RequestBucketInfoReply::EntryVector &vec = sreply->getBucketInfo();
            if (req.getBucketSpace() == FixedBucketSpaces::default_space()) {
                int cnt=0;
                for (int i=0; cnt < 2; i++) {
                    lib::Distribution distribution = defaultDistributorBucketSpace().getDistribution();
                    std::vector<uint16_t> distributors;
                    if (distribution.getIdealDistributorNode(
                        lib::ClusterState("redundancy:1 bits:14 storage:1 distributor:2"),
                        document::BucketId(16, i))
                        == 0)
                    {
                        vec.push_back(api::RequestBucketInfoReply::Entry(
                           document::BucketId(16, i),
                           api::BucketInfo(10,1,1)));

                        bucketlist.push_back(document::BucketId(16, i));
                        cnt++;
                    }
                }
            }

            getBucketDBUpdater().onRequestBucketInfoReply(std::shared_ptr<RequestBucketInfoReply>(sreply));
        }
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(bucketlist[0]));
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000002) : "
                        "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(bucketlist[1]));

    {
        _sender.clear();
        setSystemState(lib::ClusterState("bits:16 storage:1 distributor:2"));

        CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());
        for (uint32_t bsi = 0; bsi < _bucketSpaces.size(); ++bsi) {

            CPPUNIT_ASSERT(_sender.commands[bsi]->getType() == MessageType::REQUESTBUCKETINFO);
            const auto &req = dynamic_cast<const RequestBucketInfoCommand &>(*_sender.commands[bsi]);
            RequestBucketInfoReply* sreply = new RequestBucketInfoReply(req);
            sreply->setAddress(storageAddress(0));
            sreply->setResult(api::ReturnCode::OK);
            if (req.getBucketSpace() == FixedBucketSpaces::default_space()) {
                api::RequestBucketInfoReply::EntryVector &vec = sreply->getBucketInfo();

                for (uint32_t i = 0; i < 3; ++i) {
                    vec.push_back(api::RequestBucketInfoReply::Entry(
                                                                             document::BucketId(16, i),
                                                                             api::BucketInfo(10,1,1)));
                }

                vec.push_back(api::RequestBucketInfoReply::Entry(
                                                                         document::BucketId(16, 4),
                                                                         api::BucketInfo(10,1,1)));
            }

            getBucketDBUpdater().onRequestBucketInfoReply(
                    std::shared_ptr<RequestBucketInfoReply>(sreply));
        }
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000000) : "
                        "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 0)));
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1)));
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000002) : "
                        "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 2)));
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000004) : "
                        "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 4)));

    {
        _sender.clear();
        setSystemState(lib::ClusterState("storage:1 distributor:2 .1.s:i"));
    }

    {
        _sender.clear();
        setSystemState(lib::ClusterState("storage:1 distributor:2"));
    }
};

void
BucketDBUpdaterTest::testRecheckNodeWithFailure()
{
    initializeNodesAndBuckets(3, 5);

    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(1, makeDocumentBucket(document::BucketId(16, 3)));

    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());


    uint16_t index = 0;
    {
        api::RequestBucketInfoCommand& rbi(
                dynamic_cast<RequestBucketInfoCommand&>(*_sender.commands[0]));
        CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 3), rbi.getBuckets()[0]);
        auto reply(std::make_shared<api::RequestBucketInfoReply>(rbi));

        const api::StorageMessageAddress *address = _sender.commands[0]->getAddress();
        index = address->getIndex();

        reply->setResult(api::ReturnCode::NOT_CONNECTED);
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
            // Trigger that delayed message is sent
        getClock().addSecondsToTime(10);
        getBucketDBUpdater().resendDelayedMessages();
    }

    CPPUNIT_ASSERT_EQUAL(size_t(2), _sender.commands.size());

    setSystemState(
           lib::ClusterState(vespalib::make_string("distributor:1 storage:3 .%d.s:d", index)));

    // Recheck bucket.
    {
        api::RequestBucketInfoCommand& rbi(dynamic_cast<RequestBucketInfoCommand&>
                                           (*_sender.commands[1]));
        CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 3), rbi.getBuckets()[0]);
        auto reply(std::make_shared<api::RequestBucketInfoReply>(rbi));
        reply->setResult(api::ReturnCode::NOT_CONNECTED);
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
    }

    // Should not retry since node is down.
    CPPUNIT_ASSERT_EQUAL(size_t(2), _sender.commands.size());
}

void
BucketDBUpdaterTest::testRecheckNode()
{
    initializeNodesAndBuckets(3, 5);

    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(1, makeDocumentBucket(document::BucketId(16, 3)));

    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());

    api::RequestBucketInfoCommand& rbi(
            dynamic_cast<RequestBucketInfoCommand&>(*_sender.commands[0]));
    CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 3), rbi.getBuckets()[0]);

    auto reply(std::make_shared<api::RequestBucketInfoReply>(rbi));
    reply->getBucketInfo().push_back(
            api::RequestBucketInfoReply::Entry(document::BucketId(16, 3),
                                               api::BucketInfo(20, 10, 12, 50, 60, true, true)));
    getBucketDBUpdater().onRequestBucketInfoReply(reply);

    lib::ClusterState state("distributor:1 storage:3");
    for (uint32_t i = 0; i < 3; i++) {
        CPPUNIT_ASSERT_EQUAL(
                getIdealStr(document::BucketId(16, i), state),
                getNodes(document::BucketId(16, i)));
    }

    for (uint32_t i = 4; i < 5; i++) {
        CPPUNIT_ASSERT_EQUAL(
                getIdealStr(document::BucketId(16, i), state),
                getNodes(document::BucketId(16, i)));
    }

    BucketDatabase::Entry entry = getBucketDatabase().get(document::BucketId(16, 3));
    CPPUNIT_ASSERT(entry.valid());

    const BucketCopy* copy = entry->getNode(1);
    CPPUNIT_ASSERT(copy != 0);
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(20,10,12, 50, 60, true, true),
                         copy->getBucketInfo());
}

void
BucketDBUpdaterTest::testNotifyBucketChange()
{
    enableDistributorClusterState("distributor:1 storage:1");

    addNodesToBucketDB(document::BucketId(16, 1), "0=1234");
    _sender.replies.clear();

    {
        api::BucketInfo info(1, 2, 3, 4, 5, true, true);
        auto cmd(std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 1)), info));
        cmd->setSourceIndex(0);
        getBucketDBUpdater().onNotifyBucketChange(cmd);
    }

    {
        api::BucketInfo info(10, 11, 12, 13, 14, false, false);
        auto cmd(std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 2)), info));
        cmd->setSourceIndex(0);
        getBucketDBUpdater().onNotifyBucketChange(cmd);
    }

    // Must receive reply
    CPPUNIT_ASSERT_EQUAL(size_t(2), _sender.replies.size());

    for (int i = 0; i < 2; ++i) {
        CPPUNIT_ASSERT_EQUAL(MessageType::NOTIFYBUCKETCHANGE_REPLY,
                             _sender.replies[i]->getType());
    }

    // No database update until request bucket info replies have been received.
    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x4000000000000001) : "
                                     "node(idx=0,crc=0x4d2,docs=1234/1234,bytes=1234/1234,"
                                           "trusted=false,active=false,ready=false)"),
                         dumpBucket(document::BucketId(16, 1)));
    CPPUNIT_ASSERT_EQUAL(std::string("NONEXISTING"),
                         dumpBucket(document::BucketId(16, 2)));

    CPPUNIT_ASSERT_EQUAL(size_t(2), _sender.commands.size());

    std::vector<api::BucketInfo> infos;
    infos.push_back(api::BucketInfo(4567, 200, 2000, 400, 4000, true, true));
    infos.push_back(api::BucketInfo(8999, 300, 3000, 500, 5000, false, false));

    for (int i = 0; i < 2; ++i) {
        api::RequestBucketInfoCommand& rbi(
                dynamic_cast<RequestBucketInfoCommand&>(*_sender.commands[i]));
        CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, i + 1), rbi.getBuckets()[0]);

        auto reply(std::make_shared<api::RequestBucketInfoReply>(rbi));
        reply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(document::BucketId(16, i + 1),
                        infos[i]));
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=0,crc=0x11d7,docs=200/400,bytes=2000/4000,trusted=true,active=true,ready=true)"),
            dumpBucket(document::BucketId(16, 1)));
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000002) : "
                        "node(idx=0,crc=0x2327,docs=300/500,bytes=3000/5000,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 2)));

}

void
BucketDBUpdaterTest::testNotifyBucketChangeFromNodeDown()
{
    enableDistributorClusterState("distributor:1 storage:2");

    addNodesToBucketDB(document::BucketId(16, 1), "1=1234");

    _sender.replies.clear();

    {
        api::BucketInfo info(8999, 300, 3000, 500, 5000, false, false);
        auto cmd(std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 1)), info));
        cmd->setSourceIndex(0);
        getBucketDBUpdater().onNotifyBucketChange(cmd);
    }
    // Enable here to avoid having request bucket info be silently swallowed
    // (sendRequestBucketInfo drops message if node is down).
    enableDistributorClusterState("distributor:1 storage:2 .0.s:d");

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=1,crc=0x4d2,docs=1234/1234,bytes=1234/1234,trusted=false,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1)));

    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size());
    CPPUNIT_ASSERT_EQUAL(MessageType::NOTIFYBUCKETCHANGE_REPLY,
                         _sender.replies[0]->getType());

    // Currently, this pending operation will be auto-flushed when the cluster state
    // changes so the behavior is still correct. Keep this test around to prevent
    // regressions here.
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());
    api::RequestBucketInfoCommand& rbi(
            dynamic_cast<RequestBucketInfoCommand&>(*_sender.commands[0]));
    CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1), rbi.getBuckets()[0]);

    auto reply(std::make_shared<api::RequestBucketInfoReply>(rbi));
    reply->getBucketInfo().push_back(
            api::RequestBucketInfoReply::Entry(
                    document::BucketId(16, 1),
                    api::BucketInfo(8999, 300, 3000, 500, 5000, false, false)));
    getBucketDBUpdater().onRequestBucketInfoReply(reply);

    // No change
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=1,crc=0x4d2,docs=1234/1234,bytes=1234/1234,trusted=false,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1)));
}

/**
 * Test that NotifyBucketChange received while there's a pending cluster state
 * waits until the cluster state has been enabled as current before it sends off
 * the single bucket info requests. This is to prevent a race condition where
 * the replies to bucket info requests for buckets that would be owned by the
 * distributor in the pending state but not by the current state would be
 * discarded when attempted inserted into the bucket database.
 */
void
BucketDBUpdaterTest::testNotifyChangeWithPendingStateQueuesBucketInfoRequests()
{
    setSystemState(lib::ClusterState("distributor:1 storage:1"));
    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    {
        api::BucketInfo info(8999, 300, 3000, 500, 5000, false, false);
        auto cmd(std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 1)), info));
        cmd->setSourceIndex(0);
        getBucketDBUpdater().onNotifyBucketChange(cmd);
    }

    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    completeBucketInfoGathering(lib::ClusterState("distributor:1 storage:1"),
                                _bucketSpaces.size(), 10);

    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size() + 1, _sender.commands.size());

    {
        api::RequestBucketInfoCommand& rbi(
                dynamic_cast<RequestBucketInfoCommand&>(*_sender.commands[_bucketSpaces.size()]));
        CPPUNIT_ASSERT_EQUAL(size_t(1), rbi.getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1), rbi.getBuckets()[0]);
    }
    _sender.clear();

    // Queue must be cleared once pending state is enabled.
    {
        lib::ClusterState state("distributor:1 storage:2");
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        setAndEnableClusterState(state, expectedMsgs, dummyBucketsToReturn);
    }
    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());
    {
        api::RequestBucketInfoCommand& rbi(
                dynamic_cast<RequestBucketInfoCommand&>(*_sender.commands[0]));
        CPPUNIT_ASSERT_EQUAL(size_t(0), rbi.getBuckets().size());
    }
}

void
BucketDBUpdaterTest::testMergeReply()
{
    enableDistributorClusterState("distributor:1 storage:3");

    addNodesToBucketDB(document::BucketId(16, 1234),
                      "0=1234,1=1234,2=1234");

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(api::MergeBucketCommand::Node(0));
    nodes.push_back(api::MergeBucketCommand::Node(1));
    nodes.push_back(api::MergeBucketCommand::Node(2));

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)), nodes, 0);

    auto reply(std::make_shared<api::MergeBucketReply>(cmd));

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    CPPUNIT_ASSERT_EQUAL(size_t(3), _sender.commands.size());

    for (uint32_t i = 0; i < 3; i++) {
        std::shared_ptr<api::RequestBucketInfoCommand>
            req(std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(
                        _sender.commands[i]));

        CPPUNIT_ASSERT(req.get());
        CPPUNIT_ASSERT_EQUAL(size_t(1), req->getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1234), req->getBuckets()[0]);

        auto reqreply(std::make_shared<api::RequestBucketInfoReply>(*req));
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(document::BucketId(16, 1234),
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));

        getBucketDBUpdater().onRequestBucketInfoReply(reqreply);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x40000000000004d2) : "
                        "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
                        "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false), "
                        "node(idx=2,crc=0x1e,docs=300/300,bytes=3000/3000,trusted=false,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1234)));
};

void
BucketDBUpdaterTest::testMergeReplyNodeDown()
{
    enableDistributorClusterState("distributor:1 storage:3");
    std::vector<api::MergeBucketCommand::Node> nodes;

    addNodesToBucketDB(document::BucketId(16, 1234), "0=1234,1=1234,2=1234");

    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)), nodes, 0);

    auto reply(std::make_shared<api::MergeBucketReply>(cmd));

    setSystemState(lib::ClusterState("distributor:1 storage:2"));

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    CPPUNIT_ASSERT_EQUAL(size_t(2), _sender.commands.size());

    for (uint32_t i = 0; i < 2; i++) {
        std::shared_ptr<api::RequestBucketInfoCommand> req(
                std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(
                        _sender.commands[i]));

        CPPUNIT_ASSERT(req.get());
        CPPUNIT_ASSERT_EQUAL(size_t(1), req->getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1234), req->getBuckets()[0]);

        auto reqreply(std::make_shared<api::RequestBucketInfoReply>(*req));
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(
                        document::BucketId(16, 1234),
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));
        getBucketDBUpdater().onRequestBucketInfoReply(reqreply);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x40000000000004d2) : "
                        "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
                        "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1234)));
};

void
BucketDBUpdaterTest::testMergeReplyNodeDownAfterRequestSent()
{
    enableDistributorClusterState("distributor:1 storage:3");
    std::vector<api::MergeBucketCommand::Node> nodes;

    addNodesToBucketDB(document::BucketId(16, 1234), "0=1234,1=1234,2=1234");

    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)), nodes, 0);

    auto reply(std::make_shared<api::MergeBucketReply>(cmd));

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    CPPUNIT_ASSERT_EQUAL(size_t(3), _sender.commands.size());

    setSystemState(lib::ClusterState("distributor:1 storage:2"));

    for (uint32_t i = 0; i < 3; i++) {
        std::shared_ptr<api::RequestBucketInfoCommand> req(
                std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(
                        _sender.commands[i]));

        CPPUNIT_ASSERT(req.get());
        CPPUNIT_ASSERT_EQUAL(size_t(1), req->getBuckets().size());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1234), req->getBuckets()[0]);

        auto reqreply(std::make_shared<api::RequestBucketInfoReply>(*req));
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(
                        document::BucketId(16, 1234),
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));
        getBucketDBUpdater().onRequestBucketInfoReply(reqreply);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x40000000000004d2) : "
                        "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
                        "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1234)));
};


void
BucketDBUpdaterTest::testFlush()
{
    enableDistributorClusterState("distributor:1 storage:3");
    _sender.clear();

    addNodesToBucketDB(document::BucketId(16, 1234), "0=1234,1=1234,2=1234");

    std::vector<api::MergeBucketCommand::Node> nodes;
    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)),
                                nodes,
                                0);

    auto reply(std::make_shared<api::MergeBucketReply>(cmd));

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    CPPUNIT_ASSERT_EQUAL(size_t(3), _sender.commands.size());
    CPPUNIT_ASSERT_EQUAL(size_t(0), _senderDown.replies.size());

    getBucketDBUpdater().flush();
    // Flushing should drop all merge bucket replies
    CPPUNIT_ASSERT_EQUAL(size_t(0), _senderDown.commands.size());
}

std::string
BucketDBUpdaterTest::getSentNodes(
        const std::string& oldClusterState,
        const std::string& newClusterState)
{
    auto fixture = createPendingStateFixtureForStateChange(
            oldClusterState, newClusterState);

    sortSentMessagesByIndex(fixture->sender);

    std::ostringstream ost;
    for (uint32_t i = 0; i < fixture->sender.commands.size(); i++) {
        RequestBucketInfoCommand& req(dynamic_cast<RequestBucketInfoCommand&>(
                *fixture->sender.commands[i]));

        if (i > 0) {
            ost << ",";
        }

        ost << req.getAddress()->getIndex();
    }

    return ost.str();
}

std::string
BucketDBUpdaterTest::getSentNodesDistributionChanged(
        const std::string& oldClusterState)
{
    MessageSenderStub sender;

    framework::defaultimplementation::FakeClock clock;
    ClusterInformation::CSP clusterInfo(createClusterInfo(oldClusterState));
    std::unique_ptr<PendingClusterState> state(
            PendingClusterState::createForDistributionChange(
                    clock, clusterInfo, sender, getBucketSpaceRepo(), api::Timestamp(1)));

    sortSentMessagesByIndex(sender);

    std::ostringstream ost;
    for (uint32_t i = 0; i < sender.commands.size(); i++) {
        RequestBucketInfoCommand* req =
            dynamic_cast<RequestBucketInfoCommand*>(sender.commands[i].get());

        if (i > 0) {
            ost << ",";
        }

        ost << req->getAddress()->getIndex();
    }

    return ost.str();
}

void
BucketDBUpdaterTest::testPendingClusterStateSendMessages()
{
    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2}),
            getSentNodes("cluster:d",
                         "distributor:1 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1}),
            getSentNodes("cluster:d",
                         "distributor:1 storage:3 .2.s:m"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({2}),
            getSentNodes("distributor:1 storage:2",
                         "distributor:1 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({2, 3, 4, 5}),
            getSentNodes("distributor:1 storage:2",
                         "distributor:1 storage:6"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2}),
            getSentNodes("distributor:4 storage:3",
                         "distributor:3 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2, 3}),
            getSentNodes("distributor:4 storage:3",
                         "distributor:4 .2.s:d storage:4"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:4 storage:3",
                         "distributor:4 .0.s:d storage:4"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:3 storage:3",
                         "distributor:4 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({2}),
            getSentNodes("distributor:3 storage:3 .2.s:i",
                         "distributor:3 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({1}),
            getSentNodes("distributor:3 storage:3 .1.s:d",
                         "distributor:3 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({1, 2, 4}),
            getSentNodes("distributor:3 storage:4 .1.s:d .2.s:i",
                         "distributor:3 storage:5"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:1 storage:3",
                         "cluster:d"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:1 storage:3",
                         "distributor:1 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:1 storage:3",
                         "cluster:d distributor:1 storage:6"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:3 storage:3",
                         "distributor:3 .2.s:m storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2}),
            getSentNodes("distributor:3 .2.s:m storage:3",
                         "distributor:3 .2.s:d storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:3 .2.s:m storage:3",
                         "distributor:3 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2}),
            getSentNodesDistributionChanged("distributor:3 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1}),
            getSentNodes("distributor:10 storage:2",
                         "distributor:10 .1.s:d storage:2"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({1}),
            getSentNodes("distributor:2 storage:2",
                         "distributor:2 storage:2 .1.d:3 .1.d.1.s:d"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({1}),
            getSentNodes("distributor:2 storage:2 .1.s:d",
                         "distributor:2 storage:2 .1.d:3 .1.d.1.s:d"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:2 storage:2",
                         "distributor:3 .2.s:i storage:2"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2}),
            getSentNodes("distributor:3 storage:3",
                         "distributor:3 .2.s:s storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:3 .2.s:s storage:3",
                         "distributor:3 .2.s:d storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            getNodeList({1}),
            getSentNodes("distributor:3 storage:3 .1.s:m",
                         "distributor:3 storage:3"));
    
    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:3 storage:3",
                         "distributor:3 storage:3 .1.s:m"));
};

void
BucketDBUpdaterTest::testPendingClusterStateReceive()
{
    MessageSenderStub sender;

    auto cmd(std::make_shared<api::SetSystemStateCommand>(
            lib::ClusterState("distributor:1 storage:3")));

    framework::defaultimplementation::FakeClock clock;
    ClusterInformation::CSP clusterInfo(createClusterInfo("cluster:d"));
    OutdatedNodesMap outdatedNodesMap;
    std::unique_ptr<PendingClusterState> state(
            PendingClusterState::createForClusterStateChange(
                    clock, clusterInfo, sender, getBucketSpaceRepo(), cmd, outdatedNodesMap,
                    api::Timestamp(1)));

    CPPUNIT_ASSERT_EQUAL(messageCount(3), sender.commands.size());

    sortSentMessagesByIndex(sender);

    std::ostringstream ost;
    for (uint32_t i = 0; i < sender.commands.size(); i++) {
        RequestBucketInfoCommand* req =
            dynamic_cast<RequestBucketInfoCommand*>(sender.commands[i].get());

        RequestBucketInfoReply* rep =
            new RequestBucketInfoReply(*req);

        rep->getBucketInfo().push_back(
                RequestBucketInfoReply::Entry(
                        document::BucketId(16, i),
                        api::BucketInfo(i, i, i, i, i)));

        CPPUNIT_ASSERT(
                state->onRequestBucketInfoReply(
                        std::shared_ptr<api::RequestBucketInfoReply>(rep)));

        CPPUNIT_ASSERT_EQUAL(i == sender.commands.size() - 1 ? true : false,
                             state->done());
    }

    auto &pendingTransition = state->getPendingBucketSpaceDbTransition(makeBucketSpace());
    CPPUNIT_ASSERT_EQUAL(3, (int)pendingTransition.results().size());
}

void
BucketDBUpdaterTest::testPendingClusterStateWithGroupDown()
{
    std::string config(getDistConfig6Nodes4Groups());
    config += "distributor_auto_ownership_transfer_on_whole_group_down true\n";
    setDistribution(config);

    // Group config has nodes {0, 1}, {2, 3}, {4, 5}
    // We're node index 0.

    // Entire group 1 goes down. Must refetch from all nodes.
    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2, 3, 4, 5}),
            getSentNodes("distributor:6 storage:6",
                         "distributor:6 .2.s:d .3.s:d storage:6"));

    // But don't fetch if not the entire group is down.
    CPPUNIT_ASSERT_EQUAL(
            std::string(""),
            getSentNodes("distributor:6 storage:6",
                         "distributor:6 .2.s:d storage:6"));
}

void
BucketDBUpdaterTest::testPendingClusterStateWithGroupDownAndNoHandover()
{
    std::string config(getDistConfig6Nodes4Groups());
    config += "distributor_auto_ownership_transfer_on_whole_group_down false\n";
    setDistribution(config);

    // Group is down, but config says to not do anything about it.
    CPPUNIT_ASSERT_EQUAL(
            getNodeList({0, 1, 2, 3, 4, 5}, _bucketSpaces.size() - 1),
            getSentNodes("distributor:6 storage:6",
                         "distributor:6 .2.s:d .3.s:d storage:6"));
}

void
parseInputData(const std::string& data,
               uint64_t timestamp,
               PendingClusterState& state,
               bool includeBucketInfo)
{
    vespalib::StringTokenizer tokenizer(data, "|");
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        vespalib::StringTokenizer tok2(tokenizer[i], ":");

        uint16_t node = atoi(tok2[0].c_str());

        state.setNodeReplied(node);
        auto &pendingTransition = state.getPendingBucketSpaceDbTransition(makeBucketSpace());

        vespalib::StringTokenizer tok3(tok2[1], ",");
        for (uint32_t j = 0; j < tok3.size(); j++) {
            if (includeBucketInfo) {
                vespalib::StringTokenizer tok4(tok3[j], "/");

                pendingTransition.addNodeInfo(
                        document::BucketId(16, atoi(tok4[0].c_str())),
                        BucketCopy(
                                timestamp,
                                node,
                                api::BucketInfo(
                                        atoi(tok4[1].c_str()),
                                        atoi(tok4[2].c_str()),
                                        atoi(tok4[3].c_str()),
                                        atoi(tok4[2].c_str()),
                                        atoi(tok4[3].c_str()))));
            } else {
                pendingTransition.addNodeInfo(
                        document::BucketId(16, atoi(tok3[j].c_str())),
                        BucketCopy(timestamp,
                                   node,
                                   api::BucketInfo(3, 3, 3, 3, 3)));
            }
        }
    }
}

struct BucketDumper : public BucketDatabase::EntryProcessor
{
    std::ostringstream ost;
    bool _includeBucketInfo;

    BucketDumper(bool includeBucketInfo)
        : _includeBucketInfo(includeBucketInfo)
    {
    }

    bool process(const BucketDatabase::Entry& e) override {
        document::BucketId bucketId(e.getBucketId());

        ost << (uint32_t)bucketId.getRawId() << ":";
        for (uint32_t i = 0; i < e->getNodeCount(); ++i) {
            if (i > 0) {
                ost << ",";
            }
            const BucketCopy& copy(e->getNodeRef(i));
            ost << copy.getNode();
            if (_includeBucketInfo) {
                ost << "/" << copy.getChecksum()
                    << "/" << copy.getDocumentCount()
                    << "/" << copy.getTotalDocumentSize()
                    << "/" << (copy.trusted() ? "t" : "u");
            }
        }
        ost << "|";
        return true;
    }
};

std::string
BucketDBUpdaterTest::mergeBucketLists(
        const lib::ClusterState& oldState,
        const std::string& existingData,
        const lib::ClusterState& newState,
        const std::string& newData,
        bool includeBucketInfo)
{
    framework::defaultimplementation::FakeClock clock;
    framework::MilliSecTimer timer(clock);

    MessageSenderStub sender;
    OutdatedNodesMap outdatedNodesMap;

    {
        auto cmd(std::make_shared<api::SetSystemStateCommand>(oldState));

        api::Timestamp beforeTime(1);

        ClusterInformation::CSP clusterInfo(createClusterInfo("cluster:d"));
        std::unique_ptr<PendingClusterState> state(
                PendingClusterState::createForClusterStateChange(
                        clock, clusterInfo, sender, getBucketSpaceRepo(), cmd, outdatedNodesMap,
                        beforeTime));

        parseInputData(existingData, beforeTime, *state, includeBucketInfo);
        state->mergeIntoBucketDatabases();
    }

    BucketDumper dumper_tmp(true);
    getBucketDatabase().forEach(dumper_tmp);

    {
        auto cmd(std::make_shared<api::SetSystemStateCommand>(
                    lib::ClusterState(newState)));

        api::Timestamp afterTime(2);

        ClusterInformation::CSP clusterInfo(createClusterInfo(oldState.toString()));
        std::unique_ptr<PendingClusterState> state(
                PendingClusterState::createForClusterStateChange(
                        clock, clusterInfo, sender, getBucketSpaceRepo(), cmd, outdatedNodesMap,
                        afterTime));

        parseInputData(newData, afterTime, *state, includeBucketInfo);
        state->mergeIntoBucketDatabases();
    }

    BucketDumper dumper(includeBucketInfo);
    auto &bucketDb(defaultDistributorBucketSpace().getBucketDatabase());
    bucketDb.forEach(dumper);
    bucketDb.clear();
    return dumper.ost.str();
}

std::string
BucketDBUpdaterTest::mergeBucketLists(const std::string& existingData,
                                       const std::string& newData,
                                       bool includeBucketInfo)
{
    return mergeBucketLists(
            lib::ClusterState("distributor:1 storage:3"),
            existingData,
            lib::ClusterState("distributor:1 storage:3"),
            newData,
            includeBucketInfo);
}

void
BucketDBUpdaterTest::testPendingClusterStateMerge()
{
    // Simple initializing case - ask all nodes for info
    CPPUNIT_ASSERT_EQUAL(
            // Result is on the form: [bucket w/o count bits]:[node indexes]|..
            std::string("4:0,1|2:0,1|6:1,2|1:0,2|5:2,0|3:2,1|"),
            // Input is on the form: [node]:[bucket w/o count bits]|...
            mergeBucketLists(
                    "",
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6"));

    // Node came up with fewer buckets (lost disk)
    CPPUNIT_ASSERT_EQUAL(
            std::string("4:1|2:0,1|6:1,2|1:2,0|5:2|3:2,1|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:3"),
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                    lib::ClusterState("distributor:1 storage:3 .0.d:3 .0.d.1.s:d"),
                    "0:1,2")
            );

    // New node came up
    CPPUNIT_ASSERT_EQUAL(
            std::string("4:0,1|2:0,1|6:1,2,3|1:0,2,3|5:2,0,3|3:2,1,3|"),
            mergeBucketLists(
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                    "3:1,3,5,6"));

    // Node came up with some buckets removed and some added
    // Buckets that were removed should not be removed as the node
    // didn't lose a disk.
    CPPUNIT_ASSERT_EQUAL(
            std::string("8:0|4:0,1|2:0,1|6:1,0,2|1:0,2|5:2,0|3:2,1|"),
            mergeBucketLists(
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                    "0:1,2,6,8"));

    // Node came up with no buckets
    CPPUNIT_ASSERT_EQUAL(
            std::string("4:1|2:1|6:1,2|1:2|5:2|3:2,1|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:3"),
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                    lib::ClusterState("distributor:1 storage:3 .0.d:3 .0.d.1.s:d"),
                    "0:")
            );

    // One node lost a disk, another was just reasked (distributor
    // change)
    CPPUNIT_ASSERT_EQUAL(
            std::string("2:0,1|6:1,2|1:2,0|5:2|3:2,1|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:3"),
                    "0:1,2,4,5|1:2,3,6|2:1,3,5,6",
                    lib::ClusterState("distributor:1 storage:3 .0.d:3 .0.d.1.s:d"),
                    "0:1,2|1:2,3")
            );

    // Bucket info format is "bucketid/checksum/count/size"
    // Node went from initializing to up and invalid bucket went to empty.
    CPPUNIT_ASSERT_EQUAL(
            std::string("2:0/0/0/0/t|"),
            mergeBucketLists(
                    "0:2/0/0/1",
                    "0:2/0/0/0",
                    true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("5:1/2/3/4/u,0/0/0/0/u|"),
            mergeBucketLists("", "0:5/0/0/0|1:5/2/3/4", true));
}

void
BucketDBUpdaterTest::testPendingClusterStateMergeReplicaChanged()
{
    // Node went from initializing to up and non-invalid bucket changed.
    CPPUNIT_ASSERT_EQUAL(
            std::string("2:0/2/3/4/t|3:0/2/4/6/t|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:1 .0.s:i"),
                    "0:2/1/2/3,3/2/4/6",
                    lib::ClusterState("distributor:1 storage:1"),
                    "0:2/2/3/4,3/2/4/6",
                    true));
}

void
BucketDBUpdaterTest::testNoDbResurrectionForBucketNotOwnedInCurrentState()
{
    document::BucketId bucket(16, 3);
    lib::ClusterState stateBefore("distributor:1 storage:1");
    {
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn);
    }
    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(0, makeDocumentBucket(bucket));

    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());
    std::shared_ptr<api::RequestBucketInfoCommand> rbi(
            std::dynamic_pointer_cast<RequestBucketInfoCommand>(
                    _sender.commands[0]));

    lib::ClusterState stateAfter("distributor:3 storage:3");

    {
        uint32_t expectedMsgs = messageCount(2), dummyBucketsToReturn = 1;
        setAndEnableClusterState(stateAfter, expectedMsgs, dummyBucketsToReturn);
    }
    CPPUNIT_ASSERT(!getBucketDBUpdater().getDistributorComponent()
                   .ownsBucketInCurrentState(makeDocumentBucket(bucket)));

    sendFakeReplyForSingleBucketRequest(*rbi);

    CPPUNIT_ASSERT_EQUAL(std::string("NONEXISTING"), dumpBucket(bucket));
}

void
BucketDBUpdaterTest::testNoDbResurrectionForBucketNotOwnedInPendingState()
{
    document::BucketId bucket(16, 3);
    lib::ClusterState stateBefore("distributor:1 storage:1");
    {
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn);
    }
    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(0, makeDocumentBucket(bucket));

    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());
    std::shared_ptr<api::RequestBucketInfoCommand> rbi(
            std::dynamic_pointer_cast<RequestBucketInfoCommand>(
                    _sender.commands[0]));

    lib::ClusterState stateAfter("distributor:3 storage:3");
    // Set, but _don't_ enable cluster state. We want it to be pending.
    setSystemState(stateAfter);
    CPPUNIT_ASSERT(getBucketDBUpdater().getDistributorComponent()
                   .ownsBucketInCurrentState(makeDocumentBucket(bucket)));
    CPPUNIT_ASSERT(!getBucketDBUpdater()
            .checkOwnershipInPendingState(makeDocumentBucket(bucket)).isOwned());

    sendFakeReplyForSingleBucketRequest(*rbi);

    CPPUNIT_ASSERT_EQUAL(std::string("NONEXISTING"), dumpBucket(bucket));
}

/*
 * If we get a distribution config change, it's important that cluster states that
 * arrive after this--but _before_ the pending cluster state has finished--must trigger
 * a full bucket info fetch no matter what the cluster state change was! Otherwise, we
 * will with a high likelihood end up not getting the complete view of the buckets in
 * the cluster.
 */
void
BucketDBUpdaterTest::testClusterStateAlwaysSendsFullFetchWhenDistributionChangePending()
{
    lib::ClusterState stateBefore("distributor:6 storage:6");
    {
        uint32_t expectedMsgs = messageCount(6), dummyBucketsToReturn = 1;
        setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn);
    }
    _sender.clear();
    std::string distConfig(getDistConfig6Nodes3Groups());
    setDistribution(distConfig);
    sortSentMessagesByIndex(_sender);
    CPPUNIT_ASSERT_EQUAL(messageCount(6), _sender.commands.size());
    // Suddenly, a wild cluster state change appears! Even though this state
    // does not in itself imply any bucket changes, it will still overwrite the
    // pending cluster state and thus its state of pending bucket info requests.
    setSystemState(lib::ClusterState("distributor:6 .2.t:12345 storage:6"));

    CPPUNIT_ASSERT_EQUAL(messageCount(12), _sender.commands.size());

    // Send replies for first messageCount(6) (outdated requests).
    int numBuckets = 10;
    for (uint32_t i = 0; i < messageCount(6); ++i) {
        fakeBucketReply(lib::ClusterState("distributor:6 storage:6"),
                        *_sender.commands[i], numBuckets);
    }
    // No change from these.
    assertCorrectBuckets(1, "distributor:6 storage:6");

    // Send for current pending.
    for (uint32_t i = 0; i < messageCount(6); ++i) {
        fakeBucketReply(lib::ClusterState("distributor:6 .2.t:12345 storage:6"),
                        *_sender.commands[i + messageCount(6)],
                        numBuckets);
    }
    assertCorrectBuckets(numBuckets, "distributor:6 storage:6");
    _sender.clear();

    // No more pending global fetch; this should be a no-op state.
    setSystemState(lib::ClusterState("distributor:6 .3.t:12345 storage:6"));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());
}

void
BucketDBUpdaterTest::testChangedDistributionConfigTriggersRecoveryMode()
{
    setAndEnableClusterState(lib::ClusterState("distributor:6 storage:6"), messageCount(6), 20);
    _sender.clear();
    // First cluster state; implicit scan of all buckets which does not
    // use normal recovery mode ticking-path.
    CPPUNIT_ASSERT(!_distributor->isInRecoveryMode());

    std::string distConfig(getDistConfig6Nodes4Groups());
    setDistribution(distConfig);
    sortSentMessagesByIndex(_sender);
    // No replies received yet, still no recovery mode.
    CPPUNIT_ASSERT(!_distributor->isInRecoveryMode());

    CPPUNIT_ASSERT_EQUAL(messageCount(6), _sender.commands.size());
    uint32_t numBuckets = 10;
    for (uint32_t i = 0; i < messageCount(6); ++i) {
        fakeBucketReply(lib::ClusterState("distributor:6 storage:6"),
                         *_sender.commands[i], numBuckets);
    }

    // Pending cluster state (i.e. distribution) has been enabled, which should
    // cause recovery mode to be entered.
    CPPUNIT_ASSERT(_distributor->isInRecoveryMode());
}

void
BucketDBUpdaterTest::testNewlyAddedBucketsHaveCurrentTimeAsGcTimestamp()
{
    getClock().setAbsoluteTimeInSeconds(101234);
    lib::ClusterState stateBefore("distributor:1 storage:1");
    {
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn);
    }

    // setAndEnableClusterState adds n buckets with id (16, i)
    document::BucketId bucket(16, 0);
    BucketDatabase::Entry e(getBucket(bucket));
    CPPUNIT_ASSERT(e.valid());
    CPPUNIT_ASSERT_EQUAL(uint32_t(101234), e->getLastGarbageCollectionTime());
}

void
BucketDBUpdaterTest::testNewerMutationsNotOverwrittenByEarlierBucketFetch()
{
    {
        lib::ClusterState stateBefore("distributor:1 storage:1 .0.s:i");
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 0;
        // This step is required to make the distributor ready for accepting
        // the below explicit database insertion towards node 0.
        setAndEnableClusterState(stateBefore, expectedMsgs,
                                 dummyBucketsToReturn);
    }
    _sender.clear();
    getClock().setAbsoluteTimeInSeconds(1000);
    lib::ClusterState state("distributor:1 storage:1");
    setSystemState(state);
    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());

    // Before replying with the bucket info, simulate the arrival of a mutation
    // reply that alters the state of the bucket with information that will be
    // more recent that what is returned by the bucket info. This information
    // must not be lost when the bucket info is later merged into the database.
    document::BucketId bucket(16, 1);
    constexpr uint64_t insertionTimestamp = 1001ULL * 1000000;
    api::BucketInfo wantedInfo(5, 6, 7);
    getBucketDBUpdater().getDistributorComponent().updateBucketDatabase(
            makeDocumentBucket(bucket),
            BucketCopy(insertionTimestamp, 0, wantedInfo),
            DatabaseUpdate::CREATE_IF_NONEXISTING);

    getClock().setAbsoluteTimeInSeconds(1002);
    constexpr uint32_t bucketsReturned = 10; // Buckets (16, 0) ... (16, 9)
    // Return bucket information which on the timeline might originate from
    // anywhere between [1000, 1002]. Our assumption is that any mutations
    // taking place after t=1000 must have its reply received and processed
    // by this distributor and timestamped strictly higher than t=1000 (modulo
    // clock skew, of course, but that is outside the scope of this). A mutation
    // happening before t=1000 but receiving a reply at t>1000 does not affect
    // correctness, as this should contain the same bucket info as that
    // contained in the full bucket reply and the DB update is thus idempotent.
    for (uint32_t i = 0; i < _bucketSpaces.size(); ++i) {
        fakeBucketReply(state, *_sender.commands[i], bucketsReturned);
    }

    BucketDatabase::Entry e(getBucket(bucket));
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), e->getNodeCount());
    CPPUNIT_ASSERT_EQUAL(wantedInfo, e->getNodeRef(0).getBucketInfo());

}

std::vector<uint16_t>
BucketDBUpdaterTest::getSendSet() const
{
    std::vector<uint16_t> nodes;
    std::transform(_sender.commands.begin(),
                   _sender.commands.end(),
                   std::back_inserter(nodes),
                   [](auto& cmd)
    {
        auto& req(dynamic_cast<const api::RequestBucketInfoCommand&>(*cmd));
        return req.getAddress()->getIndex();
    });
    return nodes;
}

std::vector<uint16_t>
BucketDBUpdaterTest::getSentNodesWithPreemption(
        const std::string& oldClusterState,
        uint32_t expectedOldStateMessages,
        const std::string& preemptedClusterState,
        const std::string& newClusterState)
{
    lib::ClusterState stateBefore(oldClusterState);
    uint32_t dummyBucketsToReturn = 10;
    setAndEnableClusterState(lib::ClusterState(oldClusterState),
                             expectedOldStateMessages,
                             dummyBucketsToReturn);
    _sender.clear();

    setSystemState(lib::ClusterState(preemptedClusterState));
    _sender.clear();
    // Do not allow the pending state to become the active state; trigger a
    // new transition without ACKing the info requests first. This will
    // overwrite the pending state entirely.
    setSystemState(lib::ClusterState(newClusterState));
    return getSendSet();
}

using nodeVec = std::vector<uint16_t>;

/*
 * If we don't carry over the set of nodes that we need to fetch from,
 * a naive comparison between the active state and the new state will
 * make it appear to the distributor that nothing has changed, as any
 * database modifications caused by intermediate states will not be
 * accounted for (basically the ABA problem in a distributed setting).
 */
void
BucketDBUpdaterTest::preemptedDistrChangeCarriesNodeSetOverToNextStateFetch()
{
    CPPUNIT_ASSERT_EQUAL(
        expandNodeVec({0, 1, 2, 3, 4, 5}),
        getSentNodesWithPreemption("version:1 distributor:6 storage:6",
                                   messageCount(6),
                                   "version:2 distributor:6 .5.s:d storage:6",
                                   "version:3 distributor:6 storage:6"));
}

void
BucketDBUpdaterTest::preemptedStorChangeCarriesNodeSetOverToNextStateFetch()
{
    CPPUNIT_ASSERT_EQUAL(
        expandNodeVec({2, 3}),
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:6 .2.s:d",
                messageCount(5),
                "version:2 distributor:6 storage:6 .2.s:d .3.s:d",
                "version:3 distributor:6 storage:6"));
}

void
BucketDBUpdaterTest::preemptedStorageNodeDownMustBeReFetched()
{
    CPPUNIT_ASSERT_EQUAL(
        expandNodeVec({2}),
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:6",
                messageCount(6),
                "version:2 distributor:6 storage:6 .2.s:d",
                "version:3 distributor:6 storage:6"));
}

void
BucketDBUpdaterTest::doNotSendToPreemptedNodeNowInDownState()
{
    CPPUNIT_ASSERT_EQUAL(
        nodeVec{},
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:6 .2.s:d",
                messageCount(5),
                "version:2 distributor:6 storage:6", // Sends to 2.
                "version:3 distributor:6 storage:6 .2.s:d")); // 2 down again.
}

void
BucketDBUpdaterTest::doNotSendToPreemptedNodeNotPartOfNewState()
{
    // Even though 100 nodes are preempted, not all of these should be part
    // of the request afterwards when only 6 are part of the state.
    CPPUNIT_ASSERT_EQUAL(
        expandNodeVec({0, 1, 2, 3, 4, 5}),
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:100",
                messageCount(100),
                "version:2 distributor:5 .4.s:d storage:100",
                "version:3 distributor:6 storage:6"));
}

void
BucketDBUpdaterTest::outdatedNodeSetClearedAfterSuccessfulStateCompletion()
{
    lib::ClusterState stateBefore(
            "version:1 distributor:6 storage:6 .1.t:1234");
    uint32_t expectedMsgs = messageCount(6), dummyBucketsToReturn = 10;
    setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn);
    _sender.clear();
    // New cluster state that should not by itself trigger any new fetches,
    // unless outdated node set is somehow not cleared after an enabled
    // (completed) cluster state has been set.
    lib::ClusterState stateAfter("version:3 distributor:6 storage:6");
    setSystemState(stateAfter);
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.commands.size());
}

// XXX test currently disabled since distribution config currently isn't used
// at all in order to deduce the set of nodes to send to. This might not matter
// in practice since it is assumed that the cluster state matching the new
// distribution config will follow very shortly after the config has been
// applied to the node. The new cluster state will then send out requests to
// the correct node set.
void
BucketDBUpdaterTest::clusterConfigDownsizeOnlySendsToAvailableNodes()
{
    uint32_t expectedMsgs = 6, dummyBucketsToReturn = 20;
    setAndEnableClusterState(lib::ClusterState("distributor:6 storage:6"),
                             expectedMsgs, dummyBucketsToReturn);
    _sender.clear();

    // Intentionally trigger a racing config change which arrives before the
    // new cluster state representing it.
    std::string distConfig(getDistConfig3Nodes1Group());
    setDistribution(distConfig);
    sortSentMessagesByIndex(_sender);

    CPPUNIT_ASSERT_EQUAL((nodeVec{0, 1, 2}), getSendSet());
}

void
BucketDBUpdaterTest::changedDiskSetTriggersReFetch()
{
    // Same number of online disks, but the set of disks has changed.
    CPPUNIT_ASSERT_EQUAL(
            getNodeList({1}),
            getSentNodes("distributor:2 storage:2 .1.d:3 .1.d.2.s:d",
                         "distributor:2 storage:2 .1.d:3 .1.d.1.s:d"));
}

/**
 * Test scenario where a cluster is downsized by removing a subset of the nodes
 * from the distribution configuration. The system must be able to deal with
 * a scenario where the set of nodes between two cluster states across a config
 * change may differ.
 *
 * See VESPA-790 for details.
 */
void
BucketDBUpdaterTest::nodeMissingFromConfigIsTreatedAsNeedingOwnershipTransfer()
{
    uint32_t expectedMsgs = messageCount(3), dummyBucketsToReturn = 1;
    setAndEnableClusterState(lib::ClusterState("distributor:3 storage:3"),
                             expectedMsgs, dummyBucketsToReturn);
    _sender.clear();

    // Cluster goes from {0, 1, 2} -> {0, 1}. This leaves us with a config
    // that does not contain node 2 while the _active_ cluster state still
    // contains this node.
    const char* downsizeCfg =
        "redundancy 2\n"
        "distributor_auto_ownership_transfer_on_whole_group_down true\n"
        "group[2]\n"
        "group[0].name \"invalid\"\n"
        "group[0].index \"invalid\"\n"
        "group[0].partitions 1|*\n"
        "group[0].nodes[0]\n"
        "group[1].name rack0\n"
        "group[1].index 0\n"
        "group[1].nodes[2]\n"
        "group[1].nodes[0].index 0\n"
        "group[1].nodes[1].index 1\n";

    setDistribution(downsizeCfg);
    sortSentMessagesByIndex(_sender);
    _sender.clear();

    // Attempt to apply state with {0, 1} set. This will compare the new state
    // with the previous state, which still has node 2.
    expectedMsgs = messageCount(2);
    setAndEnableClusterState(lib::ClusterState("distributor:2 storage:2"),
                             expectedMsgs, dummyBucketsToReturn);

    CPPUNIT_ASSERT_EQUAL(expandNodeVec({0, 1}), getSendSet());
}

void
BucketDBUpdaterTest::changed_distributor_set_implies_ownership_transfer()
{
    auto fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:1 storage:2");
    CPPUNIT_ASSERT(fixture->state->hasBucketOwnershipTransfer());

    fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:2 .1.s:d storage:2");
    CPPUNIT_ASSERT(fixture->state->hasBucketOwnershipTransfer());
}

void
BucketDBUpdaterTest::unchanged_distributor_set_implies_no_ownership_transfer()
{
    auto fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:2 storage:1");
    CPPUNIT_ASSERT(!fixture->state->hasBucketOwnershipTransfer());

    fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:2 storage:2 .1.s:d");
    CPPUNIT_ASSERT(!fixture->state->hasBucketOwnershipTransfer());
}

void
BucketDBUpdaterTest::changed_distribution_config_implies_ownership_transfer()
{
    auto fixture = createPendingStateFixtureForDistributionChange(
            "distributor:2 storage:2");
    CPPUNIT_ASSERT(fixture->state->hasBucketOwnershipTransfer());
}

void
BucketDBUpdaterTest::transition_time_tracked_for_single_state_change()
{
    completeStateTransitionInSeconds("distributor:2 storage:2", 5, messageCount(2));

    CPPUNIT_ASSERT_EQUAL(uint64_t(5000), lastTransitionTimeInMillis());
}

void
BucketDBUpdaterTest::transition_time_reset_across_non_preempting_state_changes()
{
    completeStateTransitionInSeconds("distributor:2 storage:2", 5, messageCount(2));
    completeStateTransitionInSeconds("distributor:2 storage:3", 3, messageCount(1));

    CPPUNIT_ASSERT_EQUAL(uint64_t(3000), lastTransitionTimeInMillis());
}

void
BucketDBUpdaterTest::transition_time_tracked_for_distribution_config_change()
{
    lib::ClusterState state("distributor:2 storage:2");
    setAndEnableClusterState(state, messageCount(2), 1);

    _sender.clear();
    std::string distConfig(getDistConfig3Nodes1Group());
    setDistribution(distConfig);
    getClock().addSecondsToTime(4);
    completeBucketInfoGathering(state, messageCount(2));
    CPPUNIT_ASSERT_EQUAL(uint64_t(4000), lastTransitionTimeInMillis());
}

void
BucketDBUpdaterTest::transition_time_tracked_across_preempted_transitions()
{
    _sender.clear();
    lib::ClusterState state("distributor:2 storage:2");
    setSystemState(state);
    getClock().addSecondsToTime(5);
    // Pre-empted with new state here, which will push out the old pending
    // state and replace it with a new one. We should still count the time
    // used processing the old state.
    completeStateTransitionInSeconds("distributor:2 storage:3", 3, messageCount(3));

    CPPUNIT_ASSERT_EQUAL(uint64_t(8000), lastTransitionTimeInMillis());
}

/*
 * Brief reminder on test DSL for checking bucket merge operations:
 *
 *   mergeBucketLists() takes as input strings of the format
 *     <node>:<raw bucket id>/<checksum>/<num docs>/<doc size>|<node>:
 *   and returns a string describing the bucket DB post-merge with the format
 *     <raw bucket id>:<node>/<checksum>/<num docs>/<doc size>,<node>:....|<raw bucket id>:....
 *
 * Yes, the order of node<->bucket id is reversed between the two, perhaps to make sure you're awake.
 */

void BucketDBUpdaterTest::batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted() {
    // Replacing bucket information for content node 0 should not mark existing
    // untrusted replica as trusted as a side effect.
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:1/7/8/9/u,0/1/2/3/u|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:3 .0.s:i"),
                    "0:5/0/0/0|1:5/7/8/9",
                    lib::ClusterState("distributor:1 storage:3 .0.s:u"),
                    "0:5/1/2/3|1:5/7/8/9", true));
}

void BucketDBUpdaterTest::batch_add_of_new_diverging_replicas_does_not_mark_any_as_trusted() {
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:1/7/8/9/u,0/1/2/3/u|"),
            mergeBucketLists("", "0:5/1/2/3|1:5/7/8/9", true));
}

void BucketDBUpdaterTest::batch_add_with_single_resulting_replica_implicitly_marks_as_trusted() {
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:0/1/2/3/t|"),
            mergeBucketLists("", "0:5/1/2/3", true));
}

void BucketDBUpdaterTest::identity_update_of_single_replica_does_not_clear_trusted() {
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:0/1/2/3/t|"),
            mergeBucketLists("0:5/1/2/3", "0:5/1/2/3", true));
}

void BucketDBUpdaterTest::identity_update_of_diverging_untrusted_replicas_does_not_mark_any_as_trusted() {
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:1/7/8/9/u,0/1/2/3/u|"),
            mergeBucketLists("0:5/1/2/3|1:5/7/8/9", "0:5/1/2/3|1:5/7/8/9", true));
}

void BucketDBUpdaterTest::adding_diverging_replica_to_existing_trusted_does_not_remove_trusted() {
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:1/2/3/4/u,0/1/2/3/t|"),
            mergeBucketLists("0:5/1/2/3", "0:5/1/2/3|1:5/2/3/4", true));
}

void BucketDBUpdaterTest::batch_update_from_distributor_change_does_not_mark_diverging_replicas_as_trusted() {
    // This differs from batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted
    // in that _all_ content nodes are considered outdated when distributor changes take place,
    // and therefore a slightly different code path is taken. In particular, bucket info for
    // outdated nodes gets removed before possibly being re-added (if present in the bucket info
    // response).
    CPPUNIT_ASSERT_EQUAL(
            std::string("5:1/7/8/9/u,0/1/2/3/u|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:2 storage:3"),
                    "0:5/1/2/3|1:5/7/8/9",
                    lib::ClusterState("distributor:1 storage:3"),
                    "0:5/1/2/3|1:5/7/8/9", true));
}

}
