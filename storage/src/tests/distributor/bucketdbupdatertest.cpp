// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/bucket_space_distribution_context.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/pending_bucket_space_db_transition.h>
#include <vespa/storage/distributor/outdated_nodes_map.h>
#include <vespa/storage/storageutil/distributorstatecache.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/simpleclusterinformation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <sstream>
#include <iomanip>

using namespace storage::api;
using namespace storage::lib;
using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using document::BucketSpace;
using document::FixedBucketSpaces;
using document::BucketId;
using document::Bucket;

using namespace ::testing;

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

class BucketDBUpdaterTest : public Test,
                            public DistributorTestUtil
{
public:
    BucketDBUpdaterTest();
    ~BucketDBUpdaterTest() override;

    auto &defaultDistributorBucketSpace() { return getBucketSpaceRepo().get(makeBucketSpace()); }

    bool bucketExistsThatHasNode(int bucketCount, uint16_t node) const;

    ClusterInformation::CSP createClusterInfo(const std::string& clusterStateString) {
        lib::ClusterState baselineClusterState(clusterStateString);
        lib::ClusterStateBundle clusterStateBundle(baselineClusterState);
        ClusterInformation::CSP clusterInfo(
                new SimpleClusterInformation(
                        getBucketDBUpdater().getDistributorComponent().getIndex(),
                        clusterStateBundle,
                        "ui"));
        for (auto* repo : {&mutable_repo(), &read_only_repo()}) {
            for (auto& space : *repo) {
                space.second->setClusterState(clusterStateBundle.getDerivedClusterState(space.first));
            }
        }
        return clusterInfo;
    }

    DistributorBucketSpaceRepo& mutable_repo() noexcept { return getBucketSpaceRepo(); }
    // Note: not calling this "immutable_repo" since it may actually be modified by the pending
    // cluster state component (just not by operations), so it would not have the expected semantics.
    DistributorBucketSpaceRepo& read_only_repo() noexcept { return getReadOnlyBucketSpaceRepo(); }

    BucketDatabase& mutable_default_db() noexcept {
        return mutable_repo().get(FixedBucketSpaces::default_space()).getBucketDatabase();
    }
    BucketDatabase& mutable_global_db() noexcept {
        return mutable_repo().get(FixedBucketSpaces::global_space()).getBucketDatabase();
    }
    BucketDatabase& read_only_default_db() noexcept {
        return read_only_repo().get(FixedBucketSpaces::default_space()).getBucketDatabase();
    }
    BucketDatabase& read_only_global_db() noexcept {
        return read_only_repo().get(FixedBucketSpaces::global_space()).getBucketDatabase();
    }

    static std::string getNodeList(std::vector<uint16_t> nodes, size_t count);

    std::string getNodeList(std::vector<uint16_t> nodes);

    std::vector<uint16_t>
    expandNodeVec(const std::vector<uint16_t> &nodes);

    std::vector<document::BucketSpace> _bucketSpaces;

    size_t messageCount(size_t messagesPerBucketSpace) const {
        return messagesPerBucketSpace * _bucketSpaces.size();
    }

    void trigger_completed_but_not_yet_activated_transition(
            vespalib::stringref initial_state, uint32_t initial_buckets, uint32_t initial_expected_msgs,
            vespalib::stringref pending_state, uint32_t pending_buckets, uint32_t pending_expected_msgs);

public:
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;
    void SetUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
        // Disable deferred activation by default (at least for now) to avoid breaking the entire world.
        getBucketDBUpdater().set_stale_reads_enabled(false);
    };

    void TearDown() override {
        close();
    }

    std::shared_ptr<RequestBucketInfoReply> getFakeBucketReply(
            const lib::ClusterState& state,
            const RequestBucketInfoCommand& cmd,
            int storageIndex,
            uint32_t bucketCount,
            uint32_t invalidBucketCount = 0)
    {
        auto sreply = std::make_shared<RequestBucketInfoReply>(cmd);
        sreply->setAddress(storageAddress(storageIndex));

        api::RequestBucketInfoReply::EntryVector &vec = sreply->getBucketInfo();

        for (uint32_t i=0; i<bucketCount + invalidBucketCount; i++) {
            if (!getDistributorBucketSpace().owns_bucket_in_state(state, document::BucketId(16, i))) {
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

        return sreply;
    }

    void fakeBucketReply(const lib::ClusterState &state,
                         const api::StorageCommand &cmd,
                         uint32_t bucketCount,
                         uint32_t invalidBucketCount = 0)
    {
        ASSERT_EQ(cmd.getType(), MessageType::REQUESTBUCKETINFO);
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
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
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

        ASSERT_TRUE(entry.valid());

        bool found = false;
        for (uint32_t j = 0; j<entry->getNodeCount(); j++) {
            if (entry->getNodeRef(j).getNode() == storageNode) {
                ASSERT_FALSE(entry->getNodeRef(j).valid());
                found = true;
            }
        }

        ASSERT_TRUE(found);
    }

    struct OrderByIncreasingNodeIndex {
        template <typename T>
        bool operator()(const T& lhs, const T& rhs) {
            return (lhs->getAddress()->getIndex()
                    < rhs->getAddress()->getIndex());
        }
    };

    void sortSentMessagesByIndex(DistributorMessageSenderStub& sender,
                                 size_t sortFromOffset = 0)
    {
        std::sort(sender.commands().begin() + sortFromOffset,
                  sender.commands().end(),
                  OrderByIncreasingNodeIndex());
    }

    void setSystemState(const lib::ClusterState& state) {
        const size_t sizeBeforeState = _sender.commands().size();
        getBucketDBUpdater().onSetSystemState(
                std::make_shared<api::SetSystemStateCommand>(state));
        // A lot of test logic has the assumption that all messages sent as a
        // result of cluster state changes will be in increasing index order
        // (for simplicity, not because this is required for correctness).
        // Only sort the messages that arrived as a result of the state, don't
        // jumble the sorting with any existing messages.
        sortSentMessagesByIndex(_sender, sizeBeforeState);
    }

    void set_cluster_state_bundle(const lib::ClusterStateBundle& state) {
        const size_t sizeBeforeState = _sender.commands().size();
        getBucketDBUpdater().onSetSystemState(
                std::make_shared<api::SetSystemStateCommand>(state));
        sortSentMessagesByIndex(_sender, sizeBeforeState);
    }

    bool activate_cluster_state_version(uint32_t version) {
        return getBucketDBUpdater().onActivateClusterStateVersion(
                std::make_shared<api::ActivateClusterStateVersionCommand>(version));
    }
    
    void assert_has_activate_cluster_state_reply_with_actual_version(uint32_t version) {
        ASSERT_EQ(size_t(1), _sender.replies().size());
        auto* response = dynamic_cast<api::ActivateClusterStateVersionReply*>(_sender.replies().back().get());
        ASSERT_TRUE(response != nullptr);
        ASSERT_EQ(version, response->actualVersion());
        _sender.clear();
    }

    void completeBucketInfoGathering(const lib::ClusterState& state,
                                     size_t expectedMsgs,
                                     uint32_t bucketCount = 1,
                                     uint32_t invalidBucketCount = 0)
    {
        ASSERT_EQ(expectedMsgs, _sender.commands().size());

        for (uint32_t i = 0; i < _sender.commands().size(); i++) {
            ASSERT_NO_FATAL_FAILURE(fakeBucketReply(state, *_sender.command(i),
                                                    bucketCount, invalidBucketCount));
        }
    }

    void setAndEnableClusterState(const lib::ClusterState& state,
                                  uint32_t expectedMsgs,
                                  uint32_t nBuckets)
    {
        _sender.clear();
        setSystemState(state);
        ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(state, expectedMsgs, nBuckets));
    }

    void completeStateTransitionInSeconds(const std::string& stateStr,
                                          uint32_t seconds,
                                          uint32_t expectedMsgs)
    {
        _sender.clear();
        lib::ClusterState state(stateStr);
        setSystemState(state);
        getClock().addSecondsToTime(seconds);
        ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(state, expectedMsgs));
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
            ASSERT_EQ(_sender.command(i)->getType(), MessageType::REQUESTBUCKETINFO);

            const api::StorageMessageAddress *address = _sender.command(i)->getAddress();
            ASSERT_EQ((uint32_t)(i / _bucketSpaces.size()), (uint32_t)address->getIndex());
        }
    }

    void initializeNodesAndBuckets(uint32_t numStorageNodes,
                                   uint32_t numBuckets)
    {
        ASSERT_NO_FATAL_FAILURE(setStorageNodes(numStorageNodes));

        vespalib::string state(vespalib::make_string(
                "distributor:1 storage:%d", numStorageNodes));
        lib::ClusterState newState(state);

        for (uint32_t i=0; i< messageCount(numStorageNodes); i++) {
            ASSERT_NO_FATAL_FAILURE(fakeBucketReply(newState, *_sender.command(i), numBuckets));
        }
        ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(numBuckets, state));
    }

    bool bucketHasNode(document::BucketId id, uint16_t node) const {
        BucketDatabase::Entry entry = getBucket(id);
        assert(entry.valid());

        for (uint32_t j=0; j<entry->getNodeCount(); j++) {
            if (entry->getNodeRef(j).getNode() == node) {
                return true;
            }
        }

        return false;
    }

    api::StorageMessageAddress storageAddress(uint16_t node) {
        static vespalib::string _storage("storage");
        return api::StorageMessageAddress(&_storage, lib::NodeType::STORAGE, node);
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
            ASSERT_EQ(getIdealStr(document::BucketId(16, i), state),
                      getNodes(document::BucketId(16, i)));
        }
    }

    void setDistribution(const std::string& distConfig) {
        triggerDistributionChange(
                std::make_shared<lib::Distribution>(distConfig));
    }

    std::string getDistConfig6Nodes2Groups() const {
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
        DistributorMessageSenderStub sender;
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
                    clock, clusterInfo, sender,
                    owner.getBucketSpaceRepo(),
                    cmd, outdatedNodesMap, api::Timestamp(1));
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

    std::unique_ptr<PendingClusterStateFixture> createPendingStateFixtureForStateChange(
            const std::string& oldClusterState,
            const std::string& newClusterState)
    {
        return std::make_unique<PendingClusterStateFixture>(*this, oldClusterState, newClusterState);
    }

    std::unique_ptr<PendingClusterStateFixture> createPendingStateFixtureForDistributionChange(
            const std::string& oldClusterState)
    {
        return std::make_unique<PendingClusterStateFixture>(*this, oldClusterState);
    }

    uint32_t populate_bucket_db_via_request_bucket_info_for_benchmarking();

    void complete_recovery_mode() {
        _distributor->scanAllBuckets();
    }
};

BucketDBUpdaterTest::BucketDBUpdaterTest()
    : DistributorTestUtil(),
      _bucketSpaces()
{
}

BucketDBUpdaterTest::~BucketDBUpdaterTest() = default;

TEST_F(BucketDBUpdaterTest, normal_usage) {
    setSystemState(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"));

    ASSERT_EQ(messageCount(3), _sender.commands().size());

    // Ensure distribution hash is set correctly
    ASSERT_EQ(
            defaultDistributorBucketSpace().getDistribution()
            .getNodeGraph().getDistributionConfigHash(),
            dynamic_cast<const RequestBucketInfoCommand&>(
                    *_sender.command(0)).getDistributionHash());

    ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
                                            *_sender.command(0), 10));

    _sender.clear();

    // Optimization for not refetching unneeded data after cluster state
    // change is only implemented after completion of previous cluster state
    setSystemState(lib::ClusterState("distributor:2 .0.s:i storage:3"));

    ASSERT_EQ(messageCount(3), _sender.commands().size());
    // Expect reply of first set SystemState request.
    ASSERT_EQ(size_t(1), _sender.replies().size());

    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(
            lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
            messageCount(3), 10));
    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(10, "distributor:2 storage:3"));
}

TEST_F(BucketDBUpdaterTest, distributor_change) {
    int numBuckets = 100;

    // First sends request
    setSystemState(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"));
    ASSERT_EQ(messageCount(3), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
                                                        messageCount(3), numBuckets));
    _sender.clear();

    // No change from initializing to up (when done with last job)
    setSystemState(lib::ClusterState("distributor:2 storage:3"));
    ASSERT_EQ(size_t(0), _sender.commands().size());
    _sender.clear();

    // Adding node. No new read requests, but buckets thrown
    setSystemState(lib::ClusterState("distributor:3 storage:3"));
    ASSERT_EQ(size_t(0), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(numBuckets, "distributor:3 storage:3"));
    _sender.clear();

    // Removing distributor. Need to refetch new data from all nodes.
    setSystemState(lib::ClusterState("distributor:2 storage:3"));
    ASSERT_EQ(messageCount(3), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(lib::ClusterState("distributor:2 storage:3"),
                                                        messageCount(3), numBuckets));
    _sender.clear();
    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(numBuckets, "distributor:2 storage:3"));
}

TEST_F(BucketDBUpdaterTest, distributor_change_with_grouping) {
    std::string distConfig(getDistConfig6Nodes2Groups());
    setDistribution(distConfig);
    int numBuckets = 100;

    setSystemState(lib::ClusterState("distributor:6 storage:6"));
    ASSERT_EQ(messageCount(6), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(lib::ClusterState("distributor:6 storage:6"),
                                                        messageCount(6), numBuckets));
    _sender.clear();

    // Distributor going down in other group, no change
    setSystemState(lib::ClusterState("distributor:6 .5.s:d storage:6"));
    ASSERT_EQ(size_t(0), _sender.commands().size());
    _sender.clear();

    setSystemState(lib::ClusterState("distributor:6 storage:6"));
    ASSERT_EQ(size_t(0), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(numBuckets, "distributor:6 storage:6"));
    _sender.clear();

    // Unchanged grouping cause no change.
    setDistribution(distConfig);
    ASSERT_EQ(size_t(0), _sender.commands().size());

    // Changed grouping cause change
    setDistribution(getDistConfig6Nodes4Groups());

    ASSERT_EQ(messageCount(6), _sender.commands().size());
}

TEST_F(BucketDBUpdaterTest, normal_usage_initializing) {
    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1 .0.s:i"));

    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

    // Not yet passing on system state.
    ASSERT_EQ(size_t(0), _senderDown.commands().size());

    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                        _bucketSpaces.size(), 10, 10));

    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(10, "distributor:1 storage:1"));

    for (int i=10; i<20; i++) {
        ASSERT_NO_FATAL_FAILURE(verifyInvalid(document::BucketId(16, i), 0));
    }

    // Pass on cluster state and recheck buckets now.
    ASSERT_EQ(size_t(1), _senderDown.commands().size());

    _sender.clear();
    _senderDown.clear();

    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1"));

    // Send a new request bucket info up.
    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                        _bucketSpaces.size(), 20));

    // Pass on cluster state and recheck buckets now.
    ASSERT_EQ(size_t(1), _senderDown.commands().size());

    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(20, "distributor:1 storage:1"));
}

TEST_F(BucketDBUpdaterTest, failed_request_bucket_info) {
    setSystemState(lib::ClusterState("distributor:1 .0.s:i storage:1"));

    // 2 messages sent up: 1 to the nodes, and one reply to the setsystemstate.
    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

    {
        for (uint32_t i = 0; i < _bucketSpaces.size(); ++i) {
            std::shared_ptr<api::RequestBucketInfoReply> reply =
                getFakeBucketReply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                   *((RequestBucketInfoCommand*)_sender.command(i).get()),
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
    ASSERT_EQ(getRequestBucketInfoStrings(messageCount(2)), _sender.getCommands());

    ASSERT_EQ(size_t(0), _senderDown.commands().size());

    for (uint32_t i = 0; i < _bucketSpaces.size(); ++i) {
        ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                *_sender.command(_bucketSpaces.size() + i), 10));
    }

    for (int i=0; i<10; i++) {
        EXPECT_EQ(std::string(""),
                  verifyBucket(document::BucketId(16, i),
                              lib::ClusterState("distributor:1 storage:1")));
    }

    // Set system state should now be passed on
    EXPECT_EQ(std::string("Set system state"), _senderDown.getCommands());
}

TEST_F(BucketDBUpdaterTest, down_while_init) {
    ASSERT_NO_FATAL_FAILURE(setStorageNodes(3));

    ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:1 storage:3"),
                                            *_sender.command(0), 5));

    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:d"));

    ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:1 storage:3"),
                                            *_sender.command(2), 5));

    ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:1 storage:3"),
                                            *_sender.command(1), 5));
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

TEST_F(BucketDBUpdaterTest, node_down) {
    ASSERT_NO_FATAL_FAILURE(setStorageNodes(3));
    enableDistributorClusterState("distributor:1 storage:3");

    for (int i=1; i<100; i++) {
        addIdealNodes(document::BucketId(16, i));
    }

    EXPECT_TRUE(bucketExistsThatHasNode(100, 1));
                   
    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:d"));
    
    EXPECT_FALSE(bucketExistsThatHasNode(100, 1));
}

TEST_F(BucketDBUpdaterTest, storage_node_in_maintenance_clears_buckets_for_node) {
    ASSERT_NO_FATAL_FAILURE(setStorageNodes(3));
    enableDistributorClusterState("distributor:1 storage:3");

    for (int i=1; i<100; i++) {
        addIdealNodes(document::BucketId(16, i));
    }

    EXPECT_TRUE(bucketExistsThatHasNode(100, 1));
                   
    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:m"));

    EXPECT_FALSE(bucketExistsThatHasNode(100, 1));
}

TEST_F(BucketDBUpdaterTest, node_down_copies_get_in_sync) {
    ASSERT_NO_FATAL_FAILURE(setStorageNodes(3));

    lib::ClusterState systemState("distributor:1 storage:3");
    document::BucketId bid(16, 1);

    addNodesToBucketDB(bid, "0=3,1=2,2=3");

    setSystemState(lib::ClusterState("distributor:1 storage:3 .1.s:d"));

    EXPECT_EQ(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=0,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false), "
                        "node(idx=2,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false)"),
            dumpBucket(bid));
}

TEST_F(BucketDBUpdaterTest, initializing_while_recheck) {
   lib::ClusterState systemState("distributor:1 storage:2 .0.s:i .0.i:0.1");
    setSystemState(systemState);

    ASSERT_EQ(messageCount(2), _sender.commands().size());
    ASSERT_EQ(size_t(0), _senderDown.commands().size());

    getBucketDBUpdater().recheckBucketInfo(1, makeDocumentBucket(document::BucketId(16, 3)));

    for (uint32_t i = 0; i < messageCount(2); ++i) {
        ASSERT_NO_FATAL_FAILURE(fakeBucketReply(systemState, *_sender.command(i), 100));
    }

    // Now we can pass on system state.
    ASSERT_EQ(size_t(1), _senderDown.commands().size());
    EXPECT_EQ(MessageType::SETSYSTEMSTATE, _senderDown.command(0)->getType());
}

TEST_F(BucketDBUpdaterTest, bit_change) {
    std::vector<document::BucketId> bucketlist;

    {
        setSystemState(lib::ClusterState("bits:14 storage:1 distributor:2"));

        ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

        for (uint32_t bsi = 0; bsi < _bucketSpaces.size(); ++bsi) {
            ASSERT_EQ(_sender.command(bsi)->getType(), MessageType::REQUESTBUCKETINFO);
            const auto &req = dynamic_cast<const RequestBucketInfoCommand &>(*_sender.command(bsi));
            auto sreply = std::make_shared<RequestBucketInfoReply>(req);
            sreply->setAddress(storageAddress(0));
            auto& vec = sreply->getBucketInfo();
            if (req.getBucketSpace() == FixedBucketSpaces::default_space()) {
                int cnt=0;
                for (int i=0; cnt < 2; i++) {
                    lib::Distribution distribution = defaultDistributorBucketSpace().getDistribution();
                    std::vector<uint16_t> distributors;
                    if (distribution.getIdealDistributorNode(
                        lib::ClusterState("bits:14 storage:1 distributor:2"),
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

            getBucketDBUpdater().onRequestBucketInfoReply(sreply);
        }
    }

    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                         "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
             dumpBucket(bucketlist[0]));
    EXPECT_EQ(std::string("BucketId(0x4000000000000002) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dumpBucket(bucketlist[1]));

    {
        _sender.clear();
        setSystemState(lib::ClusterState("bits:16 storage:1 distributor:2"));

        ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());
        for (uint32_t bsi = 0; bsi < _bucketSpaces.size(); ++bsi) {

            ASSERT_EQ(_sender.command(bsi)->getType(), MessageType::REQUESTBUCKETINFO);
            const auto &req = dynamic_cast<const RequestBucketInfoCommand &>(*_sender.command(bsi));
            auto sreply = std::make_shared<RequestBucketInfoReply>(req);
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

            getBucketDBUpdater().onRequestBucketInfoReply(sreply);
        }
    }

    EXPECT_EQ(std::string("BucketId(0x4000000000000000) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 0)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 1)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000002) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 2)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000004) : "
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

TEST_F(BucketDBUpdaterTest, recheck_node_with_failure) {
    ASSERT_NO_FATAL_FAILURE(initializeNodesAndBuckets(3, 5));

    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(1, makeDocumentBucket(document::BucketId(16, 3)));

    ASSERT_EQ(size_t(1), _sender.commands().size());

    uint16_t index = 0;
    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 3), rbi.getBuckets()[0]);
        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        const api::StorageMessageAddress *address = _sender.command(0)->getAddress();
        index = address->getIndex();
        reply->setResult(api::ReturnCode::NOT_CONNECTED);
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
            // Trigger that delayed message is sent
        getClock().addSecondsToTime(10);
        getBucketDBUpdater().resendDelayedMessages();
    }

    ASSERT_EQ(size_t(2), _sender.commands().size());

    setSystemState(
           lib::ClusterState(vespalib::make_string("distributor:1 storage:3 .%d.s:d", index)));

    // Recheck bucket.
    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(1));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 3), rbi.getBuckets()[0]);
        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        reply->setResult(api::ReturnCode::NOT_CONNECTED);
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
    }

    // Should not retry since node is down.
    EXPECT_EQ(size_t(2), _sender.commands().size());
}

TEST_F(BucketDBUpdaterTest, recheck_node) {
    ASSERT_NO_FATAL_FAILURE(initializeNodesAndBuckets(3, 5));

    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(1, makeDocumentBucket(document::BucketId(16, 3)));

    ASSERT_EQ(size_t(1), _sender.commands().size());

    auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
    ASSERT_EQ(size_t(1), rbi.getBuckets().size());
    EXPECT_EQ(document::BucketId(16, 3), rbi.getBuckets()[0]);

    auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
    reply->getBucketInfo().push_back(
            api::RequestBucketInfoReply::Entry(document::BucketId(16, 3),
                                               api::BucketInfo(20, 10, 12, 50, 60, true, true)));
    getBucketDBUpdater().onRequestBucketInfoReply(reply);

    lib::ClusterState state("distributor:1 storage:3");
    for (uint32_t i = 0; i < 3; i++) {
        EXPECT_EQ(getIdealStr(document::BucketId(16, i), state),
                  getNodes(document::BucketId(16, i)));
    }

    for (uint32_t i = 4; i < 5; i++) {
        EXPECT_EQ(getIdealStr(document::BucketId(16, i), state),
                  getNodes(document::BucketId(16, i)));
    }

    BucketDatabase::Entry entry = getBucketDatabase().get(document::BucketId(16, 3));
    ASSERT_TRUE(entry.valid());

    const BucketCopy* copy = entry->getNode(1);
    ASSERT_TRUE(copy != nullptr);
    EXPECT_EQ(api::BucketInfo(20,10,12, 50, 60, true, true), copy->getBucketInfo());
}

TEST_F(BucketDBUpdaterTest, notify_bucket_change) {
    enableDistributorClusterState("distributor:1 storage:1");

    addNodesToBucketDB(document::BucketId(16, 1), "0=1234");
    _sender.replies().clear();

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
    ASSERT_EQ(size_t(2), _sender.replies().size());

    for (int i = 0; i < 2; ++i) {
        ASSERT_EQ(MessageType::NOTIFYBUCKETCHANGE_REPLY,
                  _sender.reply(i)->getType());
    }

    // No database update until request bucket info replies have been received.
    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=0,crc=0x4d2,docs=1234/1234,bytes=1234/1234,"
                          "trusted=false,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 1)));
    EXPECT_EQ(std::string("NONEXISTING"), dumpBucket(document::BucketId(16, 2)));

    ASSERT_EQ(size_t(2), _sender.commands().size());

    std::vector<api::BucketInfo> infos;
    infos.push_back(api::BucketInfo(4567, 200, 2000, 400, 4000, true, true));
    infos.push_back(api::BucketInfo(8999, 300, 3000, 500, 5000, false, false));

    for (int i = 0; i < 2; ++i) {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(i));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(document::BucketId(16, i + 1), rbi.getBuckets()[0]);

        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        reply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(document::BucketId(16, i + 1),
                        infos[i]));
        getBucketDBUpdater().onRequestBucketInfoReply(reply);
    }

    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=0,crc=0x11d7,docs=200/400,bytes=2000/4000,trusted=true,active=true,ready=true)"),
              dumpBucket(document::BucketId(16, 1)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000002) : "
                          "node(idx=0,crc=0x2327,docs=300/500,bytes=3000/5000,trusted=true,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 2)));
}

TEST_F(BucketDBUpdaterTest, notify_bucket_change_from_node_down) {
    enableDistributorClusterState("distributor:1 storage:2");

    addNodesToBucketDB(document::BucketId(16, 1), "1=1234");

    _sender.replies().clear();

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

    ASSERT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=1,crc=0x4d2,docs=1234/1234,bytes=1234/1234,trusted=false,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 1)));

    ASSERT_EQ(size_t(1), _sender.replies().size());
    ASSERT_EQ(MessageType::NOTIFYBUCKETCHANGE_REPLY, _sender.reply(0)->getType());

    // Currently, this pending operation will be auto-flushed when the cluster state
    // changes so the behavior is still correct. Keep this test around to prevent
    // regressions here.
    ASSERT_EQ(size_t(1), _sender.commands().size());
    auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
    ASSERT_EQ(size_t(1), rbi.getBuckets().size());
    EXPECT_EQ(document::BucketId(16, 1), rbi.getBuckets()[0]);

    auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
    reply->getBucketInfo().push_back(
            api::RequestBucketInfoReply::Entry(
                    document::BucketId(16, 1),
                    api::BucketInfo(8999, 300, 3000, 500, 5000, false, false)));
    getBucketDBUpdater().onRequestBucketInfoReply(reply);

    // No change
    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
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
TEST_F(BucketDBUpdaterTest, notify_change_with_pending_state_queues_bucket_info_requests) {
    setSystemState(lib::ClusterState("distributor:1 storage:1"));
    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

    {
        api::BucketInfo info(8999, 300, 3000, 500, 5000, false, false);
        auto cmd(std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 1)), info));
        cmd->setSourceIndex(0);
        getBucketDBUpdater().onNotifyBucketChange(cmd);
    }

    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(lib::ClusterState("distributor:1 storage:1"),
                                                        _bucketSpaces.size(), 10));

    ASSERT_EQ(_bucketSpaces.size() + 1, _sender.commands().size());

    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(_bucketSpaces.size()));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 1), rbi.getBuckets()[0]);
    }
    _sender.clear();

    // Queue must be cleared once pending state is enabled.
    {
        lib::ClusterState state("distributor:1 storage:2");
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(state, expectedMsgs, dummyBucketsToReturn));
    }
    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());
    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
        EXPECT_EQ(size_t(0), rbi.getBuckets().size());
    }
}

TEST_F(BucketDBUpdaterTest, merge_reply) {
    enableDistributorClusterState("distributor:1 storage:3");

    addNodesToBucketDB(document::BucketId(16, 1234),
                      "0=1234,1=1234,2=1234");

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(api::MergeBucketCommand::Node(0));
    nodes.push_back(api::MergeBucketCommand::Node(1));
    nodes.push_back(api::MergeBucketCommand::Node(2));

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)), nodes, 0);

    auto reply = std::make_shared<api::MergeBucketReply>(cmd);

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(3), _sender.commands().size());

    for (uint32_t i = 0; i < 3; i++) {
        auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.command(i));

        ASSERT_TRUE(req.get() != nullptr);
        ASSERT_EQ(size_t(1), req->getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 1234), req->getBuckets()[0]);

        auto reqreply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(document::BucketId(16, 1234),
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));

        getBucketDBUpdater().onRequestBucketInfoReply(reqreply);
    }

    EXPECT_EQ(std::string("BucketId(0x40000000000004d2) : "
                          "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
                          "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false), "
                          "node(idx=2,crc=0x1e,docs=300/300,bytes=3000/3000,trusted=false,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 1234)));
};

TEST_F(BucketDBUpdaterTest, merge_reply_node_down) {
    enableDistributorClusterState("distributor:1 storage:3");
    std::vector<api::MergeBucketCommand::Node> nodes;

    addNodesToBucketDB(document::BucketId(16, 1234), "0=1234,1=1234,2=1234");

    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)), nodes, 0);

    auto reply = std::make_shared<api::MergeBucketReply>(cmd);

    setSystemState(lib::ClusterState("distributor:1 storage:2"));

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(2), _sender.commands().size());

    for (uint32_t i = 0; i < 2; i++) {
        auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.command(i));

        ASSERT_TRUE(req.get() != nullptr);
        ASSERT_EQ(size_t(1), req->getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 1234), req->getBuckets()[0]);

        auto reqreply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(
                        document::BucketId(16, 1234),
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));
        getBucketDBUpdater().onRequestBucketInfoReply(reqreply);
    }

    EXPECT_EQ(std::string("BucketId(0x40000000000004d2) : "
                          "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
                          "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 1234)));
};

TEST_F(BucketDBUpdaterTest, merge_reply_node_down_after_request_sent) {
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

    ASSERT_EQ(size_t(3), _sender.commands().size());

    setSystemState(lib::ClusterState("distributor:1 storage:2"));

    for (uint32_t i = 0; i < 3; i++) {
        auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.command(i));

        ASSERT_TRUE(req.get() != nullptr);
        ASSERT_EQ(size_t(1), req->getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 1234), req->getBuckets()[0]);

        auto reqreply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(
                        document::BucketId(16, 1234),
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));
        getBucketDBUpdater().onRequestBucketInfoReply(reqreply);
    }

    EXPECT_EQ(std::string("BucketId(0x40000000000004d2) : "
                          "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
                          "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false)"),
              dumpBucket(document::BucketId(16, 1234)));
};


TEST_F(BucketDBUpdaterTest, flush) {
    enableDistributorClusterState("distributor:1 storage:3");
    _sender.clear();

    addNodesToBucketDB(document::BucketId(16, 1234), "0=1234,1=1234,2=1234");

    std::vector<api::MergeBucketCommand::Node> nodes;
    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 1234)), nodes, 0);

    auto reply(std::make_shared<api::MergeBucketReply>(cmd));

    _sender.clear();
    getBucketDBUpdater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(3), _sender.commands().size());
    ASSERT_EQ(size_t(0), _senderDown.replies().size());

    getBucketDBUpdater().flush();
    // Flushing should drop all merge bucket replies
    EXPECT_EQ(size_t(0), _senderDown.commands().size());
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
    for (uint32_t i = 0; i < fixture->sender.commands().size(); i++) {
        auto& req = dynamic_cast<RequestBucketInfoCommand&>(*fixture->sender.command(i));

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
    DistributorMessageSenderStub sender;

    framework::defaultimplementation::FakeClock clock;
    ClusterInformation::CSP clusterInfo(createClusterInfo(oldClusterState));
    std::unique_ptr<PendingClusterState> state(
            PendingClusterState::createForDistributionChange(
                    clock, clusterInfo, sender, getBucketSpaceRepo(), api::Timestamp(1)));

    sortSentMessagesByIndex(sender);

    std::ostringstream ost;
    for (uint32_t i = 0; i < sender.commands().size(); i++) {
        auto& req = dynamic_cast<RequestBucketInfoCommand&>(*sender.command(i));

        if (i > 0) {
            ost << ",";
        }

        ost << req.getAddress()->getIndex();
    }

    return ost.str();
}

TEST_F(BucketDBUpdaterTest, pending_cluster_state_send_messages) {
    EXPECT_EQ(getNodeList({0, 1, 2}),
             getSentNodes("cluster:d",
                          "distributor:1 storage:3"));

    EXPECT_EQ(getNodeList({0, 1}),
             getSentNodes("cluster:d",
                          "distributor:1 storage:3 .2.s:m"));

    EXPECT_EQ(getNodeList({2}),
             getSentNodes("distributor:1 storage:2",
                          "distributor:1 storage:3"));

    EXPECT_EQ(getNodeList({2, 3, 4, 5}),
             getSentNodes("distributor:1 storage:2",
                          "distributor:1 storage:6"));

    EXPECT_EQ(getNodeList({0, 1, 2}),
              getSentNodes("distributor:4 storage:3",
                           "distributor:3 storage:3"));

    EXPECT_EQ(getNodeList({0, 1, 2, 3}),
              getSentNodes("distributor:4 storage:3",
                           "distributor:4 .2.s:d storage:4"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:4 storage:3",
                           "distributor:4 .0.s:d storage:4"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:3 storage:3",
                           "distributor:4 storage:3"));

    EXPECT_EQ(getNodeList({2}),
              getSentNodes("distributor:3 storage:3 .2.s:i",
                           "distributor:3 storage:3"));

    EXPECT_EQ(getNodeList({1}),
              getSentNodes("distributor:3 storage:3 .1.s:d",
                           "distributor:3 storage:3"));

    EXPECT_EQ(getNodeList({1, 2, 4}),
              getSentNodes("distributor:3 storage:4 .1.s:d .2.s:i",
                           "distributor:3 storage:5"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:1 storage:3",
                           "cluster:d"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:1 storage:3",
                           "distributor:1 storage:3"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:1 storage:3",
                           "cluster:d distributor:1 storage:6"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:3 storage:3",
                           "distributor:3 .2.s:m storage:3"));

    EXPECT_EQ(getNodeList({0, 1, 2}),
              getSentNodes("distributor:3 .2.s:m storage:3",
                           "distributor:3 .2.s:d storage:3"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:3 .2.s:m storage:3",
                           "distributor:3 storage:3"));

    EXPECT_EQ(getNodeList({0, 1, 2}),
              getSentNodesDistributionChanged("distributor:3 storage:3"));

    EXPECT_EQ(getNodeList({0, 1}),
              getSentNodes("distributor:10 storage:2",
                           "distributor:10 .1.s:d storage:2"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:2 storage:2",
                           "distributor:3 .2.s:i storage:2"));

    EXPECT_EQ(getNodeList({0, 1, 2}),
              getSentNodes("distributor:3 storage:3",
                           "distributor:3 .2.s:s storage:3"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:3 .2.s:s storage:3",
                           "distributor:3 .2.s:d storage:3"));

    EXPECT_EQ(getNodeList({1}),
              getSentNodes("distributor:3 storage:3 .1.s:m",
                           "distributor:3 storage:3"));

    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:3 storage:3",
                           "distributor:3 storage:3 .1.s:m"));
};

TEST_F(BucketDBUpdaterTest, pending_cluster_state_receive) {
    DistributorMessageSenderStub sender;

    auto cmd(std::make_shared<api::SetSystemStateCommand>(
            lib::ClusterState("distributor:1 storage:3")));

    framework::defaultimplementation::FakeClock clock;
    ClusterInformation::CSP clusterInfo(createClusterInfo("cluster:d"));
    OutdatedNodesMap outdatedNodesMap;
    std::unique_ptr<PendingClusterState> state(
            PendingClusterState::createForClusterStateChange(
                    clock, clusterInfo, sender, getBucketSpaceRepo(),
                    cmd, outdatedNodesMap, api::Timestamp(1)));

    ASSERT_EQ(messageCount(3), sender.commands().size());

    sortSentMessagesByIndex(sender);

    std::ostringstream ost;
    for (uint32_t i = 0; i < sender.commands().size(); i++) {
        auto* req = dynamic_cast<RequestBucketInfoCommand*>(sender.command(i).get());
        ASSERT_TRUE(req != nullptr);

        auto rep = std::make_shared<RequestBucketInfoReply>(*req);

        rep->getBucketInfo().push_back(
                RequestBucketInfoReply::Entry(
                        document::BucketId(16, i),
                        api::BucketInfo(i, i, i, i, i)));

        ASSERT_TRUE(state->onRequestBucketInfoReply(rep));
        ASSERT_EQ((i == (sender.commands().size() - 1)), state->done());
    }

    auto& pendingTransition = state->getPendingBucketSpaceDbTransition(makeBucketSpace());
    EXPECT_EQ(3, (int)pendingTransition.results().size());
}

TEST_F(BucketDBUpdaterTest, pending_cluster_state_with_group_down) {
    std::string config(getDistConfig6Nodes4Groups());
    config += "distributor_auto_ownership_transfer_on_whole_group_down true\n";
    setDistribution(config);

    // Group config has nodes {0, 1}, {2, 3}, {4, 5}
    // We're node index 0.

    // Entire group 1 goes down. Must refetch from all nodes.
    EXPECT_EQ(getNodeList({0, 1, 2, 3, 4, 5}),
              getSentNodes("distributor:6 storage:6",
                           "distributor:6 .2.s:d .3.s:d storage:6"));

    // But don't fetch if not the entire group is down.
    EXPECT_EQ(std::string(""),
              getSentNodes("distributor:6 storage:6",
                           "distributor:6 .2.s:d storage:6"));
}

TEST_F(BucketDBUpdaterTest, pending_cluster_state_with_group_down_and_no_handover) {
    std::string config(getDistConfig6Nodes4Groups());
    config += "distributor_auto_ownership_transfer_on_whole_group_down false\n";
    setDistribution(config);

    // Group is down, but config says to not do anything about it.
    EXPECT_EQ(getNodeList({0, 1, 2, 3, 4, 5}, _bucketSpaces.size() - 1),
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

        uint16_t node = atoi(tok2[0].data());

        state.setNodeReplied(node);
        auto& pendingTransition = state.getPendingBucketSpaceDbTransition(makeBucketSpace());

        vespalib::StringTokenizer tok3(tok2[1], ",");
        for (uint32_t j = 0; j < tok3.size(); j++) {
            if (includeBucketInfo) {
                vespalib::StringTokenizer tok4(tok3[j], "/");

                pendingTransition.addNodeInfo(
                        document::BucketId(16, atoi(tok4[0].data())),
                        BucketCopy(
                                timestamp,
                                node,
                                api::BucketInfo(
                                        atoi(tok4[1].data()),
                                        atoi(tok4[2].data()),
                                        atoi(tok4[3].data()),
                                        atoi(tok4[2].data()),
                                        atoi(tok4[3].data()))));
            } else {
                pendingTransition.addNodeInfo(
                        document::BucketId(16, atoi(tok3[j].data())),
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

    explicit BucketDumper(bool includeBucketInfo)
        : _includeBucketInfo(includeBucketInfo)
    {
    }

    bool process(const BucketDatabase::ConstEntryRef& e) override {
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

    DistributorMessageSenderStub sender;
    OutdatedNodesMap outdatedNodesMap;

    {
        auto cmd(std::make_shared<api::SetSystemStateCommand>(oldState));

        api::Timestamp beforeTime(1);

        ClusterInformation::CSP clusterInfo(createClusterInfo("cluster:d"));
        std::unique_ptr<PendingClusterState> state(
                PendingClusterState::createForClusterStateChange(
                        clock, clusterInfo, sender, getBucketSpaceRepo(),
                        cmd, outdatedNodesMap, beforeTime));

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
                        clock, clusterInfo, sender, getBucketSpaceRepo(),
                        cmd, outdatedNodesMap, afterTime));

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

TEST_F(BucketDBUpdaterTest, pending_cluster_state_merge) {
    // Simple initializing case - ask all nodes for info
    EXPECT_EQ(
            // Result is on the form: [bucket w/o count bits]:[node indexes]|..
            std::string("4:0,1|2:0,1|6:1,2|1:0,2|5:2,0|3:2,1|"),
            // Input is on the form: [node]:[bucket w/o count bits]|...
            mergeBucketLists(
                    "",
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6"));

    // New node came up
    EXPECT_EQ(
            std::string("4:0,1|2:0,1|6:1,2,3|1:0,2,3|5:2,0,3|3:2,1,3|"),
            mergeBucketLists(
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                    "3:1,3,5,6"));

    // Node came up with some buckets removed and some added
    // Buckets that were removed should not be removed as the node
    // didn't lose a disk.
    EXPECT_EQ(
            std::string("8:0|4:0,1|2:0,1|6:1,0,2|1:0,2|5:2,0|3:2,1|"),
            mergeBucketLists(
                    "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                    "0:1,2,6,8"));

    // Bucket info format is "bucketid/checksum/count/size"
    // Node went from initializing to up and invalid bucket went to empty.
    EXPECT_EQ(
            std::string("2:0/0/0/0/t|"),
            mergeBucketLists(
                    "0:2/0/0/1",
                    "0:2/0/0/0",
                    true));

    EXPECT_EQ(std::string("5:1/2/3/4/u,0/0/0/0/u|"),
              mergeBucketLists("", "0:5/0/0/0|1:5/2/3/4", true));
}

TEST_F(BucketDBUpdaterTest, pending_cluster_state_merge_replica_changed) {
    // Node went from initializing to up and non-invalid bucket changed.
    EXPECT_EQ(
            std::string("2:0/2/3/4/t|3:0/2/4/6/t|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:1 .0.s:i"),
                    "0:2/1/2/3,3/2/4/6",
                    lib::ClusterState("distributor:1 storage:1"),
                    "0:2/2/3/4,3/2/4/6",
                    true));
}

TEST_F(BucketDBUpdaterTest, no_db_resurrection_for_bucket_not_owned_in_current_state) {
    document::BucketId bucket(16, 3);
    lib::ClusterState stateBefore("distributor:1 storage:1");
    {
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn));
    }
    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(0, makeDocumentBucket(bucket));

    ASSERT_EQ(size_t(1), _sender.commands().size());
    std::shared_ptr<api::RequestBucketInfoCommand> rbi(
            std::dynamic_pointer_cast<RequestBucketInfoCommand>(
                    _sender.command(0)));

    lib::ClusterState stateAfter("distributor:3 storage:3");

    {
        uint32_t expectedMsgs = messageCount(2), dummyBucketsToReturn = 1;
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateAfter, expectedMsgs, dummyBucketsToReturn));
    }
    EXPECT_FALSE(getDistributorBucketSpace().get_bucket_ownership_flags(bucket).owned_in_current_state());

    ASSERT_NO_FATAL_FAILURE(sendFakeReplyForSingleBucketRequest(*rbi));

    EXPECT_EQ(std::string("NONEXISTING"), dumpBucket(bucket));
}

TEST_F(BucketDBUpdaterTest, no_db_resurrection_for_bucket_not_owned_in_pending_state) {
    document::BucketId bucket(16, 3);
    lib::ClusterState stateBefore("distributor:1 storage:1");
    {
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn));
    }
    _sender.clear();

    getBucketDBUpdater().recheckBucketInfo(0, makeDocumentBucket(bucket));

    ASSERT_EQ(size_t(1), _sender.commands().size());
    std::shared_ptr<api::RequestBucketInfoCommand> rbi(
            std::dynamic_pointer_cast<RequestBucketInfoCommand>(
                    _sender.command(0)));

    lib::ClusterState stateAfter("distributor:3 storage:3");
    // Set, but _don't_ enable cluster state. We want it to be pending.
    setSystemState(stateAfter);
    EXPECT_TRUE(getDistributorBucketSpace().get_bucket_ownership_flags(bucket).owned_in_current_state());
    EXPECT_FALSE(getDistributorBucketSpace().get_bucket_ownership_flags(bucket).owned_in_pending_state());

    ASSERT_NO_FATAL_FAILURE(sendFakeReplyForSingleBucketRequest(*rbi));

    EXPECT_EQ(std::string("NONEXISTING"), dumpBucket(bucket));
}

/*
 * If we get a distribution config change, it's important that cluster states that
 * arrive after this--but _before_ the pending cluster state has finished--must trigger
 * a full bucket info fetch no matter what the cluster state change was! Otherwise, we
 * will with a high likelihood end up not getting the complete view of the buckets in
 * the cluster.
 */
TEST_F(BucketDBUpdaterTest, cluster_state_always_sends_full_fetch_when_distribution_change_pending) {
    lib::ClusterState stateBefore("distributor:6 storage:6");
    {
        uint32_t expectedMsgs = messageCount(6), dummyBucketsToReturn = 1;
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn));
    }
    _sender.clear();
    std::string distConfig(getDistConfig6Nodes2Groups());
    setDistribution(distConfig);

    sortSentMessagesByIndex(_sender);
    ASSERT_EQ(messageCount(6), _sender.commands().size());
    // Suddenly, a wild cluster state change appears! Even though this state
    // does not in itself imply any bucket changes, it will still overwrite the
    // pending cluster state and thus its state of pending bucket info requests.
    setSystemState(lib::ClusterState("distributor:6 .2.t:12345 storage:6"));

    ASSERT_EQ(messageCount(12), _sender.commands().size());

    // Send replies for first messageCount(6) (outdated requests).
    int numBuckets = 10;
    for (uint32_t i = 0; i < messageCount(6); ++i) {
        ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:6 storage:6"),
                                *_sender.command(i), numBuckets));
    }
    // No change from these.
    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(1, "distributor:6 storage:6"));

    // Send for current pending.
    for (uint32_t i = 0; i < messageCount(6); ++i) {
        ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:6 .2.t:12345 storage:6"),
                                                *_sender.command(i + messageCount(6)),
                                                numBuckets));
    }
    ASSERT_NO_FATAL_FAILURE(assertCorrectBuckets(numBuckets, "distributor:6 storage:6"));
    _sender.clear();

    // No more pending global fetch; this should be a no-op state.
    setSystemState(lib::ClusterState("distributor:6 .3.t:12345 storage:6"));
    EXPECT_EQ(size_t(0), _sender.commands().size());
}

TEST_F(BucketDBUpdaterTest, changed_distribution_config_triggers_recovery_mode) {
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(lib::ClusterState("distributor:6 storage:6"), messageCount(6), 20));
    _sender.clear();
    EXPECT_TRUE(_distributor->isInRecoveryMode());
    complete_recovery_mode();
    EXPECT_FALSE(_distributor->isInRecoveryMode());

    std::string distConfig(getDistConfig6Nodes4Groups());
    setDistribution(distConfig);
    sortSentMessagesByIndex(_sender);
    // No replies received yet, still no recovery mode.
    EXPECT_FALSE(_distributor->isInRecoveryMode());

    ASSERT_EQ(messageCount(6), _sender.commands().size());
    uint32_t numBuckets = 10;
    for (uint32_t i = 0; i < messageCount(6); ++i) {
        ASSERT_NO_FATAL_FAILURE(fakeBucketReply(lib::ClusterState("distributor:6 storage:6"),
                                                *_sender.command(i), numBuckets));
    }

    // Pending cluster state (i.e. distribution) has been enabled, which should
    // cause recovery mode to be entered.
    EXPECT_TRUE(_distributor->isInRecoveryMode());
    complete_recovery_mode();
    EXPECT_FALSE(_distributor->isInRecoveryMode());
}

namespace {

template <typename Func>
struct FunctorProcessor : BucketDatabase::EntryProcessor {
    Func _f;

    template <typename F>
    explicit FunctorProcessor(F&& f) : _f(std::forward<F>(f)) {}

    bool process(const BucketDatabase::ConstEntryRef& e) override {
        _f(e);
        return true;
    }
};

template <typename Func>
std::unique_ptr<BucketDatabase::EntryProcessor> func_processor(Func&& f) {
    return std::make_unique<FunctorProcessor<Func>>(std::forward<Func>(f));
}

}

TEST_F(BucketDBUpdaterTest, changed_distribution_config_does_not_elide_bucket_db_pruning) {
    setDistribution(getDistConfig3Nodes1Group());

    constexpr uint32_t n_buckets = 100;
    ASSERT_NO_FATAL_FAILURE(
            setAndEnableClusterState(lib::ClusterState("distributor:6 storage:6"), messageCount(6), n_buckets));
    _sender.clear();

    // Config implies a different node set than the current cluster state, so it's crucial that
    // DB pruning is _not_ elided. Yes, this is inherently racing with cluster state changes and
    // should be changed to be atomic and controlled by the cluster controller instead of config.
    // But this is where we currently are.
    setDistribution(getDistConfig6Nodes2Groups());

    getBucketDatabase().forEach(*func_processor([&](const auto& e) {
        EXPECT_TRUE(getDistributorBucketSpace().get_bucket_ownership_flags(e.getBucketId()).owned_in_pending_state());
    }));
}

TEST_F(BucketDBUpdaterTest, newly_added_buckets_have_current_time_as_gc_timestamp) {
    getClock().setAbsoluteTimeInSeconds(101234);
    lib::ClusterState stateBefore("distributor:1 storage:1");
    {
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 1;
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn));
    }

    // setAndEnableClusterState adds n buckets with id (16, i)
    document::BucketId bucket(16, 0);
    BucketDatabase::Entry e(getBucket(bucket));
    ASSERT_TRUE(e.valid());
    EXPECT_EQ(uint32_t(101234), e->getLastGarbageCollectionTime());
}

TEST_F(BucketDBUpdaterTest, newer_mutations_not_overwritten_by_earlier_bucket_fetch) {
    {
        lib::ClusterState stateBefore("distributor:1 storage:1 .0.s:i");
        uint32_t expectedMsgs = _bucketSpaces.size(), dummyBucketsToReturn = 0;
        // This step is required to make the distributor ready for accepting
        // the below explicit database insertion towards node 0.
        ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateBefore, expectedMsgs,
                                                         dummyBucketsToReturn));
    }
    _sender.clear();
    getClock().setAbsoluteTimeInSeconds(1000);
    lib::ClusterState state("distributor:1 storage:1");
    setSystemState(state);
    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());

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
        ASSERT_NO_FATAL_FAILURE(fakeBucketReply(state, *_sender.command(i), bucketsReturned));
    }

    BucketDatabase::Entry e(getBucket(bucket));
    ASSERT_EQ(uint32_t(1), e->getNodeCount());
    EXPECT_EQ(wantedInfo, e->getNodeRef(0).getBucketInfo());
}

std::vector<uint16_t>
BucketDBUpdaterTest::getSendSet() const
{
    std::vector<uint16_t> nodes;
    std::transform(_sender.commands().begin(),
                   _sender.commands().end(),
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
    // FIXME cannot chain assertion checks in non-void function
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
TEST_F(BucketDBUpdaterTest, preempted_distributor_change_carries_node_set_over_to_next_state_fetch) {
    EXPECT_EQ(
        expandNodeVec({0, 1, 2, 3, 4, 5}),
        getSentNodesWithPreemption("version:1 distributor:6 storage:6",
                                   messageCount(6),
                                   "version:2 distributor:6 .5.s:d storage:6",
                                   "version:3 distributor:6 storage:6"));
}

TEST_F(BucketDBUpdaterTest, preempted_storage_change_carries_node_set_over_to_next_state_fetch) {
    EXPECT_EQ(
        expandNodeVec({2, 3}),
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:6 .2.s:d",
                messageCount(5),
                "version:2 distributor:6 storage:6 .2.s:d .3.s:d",
                "version:3 distributor:6 storage:6"));
}

TEST_F(BucketDBUpdaterTest, preempted_storage_node_down_must_be_re_fetched) {
    EXPECT_EQ(
        expandNodeVec({2}),
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:6",
                messageCount(6),
                "version:2 distributor:6 storage:6 .2.s:d",
                "version:3 distributor:6 storage:6"));
}

TEST_F(BucketDBUpdaterTest, do_not_send_to_preempted_node_now_in_down_state) {
    EXPECT_EQ(
        nodeVec{},
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:6 .2.s:d",
                messageCount(5),
                "version:2 distributor:6 storage:6", // Sends to 2.
                "version:3 distributor:6 storage:6 .2.s:d")); // 2 down again.
}

TEST_F(BucketDBUpdaterTest, doNotSendToPreemptedNodeNotPartOfNewState) {
    // Even though 100 nodes are preempted, not all of these should be part
    // of the request afterwards when only 6 are part of the state.
    EXPECT_EQ(
        expandNodeVec({0, 1, 2, 3, 4, 5}),
        getSentNodesWithPreemption(
                "version:1 distributor:6 storage:100",
                messageCount(100),
                "version:2 distributor:5 .4.s:d storage:100",
                "version:3 distributor:6 storage:6"));
}

TEST_F(BucketDBUpdaterTest, outdated_node_set_cleared_after_successful_state_completion) {
    lib::ClusterState stateBefore(
            "version:1 distributor:6 storage:6 .1.t:1234");
    uint32_t expectedMsgs = messageCount(6), dummyBucketsToReturn = 10;
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(stateBefore, expectedMsgs, dummyBucketsToReturn));
    _sender.clear();
    // New cluster state that should not by itself trigger any new fetches,
    // unless outdated node set is somehow not cleared after an enabled
    // (completed) cluster state has been set.
    lib::ClusterState stateAfter("version:3 distributor:6 storage:6");
    setSystemState(stateAfter);
    EXPECT_EQ(size_t(0), _sender.commands().size());
}

// XXX test currently disabled since distribution config currently isn't used
// at all in order to deduce the set of nodes to send to. This might not matter
// in practice since it is assumed that the cluster state matching the new
// distribution config will follow very shortly after the config has been
// applied to the node. The new cluster state will then send out requests to
// the correct node set.
TEST_F(BucketDBUpdaterTest, DISABLED_cluster_config_downsize_only_sends_to_available_nodes) {
    uint32_t expectedMsgs = 6, dummyBucketsToReturn = 20;
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(lib::ClusterState("distributor:6 storage:6"),
                                                     expectedMsgs, dummyBucketsToReturn));
    _sender.clear();

    // Intentionally trigger a racing config change which arrives before the
    // new cluster state representing it.
    std::string distConfig(getDistConfig3Nodes1Group());
    setDistribution(distConfig);
    sortSentMessagesByIndex(_sender);

    EXPECT_EQ((nodeVec{0, 1, 2}), getSendSet());
}

/**
 * Test scenario where a cluster is downsized by removing a subset of the nodes
 * from the distribution configuration. The system must be able to deal with
 * a scenario where the set of nodes between two cluster states across a config
 * change may differ.
 *
 * See VESPA-790 for details.
 */
TEST_F(BucketDBUpdaterTest, node_missing_from_config_is_treated_as_needing_ownership_transfer) {
    uint32_t expectedMsgs = messageCount(3), dummyBucketsToReturn = 1;
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(lib::ClusterState("distributor:3 storage:3"),
                                                     expectedMsgs, dummyBucketsToReturn));
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
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(lib::ClusterState("distributor:2 storage:2"),
                                                     expectedMsgs, dummyBucketsToReturn));

    EXPECT_EQ(expandNodeVec({0, 1}), getSendSet());
}

TEST_F(BucketDBUpdaterTest, changed_distributor_set_implies_ownership_transfer) {
    auto fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:1 storage:2");
    EXPECT_TRUE(fixture->state->hasBucketOwnershipTransfer());

    fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:2 .1.s:d storage:2");
    EXPECT_TRUE(fixture->state->hasBucketOwnershipTransfer());
}

TEST_F(BucketDBUpdaterTest, unchanged_distributor_set_implies_no_ownership_transfer) {
    auto fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:2 storage:1");
    EXPECT_FALSE(fixture->state->hasBucketOwnershipTransfer());

    fixture = createPendingStateFixtureForStateChange(
            "distributor:2 storage:2", "distributor:2 storage:2 .1.s:d");
    EXPECT_FALSE(fixture->state->hasBucketOwnershipTransfer());
}

TEST_F(BucketDBUpdaterTest, changed_distribution_config_implies_ownership_transfer) {
    auto fixture = createPendingStateFixtureForDistributionChange(
            "distributor:2 storage:2");
    EXPECT_TRUE(fixture->state->hasBucketOwnershipTransfer());
}

TEST_F(BucketDBUpdaterTest, transition_time_tracked_for_single_state_change) {
    ASSERT_NO_FATAL_FAILURE(completeStateTransitionInSeconds("distributor:2 storage:2", 5, messageCount(2)));

    EXPECT_EQ(uint64_t(5000), lastTransitionTimeInMillis());
}

TEST_F(BucketDBUpdaterTest, transition_time_reset_across_non_preempting_state_changes) {
    ASSERT_NO_FATAL_FAILURE(completeStateTransitionInSeconds("distributor:2 storage:2", 5, messageCount(2)));
    ASSERT_NO_FATAL_FAILURE(completeStateTransitionInSeconds("distributor:2 storage:3", 3, messageCount(1)));

    EXPECT_EQ(uint64_t(3000), lastTransitionTimeInMillis());
}

TEST_F(BucketDBUpdaterTest, transition_time_tracked_for_distribution_config_change) {
    lib::ClusterState state("distributor:2 storage:2");
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(state, messageCount(2), 1));

    _sender.clear();
    std::string distConfig(getDistConfig3Nodes1Group());
    setDistribution(distConfig);
    getClock().addSecondsToTime(4);
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(state, messageCount(2)));
    EXPECT_EQ(uint64_t(4000), lastTransitionTimeInMillis());
}

TEST_F(BucketDBUpdaterTest, transition_time_tracked_across_preempted_transitions) {
    _sender.clear();
    lib::ClusterState state("distributor:2 storage:2");
    setSystemState(state);
    getClock().addSecondsToTime(5);
    // Pre-empted with new state here, which will push out the old pending
    // state and replace it with a new one. We should still count the time
    // used processing the old state.
    ASSERT_NO_FATAL_FAILURE(completeStateTransitionInSeconds("distributor:2 storage:3", 3, messageCount(3)));

    EXPECT_EQ(uint64_t(8000), lastTransitionTimeInMillis());
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

TEST_F(BucketDBUpdaterTest, batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted) {
    // Replacing bucket information for content node 0 should not mark existing
    // untrusted replica as trusted as a side effect.
    EXPECT_EQ(
            std::string("5:1/7/8/9/u,0/1/2/3/u|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:1 storage:3 .0.s:i"),
                    "0:5/0/0/0|1:5/7/8/9",
                    lib::ClusterState("distributor:1 storage:3 .0.s:u"),
                    "0:5/1/2/3|1:5/7/8/9", true));
}

TEST_F(BucketDBUpdaterTest, batch_add_of_new_diverging_replicas_does_not_mark_any_as_trusted) {
    EXPECT_EQ(std::string("5:1/7/8/9/u,0/1/2/3/u|"),
              mergeBucketLists("", "0:5/1/2/3|1:5/7/8/9", true));
}

TEST_F(BucketDBUpdaterTest, batch_add_with_single_resulting_replica_implicitly_marks_as_trusted) {
    EXPECT_EQ(std::string("5:0/1/2/3/t|"),
              mergeBucketLists("", "0:5/1/2/3", true));
}

TEST_F(BucketDBUpdaterTest, identity_update_of_single_replica_does_not_clear_trusted) {
    EXPECT_EQ(std::string("5:0/1/2/3/t|"),
              mergeBucketLists("0:5/1/2/3", "0:5/1/2/3", true));
}

TEST_F(BucketDBUpdaterTest, identity_update_of_diverging_untrusted_replicas_does_not_mark_any_as_trusted) {
    EXPECT_EQ(std::string("5:1/7/8/9/u,0/1/2/3/u|"),
              mergeBucketLists("0:5/1/2/3|1:5/7/8/9", "0:5/1/2/3|1:5/7/8/9", true));
}

TEST_F(BucketDBUpdaterTest, adding_diverging_replica_to_existing_trusted_does_not_remove_trusted) {
    EXPECT_EQ(std::string("5:1/2/3/4/u,0/1/2/3/t|"),
              mergeBucketLists("0:5/1/2/3", "0:5/1/2/3|1:5/2/3/4", true));
}

TEST_F(BucketDBUpdaterTest, batch_update_from_distributor_change_does_not_mark_diverging_replicas_as_trusted) {
    // This differs from batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted
    // in that _all_ content nodes are considered outdated when distributor changes take place,
    // and therefore a slightly different code path is taken. In particular, bucket info for
    // outdated nodes gets removed before possibly being re-added (if present in the bucket info
    // response).
    EXPECT_EQ(
            std::string("5:1/7/8/9/u,0/1/2/3/u|"),
            mergeBucketLists(
                    lib::ClusterState("distributor:2 storage:3"),
                    "0:5/1/2/3|1:5/7/8/9",
                    lib::ClusterState("distributor:1 storage:3"),
                    "0:5/1/2/3|1:5/7/8/9", true));
}

// TODO remove on Vespa 8 - this is a workaround for https://github.com/vespa-engine/vespa/issues/8475
TEST_F(BucketDBUpdaterTest, global_distribution_hash_falls_back_to_legacy_format_upon_request_rejection) {
    std::string distConfig(getDistConfig6Nodes2Groups());
    setDistribution(distConfig);

    const vespalib::string current_hash = "(0d*|*(0;0;1;2)(1;3;4;5))";
    const vespalib::string legacy_hash = "(0d3|3|*(0;0;1;2)(1;3;4;5))";

    setSystemState(lib::ClusterState("distributor:6 storage:6"));
    ASSERT_EQ(messageCount(6), _sender.commands().size());

    api::RequestBucketInfoCommand* global_req = nullptr;
    for (auto& cmd : _sender.commands()) {
        auto& req_cmd = dynamic_cast<api::RequestBucketInfoCommand&>(*cmd);
        if (req_cmd.getBucketSpace() == document::FixedBucketSpaces::global_space()) {
            global_req = &req_cmd;
            break;
        }
    }
    ASSERT_TRUE(global_req != nullptr);
    ASSERT_EQ(current_hash, global_req->getDistributionHash());

    auto reply = std::make_shared<api::RequestBucketInfoReply>(*global_req);
    reply->setResult(api::ReturnCode::REJECTED);
    getBucketDBUpdater().onRequestBucketInfoReply(reply);

    getClock().addSecondsToTime(10);
    getBucketDBUpdater().resendDelayedMessages();

    // Should now be a resent request with legacy distribution hash
    ASSERT_EQ(messageCount(6) + 1, _sender.commands().size());
    auto& legacy_req = dynamic_cast<api::RequestBucketInfoCommand&>(*_sender.commands().back());
    ASSERT_EQ(legacy_hash, legacy_req.getDistributionHash());

    // Now if we reject it _again_ we should cycle back to the current hash
    // in case it wasn't a hash-based rejection after all. And the circle of life continues.
    reply = std::make_shared<api::RequestBucketInfoReply>(legacy_req);
    reply->setResult(api::ReturnCode::REJECTED);
    getBucketDBUpdater().onRequestBucketInfoReply(reply);

    getClock().addSecondsToTime(10);
    getBucketDBUpdater().resendDelayedMessages();

    ASSERT_EQ(messageCount(6) + 2, _sender.commands().size());
    auto& new_current_req = dynamic_cast<api::RequestBucketInfoCommand&>(*_sender.commands().back());
    ASSERT_EQ(current_hash, new_current_req.getDistributionHash());
}

namespace {

template <typename Func>
void for_each_bucket(const BucketDatabase& db, const document::BucketSpace& space, Func&& f) {
    BucketId last(0);
    auto e = db.getNext(last);
    while (e.valid()) {
        f(space, e);
        e = db.getNext(e.getBucketId());
    }
}

template <typename Func>
void for_each_bucket(const DistributorBucketSpaceRepo& repo, Func&& f) {
    for (const auto& space : repo) {
        for_each_bucket(space.second->getBucketDatabase(), space.first, f);
    }
}

}

TEST_F(BucketDBUpdaterTest, non_owned_buckets_moved_to_read_only_db_on_ownership_change) {
    getBucketDBUpdater().set_stale_reads_enabled(true);

    lib::ClusterState initial_state("distributor:1 storage:4"); // All buckets owned by us by definition
    set_cluster_state_bundle(lib::ClusterStateBundle(initial_state, {}, false)); // Skip activation step for simplicity

    ASSERT_EQ(messageCount(4), _sender.commands().size());
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(initial_state, messageCount(4), n_buckets));
    _sender.clear();

    EXPECT_EQ(size_t(n_buckets), mutable_default_db().size());
    EXPECT_EQ(size_t(n_buckets), mutable_global_db().size());
    EXPECT_EQ(size_t(0), read_only_default_db().size());
    EXPECT_EQ(size_t(0), read_only_global_db().size());

    lib::ClusterState pending_state("distributor:2 storage:4");

    std::unordered_set<Bucket, Bucket::hash> buckets_not_owned_in_pending_state;
    for_each_bucket(mutable_repo(), [&](const auto& space, const auto& entry) {
        if (!getDistributorBucketSpace().owns_bucket_in_state(pending_state, entry.getBucketId())) {
            buckets_not_owned_in_pending_state.insert(Bucket(space, entry.getBucketId()));
        }
    });
    EXPECT_FALSE(buckets_not_owned_in_pending_state.empty());

    set_cluster_state_bundle(lib::ClusterStateBundle(pending_state, {}, true)); // Now requires activation

    const auto buckets_not_owned_per_space = (buckets_not_owned_in_pending_state.size() / 2); // 2 spaces
    const auto expected_mutable_buckets = n_buckets - buckets_not_owned_per_space;
    EXPECT_EQ(expected_mutable_buckets, mutable_default_db().size());
    EXPECT_EQ(expected_mutable_buckets, mutable_global_db().size());
    EXPECT_EQ(buckets_not_owned_per_space, read_only_default_db().size());
    EXPECT_EQ(buckets_not_owned_per_space, read_only_global_db().size());

    for_each_bucket(read_only_repo(), [&](const auto& space, const auto& entry) {
        EXPECT_TRUE(buckets_not_owned_in_pending_state.find(Bucket(space, entry.getBucketId()))
                    != buckets_not_owned_in_pending_state.end());
    });
}

TEST_F(BucketDBUpdaterTest, buckets_no_longer_available_are_not_moved_to_read_only_database) {
    constexpr uint32_t n_buckets = 10;
    // No ownership change, just node down. Test redundancy is 2, so removing 2 nodes will
    // cause some buckets to be entirely unavailable.
    trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                       "version:2 distributor:1 storage:4 .0.s:d .1.s:m", n_buckets, 0);

    EXPECT_EQ(size_t(0), read_only_default_db().size());
    EXPECT_EQ(size_t(0), read_only_global_db().size());
}

TEST_F(BucketDBUpdaterTest, non_owned_buckets_purged_when_read_only_support_is_config_disabled) {
    getBucketDBUpdater().set_stale_reads_enabled(false);

    lib::ClusterState initial_state("distributor:1 storage:4"); // All buckets owned by us by definition
    set_cluster_state_bundle(lib::ClusterStateBundle(initial_state, {}, false)); // Skip activation step for simplicity

    ASSERT_EQ(messageCount(4), _sender.commands().size());
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(initial_state, messageCount(4), n_buckets));
    _sender.clear();

    // Nothing in read-only DB after first bulk load of buckets.
    EXPECT_EQ(size_t(0), read_only_default_db().size());
    EXPECT_EQ(size_t(0), read_only_global_db().size());

    lib::ClusterState pending_state("distributor:2 storage:4");
    setSystemState(pending_state);
    // No buckets should be moved into read only db after ownership changes.
    EXPECT_EQ(size_t(0), read_only_default_db().size());
    EXPECT_EQ(size_t(0), read_only_global_db().size());
}

void BucketDBUpdaterTest::trigger_completed_but_not_yet_activated_transition(
        vespalib::stringref initial_state_str,
        uint32_t initial_buckets,
        uint32_t initial_expected_msgs,
        vespalib::stringref pending_state_str,
        uint32_t pending_buckets,
        uint32_t pending_expected_msgs)
{
    lib::ClusterState initial_state(initial_state_str);
    setSystemState(initial_state);
    ASSERT_EQ(messageCount(initial_expected_msgs), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(
            initial_state, messageCount(initial_expected_msgs), initial_buckets));
    _sender.clear();

    lib::ClusterState pending_state(pending_state_str); // Ownership change
    set_cluster_state_bundle(lib::ClusterStateBundle(pending_state, {}, true));
    ASSERT_EQ(messageCount(pending_expected_msgs), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(
            pending_state, messageCount(pending_expected_msgs), pending_buckets));
    _sender.clear();
}

TEST_F(BucketDBUpdaterTest, deferred_activated_state_does_not_enable_state_until_activation_received) {
    getBucketDBUpdater().set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
        trigger_completed_but_not_yet_activated_transition("version:1 distributor:2 storage:4", 0, 4,
                                                           "version:2 distributor:1 storage:4", n_buckets, 4));

    // Version should not be switched over yet
    EXPECT_EQ(uint32_t(1), getDistributor().getClusterStateBundle().getVersion());

    EXPECT_EQ(uint64_t(0), mutable_default_db().size());
    EXPECT_EQ(uint64_t(0), mutable_global_db().size());

    EXPECT_FALSE(activate_cluster_state_version(2));

    EXPECT_EQ(uint32_t(2), getDistributor().getClusterStateBundle().getVersion());
    EXPECT_EQ(uint64_t(n_buckets), mutable_default_db().size());
    EXPECT_EQ(uint64_t(n_buckets), mutable_global_db().size());
}

TEST_F(BucketDBUpdaterTest, read_only_db_cleared_once_pending_state_is_activated) {
    getBucketDBUpdater().set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
        trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                           "version:2 distributor:2 storage:4", n_buckets, 0));
    EXPECT_FALSE(activate_cluster_state_version(2));

    EXPECT_EQ(uint64_t(0), read_only_default_db().size());
    EXPECT_EQ(uint64_t(0), read_only_global_db().size());
}

TEST_F(BucketDBUpdaterTest, read_only_db_is_populated_even_when_self_is_marked_down) {
    getBucketDBUpdater().set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
        trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                           "version:2 distributor:1 .0.s:d storage:4", n_buckets, 0));

    // State not yet activated, so read-only DBs have got all the buckets we used to have.
    EXPECT_EQ(uint64_t(0), mutable_default_db().size());
    EXPECT_EQ(uint64_t(0), mutable_global_db().size());
    EXPECT_EQ(uint64_t(n_buckets), read_only_default_db().size());
    EXPECT_EQ(uint64_t(n_buckets), read_only_global_db().size());
}

TEST_F(BucketDBUpdaterTest, activate_cluster_state_request_with_mismatching_version_returns_actual_version) {
    getBucketDBUpdater().set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
        trigger_completed_but_not_yet_activated_transition("version:4 distributor:1 storage:4", n_buckets, 4,
                                                           "version:5 distributor:2 storage:4", n_buckets, 0));

    EXPECT_TRUE(activate_cluster_state_version(4)); // Too old version
    ASSERT_NO_FATAL_FAILURE(assert_has_activate_cluster_state_reply_with_actual_version(5));

    EXPECT_TRUE(activate_cluster_state_version(6)); // More recent version than what has been observed
    ASSERT_NO_FATAL_FAILURE(assert_has_activate_cluster_state_reply_with_actual_version(5));
}

TEST_F(BucketDBUpdaterTest, activate_cluster_state_request_without_pending_transition_passes_message_through) {
    getBucketDBUpdater().set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
        trigger_completed_but_not_yet_activated_transition("version:1 distributor:2 storage:4", 0, 4,
                                                           "version:2 distributor:1 storage:4", n_buckets, 4));
    // Activate version 2; no pending cluster state after this.
    EXPECT_FALSE(activate_cluster_state_version(2));

    // No pending cluster state for version 3, just passed through to be implicitly bounced by state manager.
    // Note: state manager is not modelled in this test, so we just check that the message handler returns
    // false (meaning "didn't take message ownership") and there's no auto-generated reply.
    EXPECT_FALSE(activate_cluster_state_version(3));
    EXPECT_EQ(size_t(0), _sender.replies().size());
}

TEST_F(BucketDBUpdaterTest, DISABLED_benchmark_bulk_loading_into_empty_db) {
    // Need to trigger an initial edge to complete first bucket scan
    ASSERT_NO_FATAL_FAILURE(setAndEnableClusterState(lib::ClusterState("distributor:2 storage:1"),
                                                     messageCount(1), 0));
    _sender.clear();

    lib::ClusterState state("distributor:1 storage:1");
    setSystemState(state);

    constexpr uint32_t superbuckets = 1u << 16u;
    constexpr uint32_t sub_buckets = 14;
    constexpr uint32_t n_buckets = superbuckets * sub_buckets;

    ASSERT_EQ(_bucketSpaces.size(), _sender.commands().size());
    for (uint32_t bsi = 0; bsi < _bucketSpaces.size(); ++bsi) {
        ASSERT_EQ(_sender.command(bsi)->getType(), MessageType::REQUESTBUCKETINFO);
        const auto& req = dynamic_cast<const RequestBucketInfoCommand&>(*_sender.command(bsi));

        auto sreply = std::make_shared<RequestBucketInfoReply>(req);
        sreply->setAddress(storageAddress(0));
        auto& vec = sreply->getBucketInfo();
        if (req.getBucketSpace() == FixedBucketSpaces::default_space()) {
            for (uint32_t sb = 0; sb < superbuckets; ++sb) {
                for (uint64_t i = 0; i < sub_buckets; ++i) {
                    document::BucketId bucket(48, (i << 32ULL) | sb);
                    vec.push_back(api::RequestBucketInfoReply::Entry(bucket, api::BucketInfo(10, 1, 1)));
                }
            }
        }

        vespalib::BenchmarkTimer timer(1.0);
        // Global space has no buckets but will serve as a trigger for merging
        // buckets into the DB. This lets us measure the overhead of just this part.
        if (req.getBucketSpace() == FixedBucketSpaces::global_space()) {
            timer.before();
        }
        getBucketDBUpdater().onRequestBucketInfoReply(sreply);
        if (req.getBucketSpace() == FixedBucketSpaces::global_space()) {
            timer.after();
            fprintf(stderr, "Took %g seconds to merge %u buckets into DB\n", timer.min_time(), n_buckets);
        }
    }

    EXPECT_EQ(size_t(n_buckets), mutable_default_db().size());
    EXPECT_EQ(size_t(0), mutable_global_db().size());
}

uint32_t BucketDBUpdaterTest::populate_bucket_db_via_request_bucket_info_for_benchmarking() {
    // Need to trigger an initial edge to complete first bucket scan
    setAndEnableClusterState(lib::ClusterState("distributor:2 storage:1"), messageCount(1), 0);
    _sender.clear();

    lib::ClusterState state("distributor:1 storage:1");
    setSystemState(state);

    constexpr uint32_t superbuckets = 1u << 16u;
    constexpr uint32_t sub_buckets = 14;
    constexpr uint32_t n_buckets = superbuckets * sub_buckets;

    assert(_bucketSpaces.size() == _sender.commands().size());
    for (uint32_t bsi = 0; bsi < _bucketSpaces.size(); ++bsi) {
        assert(_sender.command(bsi)->getType() == MessageType::REQUESTBUCKETINFO);
        const auto& req = dynamic_cast<const RequestBucketInfoCommand&>(*_sender.command(bsi));

        auto sreply = std::make_shared<RequestBucketInfoReply>(req);
        sreply->setAddress(storageAddress(0));
        auto& vec = sreply->getBucketInfo();
        if (req.getBucketSpace() == FixedBucketSpaces::default_space()) {
            for (uint32_t sb = 0; sb < superbuckets; ++sb) {
                for (uint64_t i = 0; i < sub_buckets; ++i) {
                    document::BucketId bucket(48, (i << 32ULL) | sb);
                    vec.push_back(api::RequestBucketInfoReply::Entry(bucket, api::BucketInfo(10, 1, 1)));
                }
            }
        }
        getBucketDBUpdater().onRequestBucketInfoReply(sreply);
    }

    assert(mutable_default_db().size() == n_buckets);
    assert(mutable_global_db().size() == 0);
    return n_buckets;
}

TEST_F(BucketDBUpdaterTest, DISABLED_benchmark_removing_buckets_for_unavailable_storage_nodes) {
    const uint32_t n_buckets = populate_bucket_db_via_request_bucket_info_for_benchmarking();

    lib::ClusterState no_op_state("distributor:1 storage:1 .0.s:m"); // Removing all buckets via ownership
    vespalib::BenchmarkTimer timer(1.0);
    timer.before();
    setSystemState(no_op_state);
    timer.after();
    fprintf(stderr, "Took %g seconds to scan and remove %u buckets\n", timer.min_time(), n_buckets);
}

TEST_F(BucketDBUpdaterTest, DISABLED_benchmark_no_buckets_removed_during_node_remover_db_pass) {
    const uint32_t n_buckets = populate_bucket_db_via_request_bucket_info_for_benchmarking();

    // TODO this benchmark is void if we further restrict the pruning elision logic to allow
    // elision when storage nodes come online.
    lib::ClusterState no_op_state("distributor:1 storage:2"); // Not removing any buckets
    vespalib::BenchmarkTimer timer(1.0);
    timer.before();
    setSystemState(no_op_state);
    timer.after();
    fprintf(stderr, "Took %g seconds to scan %u buckets with no-op action\n", timer.min_time(), n_buckets);
}

TEST_F(BucketDBUpdaterTest, DISABLED_benchmark_all_buckets_removed_during_node_remover_db_pass) {
    const uint32_t n_buckets = populate_bucket_db_via_request_bucket_info_for_benchmarking();

    lib::ClusterState no_op_state("distributor:1 storage:1 .0.s:m"); // Removing all buckets via all replicas gone
    vespalib::BenchmarkTimer timer(1.0);
    timer.before();
    setSystemState(no_op_state);
    timer.after();
    fprintf(stderr, "Took %g seconds to scan and remove %u buckets\n", timer.min_time(), n_buckets);
}

TEST_F(BucketDBUpdaterTest, pending_cluster_state_getter_is_non_null_only_when_state_is_pending) {
    auto initial_baseline = std::make_shared<lib::ClusterState>("distributor:1 storage:2 .0.s:d");
    auto initial_default = std::make_shared<lib::ClusterState>("distributor:1 storage:2 .0.s:m");

    lib::ClusterStateBundle initial_bundle(*initial_baseline, {{FixedBucketSpaces::default_space(), initial_default},
                                                               {FixedBucketSpaces::global_space(), initial_baseline}});
    set_cluster_state_bundle(initial_bundle);

    auto* state = getBucketDBUpdater().pendingClusterStateOrNull(FixedBucketSpaces::default_space());
    ASSERT_TRUE(state != nullptr);
    EXPECT_EQ(*initial_default, *state);

    state = getBucketDBUpdater().pendingClusterStateOrNull(FixedBucketSpaces::global_space());
    ASSERT_TRUE(state != nullptr);
    EXPECT_EQ(*initial_baseline, *state);

    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(*initial_baseline, messageCount(1), 0));

    state = getBucketDBUpdater().pendingClusterStateOrNull(FixedBucketSpaces::default_space());
    EXPECT_TRUE(state == nullptr);

    state = getBucketDBUpdater().pendingClusterStateOrNull(FixedBucketSpaces::global_space());
    EXPECT_TRUE(state == nullptr);
}

struct BucketDBUpdaterSnapshotTest : BucketDBUpdaterTest {
    lib::ClusterState empty_state;
    std::shared_ptr<lib::ClusterState> initial_baseline;
    std::shared_ptr<lib::ClusterState> initial_default;
    lib::ClusterStateBundle initial_bundle;
    Bucket default_bucket;
    Bucket global_bucket;

    BucketDBUpdaterSnapshotTest()
        : BucketDBUpdaterTest(),
          empty_state(),
          initial_baseline(std::make_shared<lib::ClusterState>("distributor:1 storage:2 .0.s:d")),
          initial_default(std::make_shared<lib::ClusterState>("distributor:1 storage:2 .0.s:m")),
          initial_bundle(*initial_baseline, {{FixedBucketSpaces::default_space(), initial_default},
                                             {FixedBucketSpaces::global_space(), initial_baseline}}),
          default_bucket(FixedBucketSpaces::default_space(), BucketId(16, 1234)),
          global_bucket(FixedBucketSpaces::global_space(), BucketId(16, 1234))
    {
    }
    ~BucketDBUpdaterSnapshotTest() override;

    void SetUp() override {
        BucketDBUpdaterTest::SetUp();
        getBucketDBUpdater().set_stale_reads_enabled(true);
    };

    // Assumes that the distributor owns all buckets, so it may choose any arbitrary bucket in the bucket space
    uint32_t buckets_in_snapshot_matching_current_db(DistributorBucketSpaceRepo& repo, BucketSpace bucket_space) {
        auto rs = getBucketDBUpdater().read_snapshot_for_bucket(Bucket(bucket_space, BucketId(16, 1234)));
        if (!rs.is_routable()) {
            return 0;
        }
        auto guard = rs.steal_read_guard();
        uint32_t found_buckets = 0;
        for_each_bucket(repo, [&](const auto& space, const auto& entry) {
            if (space == bucket_space) {
                auto entries = guard->find_parents_and_self(entry.getBucketId());
                if (entries.size() == 1) {
                    ++found_buckets;
                }
            }
        });
        return found_buckets;
    }
};

BucketDBUpdaterSnapshotTest::~BucketDBUpdaterSnapshotTest() = default;

TEST_F(BucketDBUpdaterSnapshotTest, default_space_snapshot_prior_to_activated_state_is_non_routable) {
    auto rs = getBucketDBUpdater().read_snapshot_for_bucket(default_bucket);
    EXPECT_FALSE(rs.is_routable());
}

TEST_F(BucketDBUpdaterSnapshotTest, global_space_snapshot_prior_to_activated_state_is_non_routable) {
    auto rs = getBucketDBUpdater().read_snapshot_for_bucket(global_bucket);
    EXPECT_FALSE(rs.is_routable());
}

TEST_F(BucketDBUpdaterSnapshotTest, read_snapshot_returns_appropriate_cluster_states) {
    set_cluster_state_bundle(initial_bundle);
    // State currently pending, empty initial state is active

    auto def_rs = getBucketDBUpdater().read_snapshot_for_bucket(default_bucket);
    EXPECT_EQ(def_rs.context().active_cluster_state()->toString(), empty_state.toString());
    EXPECT_EQ(def_rs.context().default_active_cluster_state()->toString(), empty_state.toString());
    ASSERT_TRUE(def_rs.context().has_pending_state_transition());
    EXPECT_EQ(def_rs.context().pending_cluster_state()->toString(), initial_default->toString());

    auto global_rs = getBucketDBUpdater().read_snapshot_for_bucket(global_bucket);
    EXPECT_EQ(global_rs.context().active_cluster_state()->toString(), empty_state.toString());
    EXPECT_EQ(global_rs.context().default_active_cluster_state()->toString(), empty_state.toString());
    ASSERT_TRUE(global_rs.context().has_pending_state_transition());
    EXPECT_EQ(global_rs.context().pending_cluster_state()->toString(), initial_baseline->toString());

    ASSERT_NO_FATAL_FAILURE(completeBucketInfoGathering(*initial_baseline, messageCount(1), 0));
    // State now activated, no pending

    def_rs = getBucketDBUpdater().read_snapshot_for_bucket(default_bucket);
    EXPECT_EQ(def_rs.context().active_cluster_state()->toString(), initial_default->toString());
    EXPECT_EQ(def_rs.context().default_active_cluster_state()->toString(), initial_default->toString());
    EXPECT_FALSE(def_rs.context().has_pending_state_transition());

    global_rs = getBucketDBUpdater().read_snapshot_for_bucket(global_bucket);
    EXPECT_EQ(global_rs.context().active_cluster_state()->toString(), initial_baseline->toString());
    EXPECT_EQ(global_rs.context().default_active_cluster_state()->toString(), initial_default->toString());
    EXPECT_FALSE(global_rs.context().has_pending_state_transition());
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_with_no_pending_state_transition_returns_mutable_db_guard) {
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:2 storage:4", 0, 4,
                                                               "version:2 distributor:1 storage:4", n_buckets, 4));
    EXPECT_FALSE(activate_cluster_state_version(2));
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(mutable_repo(), FixedBucketSpaces::default_space()),
              n_buckets);
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(mutable_repo(), FixedBucketSpaces::global_space()),
              n_buckets);
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_returns_unroutable_for_non_owned_bucket_in_current_state) {
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:2 storage:4", 0, 4,
                                                               "version:2 distributor:2 .0.s:d storage:4", 0, 0));
    EXPECT_FALSE(activate_cluster_state_version(2));
    // We're down in state 2 and therefore do not own any buckets
    auto def_rs = getBucketDBUpdater().read_snapshot_for_bucket(default_bucket);
    EXPECT_FALSE(def_rs.is_routable());
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_with_pending_state_returns_read_only_guard_for_bucket_only_owned_in_current_state) {
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                               "version:2 distributor:2 .0.s:d storage:4", 0, 0));
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(read_only_repo(), FixedBucketSpaces::default_space()),
              n_buckets);
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(read_only_repo(), FixedBucketSpaces::global_space()),
              n_buckets);
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_is_unroutable_if_stale_reads_disabled_and_bucket_not_owned_in_pending_state) {
    getBucketDBUpdater().set_stale_reads_enabled(false);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                               "version:2 distributor:2 .0.s:d storage:4", 0, 0));
    auto def_rs = getBucketDBUpdater().read_snapshot_for_bucket(default_bucket);
    EXPECT_FALSE(def_rs.is_routable());
}

}
