// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/top_level_bucket_db_updater.h>
#include <vespa/storage/distributor/bucket_space_distribution_context.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/node_supported_features_repo.h>
#include <vespa/storage/distributor/pending_bucket_space_db_transition.h>
#include <vespa/storage/distributor/outdated_nodes_map.h>
#include <vespa/storage/storageutil/distributorstatecache.h>
#include <tests/distributor/top_level_distributor_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/metrics/updatehook.h>
#include <vespa/storage/distributor/simpleclusterinformation.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>
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

class TopLevelBucketDBUpdaterTest : public Test,
                                    public TopLevelDistributorTestUtil
{
public:
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;

    TopLevelBucketDBUpdaterTest();
    ~TopLevelBucketDBUpdaterTest() override;

    std::vector<document::BucketSpace> _bucket_spaces;

    size_t message_count(size_t messagesPerBucketSpace) const {
        return messagesPerBucketSpace * _bucket_spaces.size();
    }

    using NodeCount = int;
    using Redundancy = int;

    void SetUp() override {
        create_links();
        _bucket_spaces = bucket_spaces();
        // Disable deferred activation by default (at least for now) to avoid breaking the entire world.
        bucket_db_updater().set_stale_reads_enabled(false);
        setup_distributor(Redundancy(2), NodeCount(10), "cluster:d");
    };

    void TearDown() override {
        close();
    }

    std::shared_ptr<RequestBucketInfoReply> make_fake_bucket_reply(
            const lib::ClusterState& state,
            const RequestBucketInfoCommand& cmd,
            int storageIndex,
            uint32_t bucketCount,
            uint32_t invalidBucketCount = 0)
    {
        auto sreply = std::make_shared<RequestBucketInfoReply>(cmd);
        sreply->setAddress(storage_address(storageIndex));

        auto& vec = sreply->getBucketInfo();

        for (uint32_t i=0; i<bucketCount + invalidBucketCount; i++) {
            document::BucketId bucket(16, i);
            if (!distributor_bucket_space(bucket).owns_bucket_in_state(state, bucket)) {
                continue;
            }

            std::vector<uint16_t> nodes;
            distributor_bucket_space(bucket).getDistribution().getIdealNodes(
                    lib::NodeType::STORAGE, state, bucket, nodes);

            for (uint32_t j = 0; j < nodes.size(); ++j) {
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

    void fake_bucket_reply(const lib::ClusterState &state,
                           const api::StorageCommand &cmd,
                           uint32_t bucket_count,
                           uint32_t invalid_bucket_count = 0)
    {
        ASSERT_EQ(cmd.getType(), MessageType::REQUESTBUCKETINFO);
        const api::StorageMessageAddress& address(*cmd.getAddress());
        bucket_db_updater().onRequestBucketInfoReply(
                make_fake_bucket_reply(state,
                                       dynamic_cast<const RequestBucketInfoCommand &>(cmd),
                                       address.getIndex(),
                                       bucket_count,
                                       invalid_bucket_count));
    }

    void fake_bucket_reply(const lib::ClusterState &state,
                           const api::StorageCommand &cmd,
                           uint32_t bucket_count,
                           const std::function<void(api::RequestBucketInfoReply&)>& reply_decorator)
    {
        ASSERT_EQ(cmd.getType(), MessageType::REQUESTBUCKETINFO);
        const api::StorageMessageAddress& address(*cmd.getAddress());
        auto reply = make_fake_bucket_reply(state,
                                            dynamic_cast<const RequestBucketInfoCommand &>(cmd),
                                            address.getIndex(),
                                            bucket_count, 0);
        reply_decorator(*reply);
        bucket_db_updater().onRequestBucketInfoReply(reply);
    }

    void send_fake_reply_for_single_bucket_request(
            const api::RequestBucketInfoCommand& rbi)
    {
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        const document::BucketId& bucket(rbi.getBuckets()[0]);

        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        reply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(bucket, api::BucketInfo(20, 10, 12, 50, 60, true, true)));
        stripe_of_bucket(bucket).bucket_db_updater().onRequestBucketInfoReply(reply);
    }

    std::string verify_bucket(document::BucketId id, const lib::ClusterState& state) {
        BucketDatabase::Entry entry = get_bucket(id);
        if (!entry.valid()) {
            return vespalib::make_string("%s doesn't exist in DB", id.toString().c_str());
        }

        std::vector<uint16_t> nodes;
        distributor_bucket_space(id).getDistribution().getIdealNodes(
                lib::NodeType::STORAGE, state, document::BucketId(id), nodes);

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

    struct OrderByIncreasingNodeIndex {
        template <typename T>
        bool operator()(const T& lhs, const T& rhs) {
            return (lhs->getAddress()->getIndex()
                    < rhs->getAddress()->getIndex());
        }
    };

    void sort_sent_messages_by_index(DistributorMessageSenderStub& sender,
                                     size_t sortFromOffset = 0)
    {
        std::sort(sender.commands().begin() + sortFromOffset,
                  sender.commands().end(),
                  OrderByIncreasingNodeIndex());
    }

    void set_cluster_state(const lib::ClusterState& state) {
        const size_t size_before_state = _sender.commands().size();
        bucket_db_updater().onSetSystemState(std::make_shared<api::SetSystemStateCommand>(state));
        // A lot of test logic has the assumption that all messages sent as a
        // result of cluster state changes will be in increasing index order
        // (for simplicity, not because this is required for correctness).
        // Only sort the messages that arrived as a result of the state, don't
        // jumble the sorting with any existing messages.
        sort_sent_messages_by_index(_sender, size_before_state);
    }

    void set_cluster_state_bundle(const lib::ClusterStateBundle& state) {
        const size_t size_before_state = _sender.commands().size();
        bucket_db_updater().onSetSystemState(std::make_shared<api::SetSystemStateCommand>(state));
        sort_sent_messages_by_index(_sender, size_before_state);
    }

    void set_cluster_state(const vespalib::string& state_str) {
        set_cluster_state(lib::ClusterState(state_str));
    }

    bool activate_cluster_state_version(uint32_t version) {
        return bucket_db_updater().onActivateClusterStateVersion(
                std::make_shared<api::ActivateClusterStateVersionCommand>(version));
    }

    void assert_has_activate_cluster_state_reply_with_actual_version(uint32_t version) {
        ASSERT_EQ(size_t(1), _sender.replies().size());
        auto* response = dynamic_cast<api::ActivateClusterStateVersionReply*>(_sender.replies().back().get());
        ASSERT_TRUE(response != nullptr);
        ASSERT_EQ(version, response->actualVersion());
        _sender.clear();
    }

    void complete_bucket_info_gathering(const lib::ClusterState& state,
                                        size_t expected_msgs,
                                        uint32_t bucket_count = 1,
                                        uint32_t invalid_bucket_count = 0)
    {
        ASSERT_EQ(expected_msgs, _sender.commands().size());

        for (uint32_t i = 0; i < _sender.commands().size(); i++) {
            ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(state, *_sender.command(i),
                                                      bucket_count, invalid_bucket_count));
        }
    }

    static api::StorageMessageAddress storage_address(uint16_t node) {
        static vespalib::string _storage("storage");
        return api::StorageMessageAddress(&_storage, lib::NodeType::STORAGE, node);
    }

    void assert_correct_buckets(int num_buckets, const std::string& state_str) {
        lib::ClusterState state(state_str);
        for (int i = 0; i < num_buckets; i++) {
            ASSERT_EQ(get_ideal_str(document::BucketId(16, i), state),
                      get_nodes(document::BucketId(16, i)));
        }
    }

    void set_distribution(const std::string& dist_config) {
        trigger_distribution_change(std::make_shared<lib::Distribution>(dist_config));
    }

    void verify_invalid(document::BucketId id, int storageNode) {
        BucketDatabase::Entry entry = get_bucket(id);
        ASSERT_TRUE(entry.valid());
        bool found = false;
        for (uint32_t j = 0; j < entry->getNodeCount(); j++) {
            if (entry->getNodeRef(j).getNode() == storageNode) {
                ASSERT_FALSE(entry->getNodeRef(j).valid());
                found = true;
            }
        }

        ASSERT_TRUE(found);
    }

    void set_storage_nodes(uint32_t numStorageNodes) {
        _sender.clear();
        set_cluster_state(lib::ClusterState(vespalib::make_string("distributor:1 storage:%d", numStorageNodes)));

        for (uint32_t i=0; i< message_count(numStorageNodes); i++) {
            ASSERT_EQ(_sender.command(i)->getType(), MessageType::REQUESTBUCKETINFO);

            const api::StorageMessageAddress *address = _sender.command(i)->getAddress();
            ASSERT_EQ(i / _bucket_spaces.size(), address->getIndex());
        }
    }

    bool bucket_has_node(document::BucketId id, uint16_t node) const {
        BucketDatabase::Entry entry = get_bucket(id);
        assert(entry.valid());

        for (uint32_t j = 0; j < entry->getNodeCount(); ++j) {
            if (entry->getNodeRef(j).getNode() == node) {
                return true;
            }
        }
        return false;
    }

    bool bucket_exists_that_has_node(int bucket_count, uint16_t node) const {
        for (int i = 1; i < bucket_count; ++i) {
            if (bucket_has_node(document::BucketId(16, i), node)) {
                return true;
            }
        }
        return false;
    }

    std::string dump_bucket(const document::BucketId& id) const {
        return get_bucket(id).toString();
    }

    void initialize_nodes_and_buckets(uint32_t num_storage_nodes, uint32_t num_buckets) {
        ASSERT_NO_FATAL_FAILURE(set_storage_nodes(num_storage_nodes));

        vespalib::string state(vespalib::make_string("distributor:1 storage:%d", num_storage_nodes));
        lib::ClusterState new_state(state);

        for (uint32_t i = 0; i < message_count(num_storage_nodes); ++i) {
            ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(new_state, *_sender.command(i), num_buckets));
        }
        ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(num_buckets, state));
    }

    void set_and_enable_cluster_state(const lib::ClusterState& state, uint32_t expected_msgs, uint32_t n_buckets) {
        _sender.clear();
        set_cluster_state(state);
        ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(state, expected_msgs, n_buckets));
    }

    void complete_state_transition_in_seconds(const std::string& stateStr,
                                              uint32_t seconds,
                                              uint32_t expectedMsgs)
    {
        _sender.clear();
        lib::ClusterState state(stateStr);
        set_cluster_state(state);
        fake_clock().addSecondsToTime(seconds);
        ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(state, expectedMsgs));
    }

    uint64_t last_transition_time_in_millis() {
        {
            // Force stripe metrics to be aggregated into total.
            std::mutex l;
            distributor_metric_update_hook().updateMetrics(metrics::MetricLockGuard(l));
        }
        return uint64_t(total_distributor_metrics().stateTransitionTime.getLast());
    }

    ClusterInformation::CSP create_cluster_info(const std::string& clusterStateString) {
        lib::ClusterState baseline_cluster_state(clusterStateString);
        lib::ClusterStateBundle cluster_state_bundle(baseline_cluster_state);
        auto cluster_info = std::make_shared<SimpleClusterInformation>(
                _distributor->node_identity().node_index(),
                cluster_state_bundle,
                "ui");
        enable_distributor_cluster_state(clusterStateString);
        return cluster_info;
    }

    struct PendingClusterStateFixture {
        DistributorMessageSenderStub sender;
        framework::defaultimplementation::FakeClock clock;
        std::unique_ptr<PendingClusterState> state;

        PendingClusterStateFixture(
                TopLevelBucketDBUpdaterTest& owner,
                const std::string& old_cluster_state,
                const std::string& new_cluster_state)
        {
            auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(new_cluster_state));
            auto cluster_info = owner.create_cluster_info(old_cluster_state);
            OutdatedNodesMap outdated_nodes_map;
            state = PendingClusterState::createForClusterStateChange(
                    clock, cluster_info, sender,
                    owner.bucket_space_states(),
                    cmd, outdated_nodes_map, api::Timestamp(1));
        }

        PendingClusterStateFixture(
                TopLevelBucketDBUpdaterTest& owner,
                const std::string& old_cluster_state)
        {
            auto cluster_info = owner.create_cluster_info(old_cluster_state);
            state = PendingClusterState::createForDistributionChange(
                    clock, cluster_info, sender, owner.bucket_space_states(), api::Timestamp(1));
        }
    };

    std::unique_ptr<PendingClusterStateFixture> create_pending_state_fixture_for_state_change(
            const std::string& oldClusterState,
            const std::string& newClusterState)
    {
        return std::make_unique<PendingClusterStateFixture>(*this, oldClusterState, newClusterState);
    }

    std::unique_ptr<PendingClusterStateFixture> create_pending_state_fixture_for_distribution_change(
            const std::string& oldClusterState)
    {
        return std::make_unique<PendingClusterStateFixture>(*this, oldClusterState);
    }

    std::string get_sent_nodes(const std::string& old_cluster_state,
                               const std::string& new_cluster_state);

    std::string get_sent_nodes_distribution_changed(const std::string& old_cluster_state);

    std::string get_node_list(const std::vector<uint16_t>& nodes, size_t count);
    std::string get_node_list(const std::vector<uint16_t>& nodes);

    std::string merge_bucket_lists(const lib::ClusterState& old_state,
                                   const std::string& existing_data,
                                   const lib::ClusterState& new_state,
                                   const std::string& new_data,
                                   bool include_bucket_info = false);

    std::string merge_bucket_lists(const std::string& existingData,
                                   const std::string& newData,
                                   bool includeBucketInfo = false);

    std::vector<uint16_t> get_send_set() const;

    std::vector<uint16_t> get_sent_nodes_with_preemption(
            const std::string& old_cluster_state,
            uint32_t expected_old_state_messages,
            const std::string& preempted_cluster_state,
            const std::string& new_cluster_state);

    std::vector<uint16_t> expand_node_vec(const std::vector<uint16_t>& nodes);

    void trigger_completed_but_not_yet_activated_transition(
            vespalib::stringref initial_state_str,
            uint32_t initial_buckets,
            uint32_t initial_expected_msgs,
            vespalib::stringref pending_state_str,
            uint32_t pending_buckets,
            uint32_t pending_expected_msgs);

    const DistributorBucketSpaceRepo& mutable_repo(DistributorStripe& s) const noexcept {
        return s.getBucketSpaceRepo();
    }
    // Note: not calling this "immutable_repo" since it may actually be modified by the pending
    // cluster state component (just not by operations), so it would not have the expected semantics.
    const DistributorBucketSpaceRepo& read_only_repo(DistributorStripe& s) const noexcept {
        return s.getReadOnlyBucketSpaceRepo();
    }

    const BucketDatabase& mutable_default_db(DistributorStripe& s) const noexcept {
        return mutable_repo(s).get(FixedBucketSpaces::default_space()).getBucketDatabase();
    }
    const BucketDatabase& mutable_global_db(DistributorStripe& s) const noexcept {
        return mutable_repo(s).get(FixedBucketSpaces::global_space()).getBucketDatabase();
    }
    const BucketDatabase& read_only_default_db(DistributorStripe& s) const noexcept {
        return read_only_repo(s).get(FixedBucketSpaces::default_space()).getBucketDatabase();
    }
    const BucketDatabase& read_only_global_db(DistributorStripe& s) const noexcept {
        return read_only_repo(s).get(FixedBucketSpaces::global_space()).getBucketDatabase();
    }

    void set_stale_reads_enabled(bool enabled) {
        for (auto* s : distributor_stripes()) {
            s->bucket_db_updater().set_stale_reads_enabled(enabled);
        }
        bucket_db_updater().set_stale_reads_enabled(enabled);
    }

    size_t mutable_default_dbs_size() const {
        size_t total = 0;
        for (auto* s : distributor_stripes()) {
            total += mutable_default_db(*s).size();
        }
        return total;
    }

    size_t mutable_global_dbs_size() const {
        size_t total = 0;
        for (auto* s : distributor_stripes()) {
            total += mutable_global_db(*s).size();
        }
        return total;
    }

    size_t read_only_default_dbs_size() const {
        size_t total = 0;
        for (auto* s : distributor_stripes()) {
            total += read_only_default_db(*s).size();
        }
        return total;
    }

    size_t read_only_global_dbs_size() const {
        size_t total = 0;
        for (auto* s : distributor_stripes()) {
            total += read_only_global_db(*s).size();
        }
        return total;
    }

};

TopLevelBucketDBUpdaterTest::TopLevelBucketDBUpdaterTest()
    : TopLevelDistributorTestUtil(),
      _bucket_spaces()
{
}

TopLevelBucketDBUpdaterTest::~TopLevelBucketDBUpdaterTest() = default;

namespace {

std::string dist_config_6_nodes_across_2_groups() {
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

std::string dist_config_6_nodes_across_4_groups() {
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

std::string dist_config_3_nodes_in_1_group() {
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

std::string
make_string_list(std::string s, uint32_t count)
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
make_request_bucket_info_strings(uint32_t count)
{
    return make_string_list("Request bucket info", count);
}

}


std::string
TopLevelBucketDBUpdaterTest::get_node_list(const std::vector<uint16_t>& nodes, size_t count)
{
    std::ostringstream ost;
    bool first = true;
    for (const auto node : nodes) {
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
TopLevelBucketDBUpdaterTest::get_node_list(const std::vector<uint16_t>& nodes)
{
    return get_node_list(nodes, _bucket_spaces.size());
}

void
TopLevelBucketDBUpdaterTest::trigger_completed_but_not_yet_activated_transition(
        vespalib::stringref initial_state_str,
        uint32_t initial_buckets,
        uint32_t initial_expected_msgs,
        vespalib::stringref pending_state_str,
        uint32_t pending_buckets,
        uint32_t pending_expected_msgs)
{
    lib::ClusterState initial_state(initial_state_str);
    set_cluster_state(initial_state);
    ASSERT_EQ(message_count(initial_expected_msgs), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(
            initial_state, message_count(initial_expected_msgs), initial_buckets));
    _sender.clear();

    lib::ClusterState pending_state(pending_state_str); // Ownership change
    set_cluster_state_bundle(lib::ClusterStateBundle(pending_state, {}, true));
    ASSERT_EQ(message_count(pending_expected_msgs), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(
            pending_state, message_count(pending_expected_msgs), pending_buckets));
    _sender.clear();
}

TEST_F(TopLevelBucketDBUpdaterTest, normal_usage) {
    set_cluster_state(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3")); // FIXME init mode why?

    ASSERT_EQ(message_count(3), _sender.commands().size());

    // Ensure distribution hash is set correctly
    ASSERT_EQ(_component->getDistribution()->getNodeGraph().getDistributionConfigHash(),
              dynamic_cast<const RequestBucketInfoCommand&>(*_sender.command(0)).getDistributionHash());

    ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"), // FIXME init mode why?
                                              *_sender.command(0), 10));

    _sender.clear();

    // Optimization for not refetching unneeded data after cluster state
    // change is only implemented after completion of previous cluster state
    set_cluster_state("distributor:2 .0.s:i storage:3"); // FIXME init mode why?

    ASSERT_EQ(message_count(3), _sender.commands().size());
    // Expect reply of first set SystemState request.
    ASSERT_EQ(size_t(1), _sender.replies().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(
            lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"), // FIXME init mode why?
            message_count(3), 10));
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(10, "distributor:2 storage:3"));
}

TEST_F(TopLevelBucketDBUpdaterTest, distributor_change) {
    int num_buckets = 100;

    // First sends request
    set_cluster_state("distributor:2 .0.s:i .1.s:i storage:3"); // FIXME init mode why?
    ASSERT_EQ(message_count(3), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"), // FIXME init mode why?
                                                           message_count(3), num_buckets));
    _sender.clear();

    // No change from initializing to up (when done with last job)
    set_cluster_state("distributor:2 storage:3");
    ASSERT_EQ(size_t(0), _sender.commands().size());
    _sender.clear();

    // Adding node. No new read requests, but buckets thrown
    set_cluster_state("distributor:3 storage:3");
    ASSERT_EQ(size_t(0), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(num_buckets, "distributor:3 storage:3"));
    _sender.clear();

    // Removing distributor. Need to refetch new data from all nodes.
    set_cluster_state("distributor:2 storage:3");
    ASSERT_EQ(message_count(3), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:2 storage:3"),
                                                           message_count(3), num_buckets));
    _sender.clear();
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(num_buckets, "distributor:2 storage:3"));
}

TEST_F(TopLevelBucketDBUpdaterTest, distributor_change_with_grouping) {
    set_distribution(dist_config_6_nodes_across_2_groups());
    int numBuckets = 100;

    set_cluster_state("distributor:6 storage:6");
    ASSERT_EQ(message_count(6), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:6 storage:6"),
                                                           message_count(6), numBuckets));
    _sender.clear();

    // Distributor going down in other group, no change
    set_cluster_state("distributor:6 .5.s:d storage:6");
    ASSERT_EQ(size_t(0), _sender.commands().size());
    _sender.clear();

    set_cluster_state("distributor:6 storage:6");
    ASSERT_EQ(size_t(0), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(numBuckets, "distributor:6 storage:6"));
    _sender.clear();

    // Unchanged grouping cause no change.
    set_distribution(dist_config_6_nodes_across_2_groups());
    ASSERT_EQ(size_t(0), _sender.commands().size());

    // Changed grouping cause change
    set_distribution(dist_config_6_nodes_across_4_groups());

    ASSERT_EQ(message_count(6), _sender.commands().size());
}

TEST_F(TopLevelBucketDBUpdaterTest, normal_usage_initializing) {
    set_cluster_state("distributor:1 .0.s:i storage:1 .0.s:i"); // FIXME init mode why?

    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    // Not yet passing on system state.
    ASSERT_EQ(size_t(0), _sender_down.commands().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:1 .0.s:i storage:1"), // FIXME init mode why?
                                                           _bucket_spaces.size(), 10, 10));

    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(10, "distributor:1 storage:1"));

    for (int i = 10; i < 20; ++i) {
        ASSERT_NO_FATAL_FAILURE(verify_invalid(document::BucketId(16, i), 0));
    }

    // Pass on cluster state and recheck buckets now.
    ASSERT_EQ(size_t(1), _sender_down.commands().size());

    _sender.clear();
    _sender_down.clear();

    set_cluster_state("distributor:1 .0.s:i storage:1"); // FIXME init mode why?

    // Send a new request bucket info up.
    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:1 .0.s:i storage:1"), // FIXME init mode why?
                                                           _bucket_spaces.size(), 20));

    // Pass on cluster state and recheck buckets now.
    ASSERT_EQ(size_t(1), _sender_down.commands().size());

    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(20, "distributor:1 storage:1"));
}

TEST_F(TopLevelBucketDBUpdaterTest, failed_request_bucket_info) {
    set_cluster_state("distributor:1 .0.s:i storage:1"); // FIXME init mode why?

    // 2 messages sent up: 1 to the nodes, and one reply to the setsystemstate.
    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    {
        for (uint32_t i = 0; i < _bucket_spaces.size(); ++i) {
            auto reply = make_fake_bucket_reply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(i)),
                                                0,
                                                10);
            reply->setResult(api::ReturnCode::NOT_CONNECTED);
            bucket_db_updater().onRequestBucketInfoReply(reply);
        }

        // Trigger that delayed message is sent
        fake_clock().addSecondsToTime(10);
        bucket_db_updater().resend_delayed_messages();
    }

    // Should be resent.
    ASSERT_EQ(make_request_bucket_info_strings(message_count(2)), _sender.getCommands());

    ASSERT_EQ(size_t(0), _sender_down.commands().size());

    for (uint32_t i = 0; i < _bucket_spaces.size(); ++i) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:1 .0.s:i storage:1"), // FIXME init mode why?
                                                  *_sender.command(_bucket_spaces.size() + i), 10));
    }

    for (int i=0; i<10; i++) {
        EXPECT_EQ("",
                  verify_bucket(document::BucketId(16, i),
                                lib::ClusterState("distributor:1 storage:1")));
    }

    // Set system state should now be passed on
    EXPECT_EQ("Set system state", _sender_down.getCommands());
}

TEST_F(TopLevelBucketDBUpdaterTest, down_while_init) {
    ASSERT_NO_FATAL_FAILURE(set_storage_nodes(3));

    ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:1 storage:3"),
                                              *_sender.command(0), 5));

    set_cluster_state("distributor:1 storage:3 .1.s:d");

    ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:1 storage:3"),
                                              *_sender.command(2), 5));

    ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:1 storage:3"),
                                              *_sender.command(1), 5));
}

TEST_F(TopLevelBucketDBUpdaterTest, node_down) {
    ASSERT_NO_FATAL_FAILURE(set_storage_nodes(3));
    enable_distributor_cluster_state("distributor:1 storage:3");

    for (int i = 1; i < 100; ++i) {
        add_ideal_nodes(document::BucketId(16, i));
    }

    EXPECT_TRUE(bucket_exists_that_has_node(100, 1));

    set_cluster_state("distributor:1 storage:3 .1.s:d");

    EXPECT_FALSE(bucket_exists_that_has_node(100, 1));
}


TEST_F(TopLevelBucketDBUpdaterTest, storage_node_in_maintenance_clears_buckets_for_node) {
    ASSERT_NO_FATAL_FAILURE(set_storage_nodes(3));
    enable_distributor_cluster_state("distributor:1 storage:3");

    for (int i = 1; i < 100; ++i) {
        add_ideal_nodes(document::BucketId(16, i));
    }

    EXPECT_TRUE(bucket_exists_that_has_node(100, 1));

    set_cluster_state("distributor:1 storage:3 .1.s:m");

    EXPECT_FALSE(bucket_exists_that_has_node(100, 1));
}

TEST_F(TopLevelBucketDBUpdaterTest, node_down_copies_get_in_sync) {
    ASSERT_NO_FATAL_FAILURE(set_storage_nodes(3));
    document::BucketId bid(16, 1);

    add_nodes_to_stripe_bucket_db(bid, "0=3,1=2,2=3");

    set_cluster_state("distributor:1 storage:3 .1.s:d");

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false), "
              "node(idx=2,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false)",
              dump_bucket(bid));
}

TEST_F(TopLevelBucketDBUpdaterTest, initializing_while_recheck) {
    lib::ClusterState state("distributor:1 storage:2 .0.s:i .0.i:0.1");
    set_cluster_state(state);

    ASSERT_EQ(message_count(2), _sender.commands().size());
    ASSERT_EQ(size_t(0), _sender_down.commands().size());

    auto bucket = makeDocumentBucket(document::BucketId(16, 3));
    stripe_of_bucket(bucket.getBucketId()).bucket_db_updater().recheckBucketInfo(1, bucket);

    for (uint32_t i = 0; i < message_count(2); ++i) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(state, *_sender.command(i), 100));
    }

    // Now we can pass on system state.
    ASSERT_EQ(size_t(1), _sender_down.commands().size());
    EXPECT_EQ(MessageType::SETSYSTEMSTATE, _sender_down.command(0)->getType());
}

TEST_F(TopLevelBucketDBUpdaterTest, bit_change) {
    std::vector<document::BucketId> bucketlist;

    {
        set_cluster_state("bits:14 storage:1 distributor:2");

        ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

        for (uint32_t bsi = 0; bsi < _bucket_spaces.size(); ++bsi) {
            ASSERT_EQ(_sender.command(bsi)->getType(), MessageType::REQUESTBUCKETINFO);
            const auto &req = dynamic_cast<const RequestBucketInfoCommand &>(*_sender.command(bsi));
            auto sreply = std::make_shared<RequestBucketInfoReply>(req);
            sreply->setAddress(storage_address(0));
            auto& vec = sreply->getBucketInfo();
            if (req.getBucketSpace() == FixedBucketSpaces::default_space()) {
                int cnt=0;
                for (int i=0; cnt < 2; i++) {
                    auto distribution = _component->getDistribution();
                    std::vector<uint16_t> distributors;
                    if (distribution->getIdealDistributorNode(
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

            bucket_db_updater().onRequestBucketInfoReply(sreply);
        }
    }

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)",
              dump_bucket(bucketlist[0]));
    EXPECT_EQ("BucketId(0x4000000000000002) : "
              "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)",
              dump_bucket(bucketlist[1]));

    {
        _sender.clear();
        set_cluster_state("bits:16 storage:1 distributor:2");

        ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());
        for (uint32_t bsi = 0; bsi < _bucket_spaces.size(); ++bsi) {

            ASSERT_EQ(_sender.command(bsi)->getType(), MessageType::REQUESTBUCKETINFO);
            const auto &req = dynamic_cast<const RequestBucketInfoCommand &>(*_sender.command(bsi));
            auto sreply = std::make_shared<RequestBucketInfoReply>(req);
            sreply->setAddress(storage_address(0));
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

            bucket_db_updater().onRequestBucketInfoReply(sreply);
        }
    }

    EXPECT_EQ("BucketId(0x4000000000000000) : "
              "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)",
              dump_bucket(document::BucketId(16, 0)));
    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)",
              dump_bucket(document::BucketId(16, 1)));
    EXPECT_EQ("BucketId(0x4000000000000002) : "
              "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)",
              dump_bucket(document::BucketId(16, 2)));
    EXPECT_EQ("BucketId(0x4000000000000004) : "
              "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)",
              dump_bucket(document::BucketId(16, 4)));

    _sender.clear();
    set_cluster_state("storage:1 distributor:2 .1.s:i");

    _sender.clear();
    set_cluster_state("storage:1 distributor:2");
};

TEST_F(TopLevelBucketDBUpdaterTest, recheck_node_with_failure) {
    ASSERT_NO_FATAL_FAILURE(initialize_nodes_and_buckets(3, 5));

    _sender.clear();

    auto bucket = makeDocumentBucket(document::BucketId(16, 3));
    auto& stripe_bucket_db_updater = stripe_of_bucket(bucket.getBucketId()).bucket_db_updater();
    stripe_bucket_db_updater.recheckBucketInfo(1, bucket);

    ASSERT_EQ(size_t(1), _sender.commands().size());

    uint16_t index = 0;
    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(bucket.getBucketId(), rbi.getBuckets()[0]);
        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        const api::StorageMessageAddress *address = _sender.command(0)->getAddress();
        index = address->getIndex();
        reply->setResult(api::ReturnCode::NOT_CONNECTED);
        stripe_bucket_db_updater.onRequestBucketInfoReply(reply);
        // Trigger that delayed message is sent
        fake_clock().addSecondsToTime(10);
        stripe_bucket_db_updater.resendDelayedMessages();
    }

    ASSERT_EQ(size_t(2), _sender.commands().size());

    set_cluster_state(vespalib::make_string("distributor:1 storage:3 .%d.s:d", index));

    // Recheck bucket.
    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(1));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(bucket.getBucketId(), rbi.getBuckets()[0]);
        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        reply->setResult(api::ReturnCode::NOT_CONNECTED);
        stripe_bucket_db_updater.onRequestBucketInfoReply(reply);
    }

    // Should not retry since node is down.
    EXPECT_EQ(size_t(2), _sender.commands().size());
}

TEST_F(TopLevelBucketDBUpdaterTest, recheck_node) {
    ASSERT_NO_FATAL_FAILURE(initialize_nodes_and_buckets(3, 5));

    _sender.clear();

    auto bucket = makeDocumentBucket(document::BucketId(16, 3));
    auto& stripe_bucket_db_updater = stripe_of_bucket(bucket.getBucketId()).bucket_db_updater();
    stripe_bucket_db_updater.recheckBucketInfo(1, bucket);

    ASSERT_EQ(size_t(1), _sender.commands().size());

    auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
    ASSERT_EQ(size_t(1), rbi.getBuckets().size());
    EXPECT_EQ(bucket.getBucketId(), rbi.getBuckets()[0]);

    auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
    reply->getBucketInfo().push_back(
            api::RequestBucketInfoReply::Entry(document::BucketId(16, 3),
                                               api::BucketInfo(20, 10, 12, 50, 60, true, true)));
    stripe_bucket_db_updater.onRequestBucketInfoReply(reply);

    lib::ClusterState state("distributor:1 storage:3");
    for (uint32_t i = 0; i < 3; i++) {
        EXPECT_EQ(get_ideal_str(document::BucketId(16, i), state),
                  get_nodes(document::BucketId(16, i)));
    }

    for (uint32_t i = 4; i < 5; i++) {
        EXPECT_EQ(get_ideal_str(document::BucketId(16, i), state),
                  get_nodes(document::BucketId(16, i)));
    }

    BucketDatabase::Entry entry = get_bucket(bucket);
    ASSERT_TRUE(entry.valid());

    const BucketCopy* copy = entry->getNode(1);
    ASSERT_TRUE(copy != nullptr);
    EXPECT_EQ(api::BucketInfo(20,10,12, 50, 60, true, true), copy->getBucketInfo());
}

TEST_F(TopLevelBucketDBUpdaterTest, notify_bucket_change) {
    enable_distributor_cluster_state("distributor:1 storage:1");

    add_nodes_to_stripe_bucket_db(document::BucketId(16, 1), "0=1234");
    _sender.replies().clear();

    {
        api::BucketInfo info(1, 2, 3, 4, 5, true, true);
        auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 1)), info);
        cmd->setSourceIndex(0);
        stripe_of_bucket(document::BucketId(16, 1)).bucket_db_updater().onNotifyBucketChange(cmd);
    }

    {
        api::BucketInfo info(10, 11, 12, 13, 14, false, false);
        auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(document::BucketId(16, 2)), info);
        cmd->setSourceIndex(0);
        stripe_of_bucket(document::BucketId(16, 2)).bucket_db_updater().onNotifyBucketChange(cmd);
    }

    // Must receive reply
    ASSERT_EQ(size_t(2), _sender.replies().size());

    for (int i = 0; i < 2; ++i) {
        ASSERT_EQ(MessageType::NOTIFYBUCKETCHANGE_REPLY, _sender.reply(i)->getType());
    }

    // No database update until request bucket info replies have been received.
    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x4d2,docs=1234/1234,bytes=1234/1234,"
              "trusted=false,active=false,ready=false)",
              dump_bucket(document::BucketId(16, 1)));
    EXPECT_EQ(std::string("NONEXISTING"), dump_bucket(document::BucketId(16, 2)));

    ASSERT_EQ(size_t(2), _sender.commands().size());

    std::vector<api::BucketInfo> infos;
    infos.push_back(api::BucketInfo(4567, 200, 2000, 400, 4000, true, true));
    infos.push_back(api::BucketInfo(8999, 300, 3000, 500, 5000, false, false));

    for (int i = 0; i < 2; ++i) {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(i));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        document::BucketId bucket_id(16, i + 1);
        EXPECT_EQ(bucket_id, rbi.getBuckets()[0]);

        auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
        reply->getBucketInfo().push_back(api::RequestBucketInfoReply::Entry(bucket_id, infos[i]));
        stripe_of_bucket(bucket_id).bucket_db_updater().onRequestBucketInfoReply(reply);
    }

    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0x11d7,docs=200/400,bytes=2000/4000,trusted=true,active=true,ready=true)",
              dump_bucket(document::BucketId(16, 1)));
    EXPECT_EQ("BucketId(0x4000000000000002) : "
              "node(idx=0,crc=0x2327,docs=300/500,bytes=3000/5000,trusted=true,active=false,ready=false)",
              dump_bucket(document::BucketId(16, 2)));
}

TEST_F(TopLevelBucketDBUpdaterTest, notify_bucket_change_from_node_down) {
    enable_distributor_cluster_state("distributor:1 storage:2");

    document::BucketId bucket_id(16, 1);
    add_nodes_to_stripe_bucket_db(bucket_id, "1=1234");

    _sender.replies().clear();

    {
        api::BucketInfo info(8999, 300, 3000, 500, 5000, false, false);
        auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket_id), info);
        cmd->setSourceIndex(0);
        stripe_of_bucket(bucket_id).bucket_db_updater().onNotifyBucketChange(cmd);
    }
    // Enable here to avoid having request bucket info be silently swallowed
    // (send_request_bucket_info drops message if node is down).
    enable_distributor_cluster_state("distributor:1 storage:2 .0.s:d");

    ASSERT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=1,crc=0x4d2,docs=1234/1234,bytes=1234/1234,trusted=false,active=false,ready=false)",
              dump_bucket(bucket_id));

    ASSERT_EQ(size_t(1), _sender.replies().size());
    ASSERT_EQ(MessageType::NOTIFYBUCKETCHANGE_REPLY, _sender.reply(0)->getType());

    // Currently, this pending operation will be auto-flushed when the cluster state
    // changes so the behavior is still correct. Keep this test around to prevent
    // regressions here.
    ASSERT_EQ(size_t(1), _sender.commands().size());
    auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
    ASSERT_EQ(size_t(1), rbi.getBuckets().size());
    EXPECT_EQ(bucket_id, rbi.getBuckets()[0]);

    auto reply = std::make_shared<api::RequestBucketInfoReply>(rbi);
    reply->getBucketInfo().push_back(
            api::RequestBucketInfoReply::Entry(
                    bucket_id,
                    api::BucketInfo(8999, 300, 3000, 500, 5000, false, false)));
    stripe_of_bucket(bucket_id).bucket_db_updater().onRequestBucketInfoReply(reply);

    // No change
    EXPECT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=1,crc=0x4d2,docs=1234/1234,bytes=1234/1234,trusted=false,active=false,ready=false)",
              dump_bucket(bucket_id));
}

/**
 * Test that NotifyBucketChange received while there's a pending cluster state
 * waits until the cluster state has been enabled as current before it sends off
 * the single bucket info requests. This is to prevent a race condition where
 * the replies to bucket info requests for buckets that would be owned by the
 * distributor in the pending state but not by the current state would be
 * discarded when attempted inserted into the bucket database.
 */
TEST_F(TopLevelBucketDBUpdaterTest, notify_change_with_pending_state_queues_bucket_info_requests) {
    set_cluster_state("distributor:1 storage:1");
    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    document::BucketId bucket_id(16, 1);
    {
        api::BucketInfo info(8999, 300, 3000, 500, 5000, false, false);
        auto cmd(std::make_shared<api::NotifyBucketChangeCommand>(
                makeDocumentBucket(bucket_id), info));
        cmd->setSourceIndex(0);
        stripe_of_bucket(bucket_id).bucket_db_updater().onNotifyBucketChange(cmd);
    }

    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:1 storage:1"),
                                                           _bucket_spaces.size(), 10));

    ASSERT_EQ(_bucket_spaces.size() + 1, _sender.commands().size());

    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(_bucket_spaces.size()));
        ASSERT_EQ(size_t(1), rbi.getBuckets().size());
        EXPECT_EQ(bucket_id, rbi.getBuckets()[0]);
    }
    _sender.clear();

    // Queue must be cleared once pending state is enabled.
    {
        lib::ClusterState state("distributor:1 storage:2");
        uint32_t expected_msgs = _bucket_spaces.size(), dummy_buckets_to_return = 1;
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state, expected_msgs, dummy_buckets_to_return));
    }
    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());
    {
        auto& rbi = dynamic_cast<RequestBucketInfoCommand&>(*_sender.command(0));
        EXPECT_EQ(size_t(0), rbi.getBuckets().size());
    }
}

TEST_F(TopLevelBucketDBUpdaterTest, merge_reply) {
    enable_distributor_cluster_state("distributor:1 storage:3");

    document::BucketId bucket_id(16, 1234);
    add_nodes_to_stripe_bucket_db(bucket_id, "0=1234,1=1234,2=1234");

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(api::MergeBucketCommand::Node(0));
    nodes.push_back(api::MergeBucketCommand::Node(1));
    nodes.push_back(api::MergeBucketCommand::Node(2));

    api::MergeBucketCommand cmd(makeDocumentBucket(bucket_id), nodes, 0);
    auto reply = std::make_shared<api::MergeBucketReply>(cmd);

    _sender.clear();
    stripe_of_bucket(bucket_id).bucket_db_updater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(3), _sender.commands().size());

    for (uint32_t i = 0; i < 3; i++) {
        auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.command(i));

        ASSERT_TRUE(req.get() != nullptr);
        ASSERT_EQ(size_t(1), req->getBuckets().size());
        EXPECT_EQ(bucket_id, req->getBuckets()[0]);

        auto reqreply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(bucket_id,
                                                   api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));

        stripe_of_bucket(bucket_id).bucket_db_updater().onRequestBucketInfoReply(reqreply);
    }

    EXPECT_EQ("BucketId(0x40000000000004d2) : "
              "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
              "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false), "
              "node(idx=2,crc=0x1e,docs=300/300,bytes=3000/3000,trusted=false,active=false,ready=false)",
              dump_bucket(bucket_id));
};

TEST_F(TopLevelBucketDBUpdaterTest, merge_reply_node_down) {
    enable_distributor_cluster_state("distributor:1 storage:3");
    std::vector<api::MergeBucketCommand::Node> nodes;

    document::BucketId bucket_id(16, 1234);
    add_nodes_to_stripe_bucket_db(bucket_id, "0=1234,1=1234,2=1234");

    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(bucket_id), nodes, 0);
    auto reply = std::make_shared<api::MergeBucketReply>(cmd);

    set_cluster_state(lib::ClusterState("distributor:1 storage:2"));

    _sender.clear();
    stripe_of_bucket(bucket_id).bucket_db_updater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(2), _sender.commands().size());

    for (uint32_t i = 0; i < 2; i++) {
        auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.command(i));

        ASSERT_TRUE(req.get() != nullptr);
        ASSERT_EQ(size_t(1), req->getBuckets().size());
        EXPECT_EQ(bucket_id, req->getBuckets()[0]);

        auto reqreply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(
                        bucket_id,
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));
        stripe_of_bucket(bucket_id).bucket_db_updater().onRequestBucketInfoReply(reqreply);
    }

    EXPECT_EQ("BucketId(0x40000000000004d2) : "
              "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
              "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false)",
              dump_bucket(bucket_id));
};

TEST_F(TopLevelBucketDBUpdaterTest, merge_reply_node_down_after_request_sent) {
    enable_distributor_cluster_state("distributor:1 storage:3");
    std::vector<api::MergeBucketCommand::Node> nodes;

    document::BucketId bucket_id(16, 1234);
    add_nodes_to_stripe_bucket_db(bucket_id, "0=1234,1=1234,2=1234");

    for (uint32_t i = 0; i < 3; ++i) {
        nodes.emplace_back(i);
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(bucket_id), nodes, 0);
    auto reply = std::make_shared<api::MergeBucketReply>(cmd);

    _sender.clear();
    stripe_of_bucket(bucket_id).bucket_db_updater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(3), _sender.commands().size());

    set_cluster_state(lib::ClusterState("distributor:1 storage:2"));

    for (uint32_t i = 0; i < 3; i++) {
        auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.command(i));

        ASSERT_TRUE(req.get() != nullptr);
        ASSERT_EQ(size_t(1), req->getBuckets().size());
        EXPECT_EQ(bucket_id, req->getBuckets()[0]);

        auto reqreply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reqreply->getBucketInfo().push_back(
                api::RequestBucketInfoReply::Entry(
                        bucket_id,
                        api::BucketInfo(10 * (i + 1), 100 * (i +1), 1000 * (i+1))));
        stripe_of_bucket(bucket_id).bucket_db_updater().onRequestBucketInfoReply(reqreply);
    }

    EXPECT_EQ("BucketId(0x40000000000004d2) : "
              "node(idx=0,crc=0xa,docs=100/100,bytes=1000/1000,trusted=false,active=false,ready=false), "
              "node(idx=1,crc=0x14,docs=200/200,bytes=2000/2000,trusted=false,active=false,ready=false)",
              dump_bucket(bucket_id));
};

TEST_F(TopLevelBucketDBUpdaterTest, flush) {
    enable_distributor_cluster_state("distributor:1 storage:3");
    _sender.clear();

    document::BucketId bucket_id(16, 1234);
    add_nodes_to_stripe_bucket_db(bucket_id, "0=1234,1=1234,2=1234");

    std::vector<api::MergeBucketCommand::Node> nodes;
    for (uint32_t i = 0; i < 3; ++i) {
        nodes.push_back(api::MergeBucketCommand::Node(i));
    }

    api::MergeBucketCommand cmd(makeDocumentBucket(bucket_id), nodes, 0);
    auto reply = std::make_shared<api::MergeBucketReply>(cmd);

    _sender.clear();
    stripe_of_bucket(bucket_id).bucket_db_updater().onMergeBucketReply(reply);

    ASSERT_EQ(size_t(3), _sender.commands().size());
    ASSERT_EQ(size_t(0), _sender_down.replies().size());

    stripe_of_bucket(bucket_id).bucket_db_updater().flush();
    // Flushing should drop all merge bucket replies
    EXPECT_EQ(size_t(0), _sender_down.commands().size());
}

std::string
TopLevelBucketDBUpdaterTest::get_sent_nodes(const std::string& old_cluster_state,
                                            const std::string& new_cluster_state)
{
    auto fixture = create_pending_state_fixture_for_state_change(old_cluster_state, new_cluster_state);
    sort_sent_messages_by_index(fixture->sender);

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
TopLevelBucketDBUpdaterTest::get_sent_nodes_distribution_changed(const std::string& old_cluster_state)
{
    DistributorMessageSenderStub sender;

    framework::defaultimplementation::FakeClock clock;
    auto cluster_info = create_cluster_info(old_cluster_state);
    std::unique_ptr<PendingClusterState> state(
            PendingClusterState::createForDistributionChange(
                    clock, cluster_info, sender, bucket_space_states(), api::Timestamp(1)));

    sort_sent_messages_by_index(sender);

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

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_send_messages) {
    EXPECT_EQ(get_node_list({0, 1, 2}),
              get_sent_nodes("cluster:d",
                             "distributor:1 storage:3"));

    EXPECT_EQ(get_node_list({0, 1}),
              get_sent_nodes("cluster:d",
                             "distributor:1 storage:3 .2.s:m"));

    EXPECT_EQ(get_node_list({2}),
              get_sent_nodes("distributor:1 storage:2",
                             "distributor:1 storage:3"));

    EXPECT_EQ(get_node_list({2, 3, 4, 5}),
              get_sent_nodes("distributor:1 storage:2",
                             "distributor:1 storage:6"));

    EXPECT_EQ(get_node_list({0, 1, 2}),
              get_sent_nodes("distributor:4 storage:3",
                             "distributor:3 storage:3"));

    EXPECT_EQ(get_node_list({0, 1, 2, 3}),
              get_sent_nodes("distributor:4 storage:3",
                             "distributor:4 .2.s:d storage:4"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:4 storage:3",
                             "distributor:4 .0.s:d storage:4"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:3 storage:3",
                             "distributor:4 storage:3"));

    EXPECT_EQ(get_node_list({2}),
              get_sent_nodes("distributor:3 storage:3 .2.s:i",
                             "distributor:3 storage:3"));

    EXPECT_EQ(get_node_list({1}),
              get_sent_nodes("distributor:3 storage:3 .1.s:d",
                             "distributor:3 storage:3"));

    EXPECT_EQ(get_node_list({1, 2, 4}),
              get_sent_nodes("distributor:3 storage:4 .1.s:d .2.s:i",
                             "distributor:3 storage:5"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:1 storage:3",
                             "cluster:d"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:1 storage:3",
                             "distributor:1 storage:3"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:1 storage:3",
                             "cluster:d distributor:1 storage:6"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:3 storage:3",
                             "distributor:3 .2.s:m storage:3"));

    EXPECT_EQ(get_node_list({0, 1, 2}),
              get_sent_nodes("distributor:3 .2.s:m storage:3",
                             "distributor:3 .2.s:d storage:3"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:3 .2.s:m storage:3",
                           "distributor:3 storage:3"));

    EXPECT_EQ(get_node_list({0, 1, 2}),
              get_sent_nodes_distribution_changed("distributor:3 storage:3"));

    EXPECT_EQ(get_node_list({0, 1}),
              get_sent_nodes("distributor:10 storage:2",
                             "distributor:10 .1.s:d storage:2"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:2 storage:2",
                           "distributor:3 .2.s:i storage:2"));

    EXPECT_EQ(get_node_list({0, 1, 2}),
              get_sent_nodes("distributor:3 storage:3",
                           "distributor:3 .2.s:s storage:3"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:3 .2.s:s storage:3",
                           "distributor:3 .2.s:d storage:3"));

    EXPECT_EQ(get_node_list({1}),
              get_sent_nodes("distributor:3 storage:3 .1.s:m",
                           "distributor:3 storage:3"));

    EXPECT_EQ("",
              get_sent_nodes("distributor:3 storage:3",
                           "distributor:3 storage:3 .1.s:m"));
};

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_receive) {
    DistributorMessageSenderStub sender;

    auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState("distributor:1 storage:3"));

    framework::defaultimplementation::FakeClock clock;
    auto cluster_info = create_cluster_info("cluster:d");
    OutdatedNodesMap outdated_nodes_map;
    std::unique_ptr<PendingClusterState> state(
            PendingClusterState::createForClusterStateChange(
                    clock, cluster_info, sender, bucket_space_states(),
                    cmd, outdated_nodes_map, api::Timestamp(1)));

    ASSERT_EQ(message_count(3), sender.commands().size());

    sort_sent_messages_by_index(sender);

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

    auto& pending_transition = state->getPendingBucketSpaceDbTransition(makeBucketSpace());
    EXPECT_EQ(3u, pending_transition.results().size());
}

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_with_group_down) {
    std::string config = dist_config_6_nodes_across_4_groups();
    config += "distributor_auto_ownership_transfer_on_whole_group_down true\n";
    set_distribution(config);

    // Group config has nodes {0, 1}, {2, 3}, {4, 5}
    // We're node index 0.

    // Entire group 1 goes down. Must refetch from all nodes.
    EXPECT_EQ(get_node_list({0, 1, 2, 3, 4, 5}),
              get_sent_nodes("distributor:6 storage:6",
                             "distributor:6 .2.s:d .3.s:d storage:6"));

    // But don't fetch if not the entire group is down.
    EXPECT_EQ("",
              get_sent_nodes("distributor:6 storage:6",
                             "distributor:6 .2.s:d storage:6"));
}

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_with_group_down_and_no_handover) {
    std::string config = dist_config_6_nodes_across_4_groups();
    config += "distributor_auto_ownership_transfer_on_whole_group_down false\n";
    set_distribution(config);

    // Group is down, but config says to not do anything about it.
    EXPECT_EQ(get_node_list({0, 1, 2, 3, 4, 5}, _bucket_spaces.size() - 1),
              get_sent_nodes("distributor:6 storage:6",
                             "distributor:6 .2.s:d .3.s:d storage:6"));
}


namespace {

void
parse_input_data(const std::string& data,
                 uint64_t timestamp,
                 PendingClusterState& state,
                 bool include_bucket_info)
{
    vespalib::StringTokenizer tokenizer(data, "|");
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        vespalib::StringTokenizer tok2(tokenizer[i], ":");

        uint16_t node = atoi(tok2[0].data());

        state.setNodeReplied(node);
        auto& pending_transition = state.getPendingBucketSpaceDbTransition(makeBucketSpace());

        vespalib::StringTokenizer tok3(tok2[1], ",");
        for (uint32_t j = 0; j < tok3.size(); j++) {
            if (include_bucket_info) {
                vespalib::StringTokenizer tok4(tok3[j], "/");

                pending_transition.addNodeInfo(
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
                pending_transition.addNodeInfo(
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
    bool _include_bucket_info;

    explicit BucketDumper(bool include_bucket_info)
        : _include_bucket_info(include_bucket_info)
    {
    }

    bool process(const BucketDatabase::ConstEntryRef& e) override {
        document::BucketId bucket_id(e.getBucketId());

        ost << uint32_t(bucket_id.getRawId()) << ":";
        for (uint32_t i = 0; i < e->getNodeCount(); ++i) {
            if (i > 0) {
                ost << ",";
            }
            const BucketCopy& copy(e->getNodeRef(i));
            ost << copy.getNode();
            if (_include_bucket_info) {
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

}

std::string
TopLevelBucketDBUpdaterTest::merge_bucket_lists(
        const lib::ClusterState& old_state,
        const std::string& existing_data,
        const lib::ClusterState& new_state,
        const std::string& new_data,
        bool include_bucket_info)
{
    framework::defaultimplementation::FakeClock clock;
    framework::MilliSecTimer timer(clock);

    DistributorMessageSenderStub sender;
    OutdatedNodesMap outdated_nodes_map;

    {
        auto cmd = std::make_shared<api::SetSystemStateCommand>(old_state);
        api::Timestamp before_time(1);
        auto cluster_info = create_cluster_info("cluster:d");

        auto state = PendingClusterState::createForClusterStateChange(
                clock, cluster_info, sender, bucket_space_states(),
                cmd, outdated_nodes_map, before_time);

        parse_input_data(existing_data, before_time, *state, include_bucket_info);
        auto guard = acquire_stripe_guard();
        state->merge_into_bucket_databases(*guard);
    }

    BucketDumper dumper_tmp(true);
    for (auto* s : distributor_stripes()) {
        auto& db = s->getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space()).getBucketDatabase();
        db.for_each_upper_bound(dumper_tmp);
    }

    {
        auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(new_state));
        api::Timestamp after_time(2);
        auto cluster_info = create_cluster_info(old_state.toString());

        auto state = PendingClusterState::createForClusterStateChange(
                        clock, cluster_info, sender, bucket_space_states(),
                        cmd, outdated_nodes_map, after_time);

        parse_input_data(new_data, after_time, *state, include_bucket_info);
        auto guard = acquire_stripe_guard();
        state->merge_into_bucket_databases(*guard);
    }

    BucketDumper dumper(include_bucket_info);
    for (auto* s : distributor_stripes()) {
        auto& db = s->getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space()).getBucketDatabase();
        db.for_each_upper_bound(dumper);
        db.clear();
    }
    return dumper.ost.str();
}

std::string
TopLevelBucketDBUpdaterTest::merge_bucket_lists(const std::string& existing_data,
                                                const std::string& new_data,
                                                bool include_bucket_info)
{
    return merge_bucket_lists(
            lib::ClusterState("distributor:1 storage:3"),
            existing_data,
            lib::ClusterState("distributor:1 storage:3"),
            new_data,
            include_bucket_info);
}

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_merge) {
    // Result is on the form: [bucket w/o count bits]:[node indexes]|..
    // Input is on the form: [node]:[bucket w/o count bits]|...

    // Simple initializing case - ask all nodes for info
    EXPECT_EQ("4:0,1|2:0,1|6:1,2|1:0,2|5:2,0|3:2,1|",
              merge_bucket_lists(
                      "",
                      "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6"));

    // New node came up
    EXPECT_EQ("4:0,1|2:0,1|6:1,2,3|1:0,2,3|5:2,0,3|3:2,1,3|",
              merge_bucket_lists(
                      "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                      "3:1,3,5,6"));

    // Node came up with some buckets removed and some added
    // Buckets that were removed should not be removed as the node
    // didn't lose a disk.
    EXPECT_EQ("8:0|4:0,1|2:0,1|6:1,0,2|1:0,2|5:2,0|3:2,1|",
              merge_bucket_lists(
                      "0:1,2,4,5|1:2,3,4,6|2:1,3,5,6",
                      "0:1,2,6,8"));

    // Bucket info format is "bucketid/checksum/count/size"
    // Node went from initializing to up and invalid bucket went to empty.
    EXPECT_EQ("2:0/0/0/0/t|",
              merge_bucket_lists(
                      "0:2/0/0/1",
                      "0:2/0/0/0",
                      true));

    EXPECT_EQ("5:1/2/3/4/u,0/0/0/0/u|",
              merge_bucket_lists("", "0:5/0/0/0|1:5/2/3/4", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_merge_replica_changed) {
    // Node went from initializing to up and non-invalid bucket changed.
    EXPECT_EQ("2:0/2/3/4/t|3:0/2/4/6/t|",
              merge_bucket_lists(
                      lib::ClusterState("distributor:1 storage:1 .0.s:i"),
                      "0:2/1/2/3,3/2/4/6",
                      lib::ClusterState("distributor:1 storage:1"),
                      "0:2/2/3/4,3/2/4/6",
                      true));
}

TEST_F(TopLevelBucketDBUpdaterTest, no_db_resurrection_for_bucket_not_owned_in_current_state) {
    document::BucketId bucket(16, 3);
    lib::ClusterState state_before("distributor:1 storage:1");
    {
        uint32_t expected_msgs = _bucket_spaces.size(), dummy_buckets_to_return = 1;
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_before, expected_msgs, dummy_buckets_to_return));
    }
    _sender.clear();

    stripe_of_bucket(bucket).bucket_db_updater().recheckBucketInfo(0, makeDocumentBucket(bucket));

    ASSERT_EQ(size_t(1), _sender.commands().size());
    auto rbi = std::dynamic_pointer_cast<RequestBucketInfoCommand>(_sender.command(0));

    lib::ClusterState state_after("distributor:3 storage:3");

    {
        uint32_t expected_msgs = message_count(2), dummy_buckets_to_return = 1;
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_after, expected_msgs, dummy_buckets_to_return));
    }
    EXPECT_FALSE(distributor_bucket_space(bucket).get_bucket_ownership_flags(bucket).owned_in_current_state());

    ASSERT_NO_FATAL_FAILURE(send_fake_reply_for_single_bucket_request(*rbi));

    EXPECT_EQ("NONEXISTING", dump_bucket(bucket));
}

TEST_F(TopLevelBucketDBUpdaterTest, no_db_resurrection_for_bucket_not_owned_in_pending_state) {
    document::BucketId bucket(16, 3);
    lib::ClusterState state_before("distributor:1 storage:1");
    {
        uint32_t expected_msgs = _bucket_spaces.size(), dummy_buckets_to_return = 1;
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_before, expected_msgs, dummy_buckets_to_return));
    }
    _sender.clear();

    stripe_of_bucket(bucket).bucket_db_updater().recheckBucketInfo(0, makeDocumentBucket(bucket));

    ASSERT_EQ(size_t(1), _sender.commands().size());
    auto rbi = std::dynamic_pointer_cast<RequestBucketInfoCommand>(_sender.command(0));

    lib::ClusterState state_after("distributor:3 storage:3");
    // Set, but _don't_ enable cluster state. We want it to be pending.
    set_cluster_state(state_after);
    EXPECT_TRUE(distributor_bucket_space(bucket).get_bucket_ownership_flags(bucket).owned_in_current_state());
    EXPECT_FALSE(distributor_bucket_space(bucket).get_bucket_ownership_flags(bucket).owned_in_pending_state());

    ASSERT_NO_FATAL_FAILURE(send_fake_reply_for_single_bucket_request(*rbi));

    EXPECT_EQ("NONEXISTING", dump_bucket(bucket));
}

/*
 * If we get a distribution config change, it's important that cluster states that
 * arrive after this--but _before_ the pending cluster state has finished--must trigger
 * a full bucket info fetch no matter what the cluster state change was! Otherwise, we
 * will with a high likelihood end up not getting the complete view of the buckets in
 * the cluster.
 */
TEST_F(TopLevelBucketDBUpdaterTest, cluster_state_always_sends_full_fetch_when_distribution_change_pending) {
    lib::ClusterState state_before("distributor:6 storage:6");
    {
        uint32_t expected_msgs = message_count(6), dummy_buckets_to_return = 1;
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_before, expected_msgs, dummy_buckets_to_return));
    }
    _sender.clear();
    std::string distConfig(dist_config_6_nodes_across_2_groups());
    set_distribution(distConfig);

    sort_sent_messages_by_index(_sender);
    ASSERT_EQ(message_count(6), _sender.commands().size());
    // Suddenly, a wild cluster state change appears! Even though this state
    // does not in itself imply any bucket changes, it will still overwrite the
    // pending cluster state and thus its state of pending bucket info requests.
    set_cluster_state("distributor:6 .2.t:12345 storage:6");

    ASSERT_EQ(message_count(12), _sender.commands().size());

    // Send replies for first messageCount(6) (outdated requests).
    int num_buckets = 10;
    for (uint32_t i = 0; i < message_count(6); ++i) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:6 storage:6"),
                                                  *_sender.command(i), num_buckets));
    }
    // No change from these.
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(1, "distributor:6 storage:6"));

    // Send for current pending.
    for (uint32_t i = 0; i < message_count(6); ++i) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:6 .2.t:12345 storage:6"),
                                                  *_sender.command(i + message_count(6)),
                                                  num_buckets));
    }
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(num_buckets, "distributor:6 storage:6"));
    _sender.clear();

    // No more pending global fetch; this should be a no-op state.
    set_cluster_state("distributor:6 .3.t:12345 storage:6");
    EXPECT_EQ(size_t(0), _sender.commands().size());
}

TEST_F(TopLevelBucketDBUpdaterTest, changed_distribution_config_triggers_recovery_mode) {
    uint32_t num_buckets = 20;
    ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(lib::ClusterState("distributor:6 storage:6"),
                                                         message_count(6), num_buckets));
    _sender.clear();
    EXPECT_TRUE(all_distributor_stripes_are_in_recovery_mode());
    complete_recovery_mode_on_all_stripes();
    EXPECT_FALSE(all_distributor_stripes_are_in_recovery_mode());

    set_distribution(dist_config_6_nodes_across_4_groups());
    sort_sent_messages_by_index(_sender);
    // No replies received yet, still no recovery mode.
    EXPECT_FALSE(all_distributor_stripes_are_in_recovery_mode());

    ASSERT_EQ(message_count(6), _sender.commands().size());
    num_buckets = 10;
    for (uint32_t i = 0; i < message_count(6); ++i) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:6 storage:6"),
                                                  *_sender.command(i), num_buckets));
    }

    // Pending cluster state (i.e. distribution) has been enabled, which should
    // cause recovery mode to be entered.
    EXPECT_TRUE(all_distributor_stripes_are_in_recovery_mode());
    complete_recovery_mode_on_all_stripes();
    EXPECT_FALSE(all_distributor_stripes_are_in_recovery_mode());
}

TEST_F(TopLevelBucketDBUpdaterTest, changed_distribution_config_does_not_elide_bucket_db_pruning) {
    set_distribution(dist_config_3_nodes_in_1_group());

    constexpr uint32_t n_buckets = 100;
    ASSERT_NO_FATAL_FAILURE(
            set_and_enable_cluster_state(lib::ClusterState("distributor:6 storage:6"), message_count(6), n_buckets));
    _sender.clear();

    // Config implies a different node set than the current cluster state, so it's crucial that
    // DB pruning is _not_ elided. Yes, this is inherently racing with cluster state changes and
    // should be changed to be atomic and controlled by the cluster controller instead of config.
    // But this is where we currently are.
    set_distribution(dist_config_6_nodes_across_2_groups());
    for (auto* s : distributor_stripes()) {
        const auto& db = s->getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space()).getBucketDatabase();
        db.acquire_read_guard()->for_each([&]([[maybe_unused]] uint64_t key, const auto& e) {
            auto id = e.getBucketId();
            EXPECT_TRUE(distributor_bucket_space(id).get_bucket_ownership_flags(id).owned_in_pending_state());
        });
    }
}

TEST_F(TopLevelBucketDBUpdaterTest, newly_added_buckets_have_current_time_as_gc_timestamp) {
    fake_clock().setAbsoluteTimeInSeconds(101234);
    lib::ClusterState state_before("distributor:1 storage:1");
    {
        uint32_t expected_msgs = _bucket_spaces.size(), dummy_buckets_to_return = 1;
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_before, expected_msgs, dummy_buckets_to_return));
    }
    // setAndEnableClusterState adds n buckets with id (16, i)
    document::BucketId bucket(16, 0);
    BucketDatabase::Entry e = get_bucket(bucket);
    ASSERT_TRUE(e.valid());
    EXPECT_EQ(uint32_t(101234), e->getLastGarbageCollectionTime());
}

TEST_F(TopLevelBucketDBUpdaterTest, newer_mutations_not_overwritten_by_earlier_bucket_fetch) {
    {
        lib::ClusterState state_before("distributor:1 storage:1 .0.s:i");
        uint32_t expected_msgs = _bucket_spaces.size(), dummy_buckets_to_return = 0;
        // This step is required to make the distributor ready for accepting
        // the below explicit database insertion towards node 0.
        ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_before, expected_msgs, dummy_buckets_to_return));
    }
    _sender.clear();
    fake_clock().setAbsoluteTimeInSeconds(1000);
    lib::ClusterState state("distributor:1 storage:1");
    set_cluster_state(state);
    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    // Before replying with the bucket info, simulate the arrival of a mutation
    // reply that alters the state of the bucket with information that will be
    // more recent that what is returned by the bucket info. This information
    // must not be lost when the bucket info is later merged into the database.
    document::BucketId bucket(16, 1);
    constexpr uint64_t insertion_timestamp = 1001ULL * 1000000;
    api::BucketInfo wanted_info(5, 6, 7);
    stripe_of_bucket(bucket).bucket_db_updater().operation_context().update_bucket_database(
            makeDocumentBucket(bucket),
            BucketCopy(insertion_timestamp, 0, wanted_info),
            DatabaseUpdate::CREATE_IF_NONEXISTING);

    fake_clock().setAbsoluteTimeInSeconds(1002);
    constexpr uint32_t buckets_returned = 10; // Buckets (16, 0) ... (16, 9)
    // Return bucket information which on the timeline might originate from
    // anywhere between [1000, 1002]. Our assumption is that any mutations
    // taking place after t=1000 must have its reply received and processed
    // by this distributor and timestamped strictly higher than t=1000 (modulo
    // clock skew, of course, but that is outside the scope of this). A mutation
    // happening before t=1000 but receiving a reply at t>1000 does not affect
    // correctness, as this should contain the same bucket info as that
    // contained in the full bucket reply and the DB update is thus idempotent.
    for (uint32_t i = 0; i < _bucket_spaces.size(); ++i) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(state, *_sender.command(i), buckets_returned));
    }

    BucketDatabase::Entry e = get_bucket(bucket);
    ASSERT_EQ(uint32_t(1), e->getNodeCount());
    EXPECT_EQ(wanted_info, e->getNodeRef(0).getBucketInfo());
}

std::vector<uint16_t>
TopLevelBucketDBUpdaterTest::get_send_set() const
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
TopLevelBucketDBUpdaterTest::get_sent_nodes_with_preemption(
        const std::string& old_cluster_state,
        uint32_t expected_old_state_messages,
        const std::string& preempted_cluster_state,
        const std::string& new_cluster_state)
{
    uint32_t dummy_buckets_to_return = 10;
    // FIXME cannot chain assertion checks in non-void function
    set_and_enable_cluster_state(lib::ClusterState(old_cluster_state),
                                 expected_old_state_messages,
                                 dummy_buckets_to_return);

    _sender.clear();

    set_cluster_state(preempted_cluster_state);
    _sender.clear();
    // Do not allow the pending state to become the active state; trigger a
    // new transition without ACKing the info requests first. This will
    // overwrite the pending state entirely.
    set_cluster_state(lib::ClusterState(new_cluster_state));
    return get_send_set();
}

std::vector<uint16_t>
TopLevelBucketDBUpdaterTest::expand_node_vec(const std::vector<uint16_t>& nodes)
{
    std::vector<uint16_t> res;
    size_t count = _bucket_spaces.size();
    for (const auto &node : nodes) {
        for (uint32_t i = 0; i < count; ++i) {
            res.push_back(node);
        }
    }
    return res;
}

/*
 * If we don't carry over the set of nodes that we need to fetch from,
 * a naive comparison between the active state and the new state will
 * make it appear to the distributor that nothing has changed, as any
 * database modifications caused by intermediate states will not be
 * accounted for (basically the ABA problem in a distributed setting).
 */
TEST_F(TopLevelBucketDBUpdaterTest, preempted_distributor_change_carries_node_set_over_to_next_state_fetch) {
    EXPECT_EQ(expand_node_vec({0, 1, 2, 3, 4, 5}),
              get_sent_nodes_with_preemption("version:1 distributor:6 storage:6",
                                             message_count(6),
                                             "version:2 distributor:6 .5.s:d storage:6",
                                             "version:3 distributor:6 storage:6"));
}

TEST_F(TopLevelBucketDBUpdaterTest, preempted_storage_change_carries_node_set_over_to_next_state_fetch) {
    EXPECT_EQ(expand_node_vec({2, 3}),
              get_sent_nodes_with_preemption(
                      "version:1 distributor:6 storage:6 .2.s:d",
                      message_count(5),
                      "version:2 distributor:6 storage:6 .2.s:d .3.s:d",
                      "version:3 distributor:6 storage:6"));
}

TEST_F(TopLevelBucketDBUpdaterTest, preempted_storage_node_down_must_be_re_fetched) {
    EXPECT_EQ(expand_node_vec({2}),
              get_sent_nodes_with_preemption(
                      "version:1 distributor:6 storage:6",
                      message_count(6),
                      "version:2 distributor:6 storage:6 .2.s:d",
                      "version:3 distributor:6 storage:6"));
}

using NodeVec = std::vector<uint16_t>;

TEST_F(TopLevelBucketDBUpdaterTest, do_not_send_to_preempted_node_now_in_down_state) {
    EXPECT_EQ(NodeVec{},
              get_sent_nodes_with_preemption(
                      "version:1 distributor:6 storage:6 .2.s:d",
                      message_count(5),
                      "version:2 distributor:6 storage:6", // Sends to 2.
                      "version:3 distributor:6 storage:6 .2.s:d")); // 2 down again.
}

TEST_F(TopLevelBucketDBUpdaterTest, do_not_send_to_preempted_node_not_part_of_new_state) {
    // Even though 100 nodes are preempted, not all of these should be part
    // of the request afterwards when only 6 are part of the state.
    EXPECT_EQ(expand_node_vec({0, 1, 2, 3, 4, 5}),
              get_sent_nodes_with_preemption(
                      "version:1 distributor:6 storage:100",
                      message_count(100),
                      "version:2 distributor:5 .4.s:d storage:100",
                      "version:3 distributor:6 storage:6"));
}

TEST_F(TopLevelBucketDBUpdaterTest, outdated_node_set_cleared_after_successful_state_completion) {
    lib::ClusterState state_before("version:1 distributor:6 storage:6 .1.t:1234");
    uint32_t expected_msgs = message_count(6), dummy_buckets_to_return = 10;
    ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state_before, expected_msgs, dummy_buckets_to_return));
    _sender.clear();
    // New cluster state that should not by itself trigger any new fetches,
    // unless outdated node set is somehow not cleared after an enabled
    // (completed) cluster state has been set.
    set_cluster_state("version:3 distributor:6 storage:6");
    EXPECT_EQ(size_t(0), _sender.commands().size());
}

// XXX test currently disabled since distribution config currently isn't used
// at all in order to deduce the set of nodes to send to. This might not matter
// in practice since it is assumed that the cluster state matching the new
// distribution config will follow very shortly after the config has been
// applied to the node. The new cluster state will then send out requests to
// the correct node set.
TEST_F(TopLevelBucketDBUpdaterTest, DISABLED_cluster_config_downsize_only_sends_to_available_nodes) {
    uint32_t expected_msgs = 6, dummy_buckets_to_return = 20;
    ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(lib::ClusterState("distributor:6 storage:6"),
                                                         expected_msgs, dummy_buckets_to_return));
    _sender.clear();

    // Intentionally trigger a racing config change which arrives before the
    // new cluster state representing it.
    set_distribution(dist_config_3_nodes_in_1_group());
    sort_sent_messages_by_index(_sender);

    EXPECT_EQ((NodeVec{0, 1, 2}), get_send_set());
}

/**
 * Test scenario where a cluster is downsized by removing a subset of the nodes
 * from the distribution configuration. The system must be able to deal with
 * a scenario where the set of nodes between two cluster states across a config
 * change may differ.
 *
 * See VESPA-790 for details.
 */
TEST_F(TopLevelBucketDBUpdaterTest, node_missing_from_config_is_treated_as_needing_ownership_transfer) {
    uint32_t expected_msgs = message_count(3), dummy_buckets_to_return = 1;
    ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(lib::ClusterState("distributor:3 storage:3"),
                                                         expected_msgs, dummy_buckets_to_return));
    _sender.clear();

    // Cluster goes from {0, 1, 2} -> {0, 1}. This leaves us with a config
    // that does not contain node 2 while the _active_ cluster state still
    // contains this node.
    const char* downsize_cfg =
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

    set_distribution(downsize_cfg);
    sort_sent_messages_by_index(_sender);
    _sender.clear();

    // Attempt to apply state with {0, 1} set. This will compare the new state
    // with the previous state, which still has node 2.
    expected_msgs = message_count(2);
    ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(lib::ClusterState("distributor:2 storage:2"),
                                                         expected_msgs, dummy_buckets_to_return));

    EXPECT_EQ(expand_node_vec({0, 1}), get_send_set());
}

TEST_F(TopLevelBucketDBUpdaterTest, changed_distributor_set_implies_ownership_transfer) {
    auto fixture = create_pending_state_fixture_for_state_change(
            "distributor:2 storage:2", "distributor:1 storage:2");
    EXPECT_TRUE(fixture->state->hasBucketOwnershipTransfer());

    fixture = create_pending_state_fixture_for_state_change(
            "distributor:2 storage:2", "distributor:2 .1.s:d storage:2");
    EXPECT_TRUE(fixture->state->hasBucketOwnershipTransfer());
}

TEST_F(TopLevelBucketDBUpdaterTest, unchanged_distributor_set_implies_no_ownership_transfer) {
    auto fixture = create_pending_state_fixture_for_state_change(
            "distributor:2 storage:2", "distributor:2 storage:1");
    EXPECT_FALSE(fixture->state->hasBucketOwnershipTransfer());

    fixture = create_pending_state_fixture_for_state_change(
            "distributor:2 storage:2", "distributor:2 storage:2 .1.s:d");
    EXPECT_FALSE(fixture->state->hasBucketOwnershipTransfer());
}

TEST_F(TopLevelBucketDBUpdaterTest, changed_distribution_config_implies_ownership_transfer) {
    auto fixture = create_pending_state_fixture_for_distribution_change("distributor:2 storage:2");
    EXPECT_TRUE(fixture->state->hasBucketOwnershipTransfer());
}

TEST_F(TopLevelBucketDBUpdaterTest, transition_time_tracked_for_single_state_change) {
    ASSERT_NO_FATAL_FAILURE(complete_state_transition_in_seconds("distributor:2 storage:2", 5, message_count(2)));

    EXPECT_EQ(uint64_t(5000), last_transition_time_in_millis());
}

TEST_F(TopLevelBucketDBUpdaterTest, transition_time_reset_across_non_preempting_state_changes) {
    ASSERT_NO_FATAL_FAILURE(complete_state_transition_in_seconds("distributor:2 storage:2", 5, message_count(2)));
    ASSERT_NO_FATAL_FAILURE(complete_state_transition_in_seconds("distributor:2 storage:3", 3, message_count(1)));

    EXPECT_EQ(uint64_t(3000), last_transition_time_in_millis());
}

TEST_F(TopLevelBucketDBUpdaterTest, transition_time_tracked_for_distribution_config_change) {
    lib::ClusterState state("distributor:2 storage:2");
    ASSERT_NO_FATAL_FAILURE(set_and_enable_cluster_state(state, message_count(2), 1));

    _sender.clear();
    set_distribution(dist_config_3_nodes_in_1_group());
    fake_clock().addSecondsToTime(4);
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(state, message_count(2)));
    EXPECT_EQ(uint64_t(4000), last_transition_time_in_millis());
}

TEST_F(TopLevelBucketDBUpdaterTest, transition_time_tracked_across_preempted_transitions) {
    _sender.clear();
    set_cluster_state("version:1 distributor:2 storage:2");
    fake_clock().addSecondsToTime(5);
    // Pre-empted with new state here, which will push out the old pending
    // state and replace it with a new one. We should still count the time
    // used processing the old state.
    ASSERT_NO_FATAL_FAILURE(complete_state_transition_in_seconds("version:2 distributor:2 storage:3", 3, message_count(3)));

    EXPECT_EQ(uint64_t(8000), last_transition_time_in_millis());
}

/*
 * Brief reminder on test DSL for checking bucket merge operations:
 *
 *   merge_bucket_lists() takes as input strings of the format
 *     <node>:<raw bucket id>/<checksum>/<num docs>/<doc size>|<node>:
 *   and returns a string describing the bucket DB post-merge with the format
 *     <raw bucket id>:<node>/<checksum>/<num docs>/<doc size>,<node>:....|<raw bucket id>:....
 *
 * Yes, the order of node<->bucket id is reversed between the two, perhaps to make sure you're awake.
 */

TEST_F(TopLevelBucketDBUpdaterTest, batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted) {
    // Replacing bucket information for content node 0 should not mark existing
    // untrusted replica as trusted as a side effect.
    EXPECT_EQ("5:1/7/8/9/u,0/1/2/3/u|",
              merge_bucket_lists(
                      lib::ClusterState("distributor:1 storage:3 .0.s:i"),
                      "0:5/0/0/0|1:5/7/8/9",
                      lib::ClusterState("distributor:1 storage:3 .0.s:u"),
                      "0:5/1/2/3|1:5/7/8/9", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, batch_add_of_new_diverging_replicas_does_not_mark_any_as_trusted) {
    EXPECT_EQ("5:1/7/8/9/u,0/1/2/3/u|",
              merge_bucket_lists("", "0:5/1/2/3|1:5/7/8/9", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, batch_add_with_single_resulting_replica_implicitly_marks_as_trusted) {
    EXPECT_EQ("5:0/1/2/3/t|",
              merge_bucket_lists("", "0:5/1/2/3", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, identity_update_of_single_replica_does_not_clear_trusted) {
    EXPECT_EQ("5:0/1/2/3/t|",
              merge_bucket_lists("0:5/1/2/3", "0:5/1/2/3", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, identity_update_of_diverging_untrusted_replicas_does_not_mark_any_as_trusted) {
    EXPECT_EQ("5:1/7/8/9/u,0/1/2/3/u|",
              merge_bucket_lists("0:5/1/2/3|1:5/7/8/9", "0:5/1/2/3|1:5/7/8/9", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, adding_diverging_replica_to_existing_trusted_does_not_remove_trusted) {
    EXPECT_EQ("5:1/2/3/4/u,0/1/2/3/t|",
              merge_bucket_lists("0:5/1/2/3", "0:5/1/2/3|1:5/2/3/4", true));
}

TEST_F(TopLevelBucketDBUpdaterTest, batch_update_from_distributor_change_does_not_mark_diverging_replicas_as_trusted) {
    // This differs from batch_update_of_existing_diverging_replicas_does_not_mark_any_as_trusted
    // in that _all_ content nodes are considered outdated when distributor changes take place,
    // and therefore a slightly different code path is taken. In particular, bucket info for
    // outdated nodes gets removed before possibly being re-added (if present in the bucket info
    // response).
    EXPECT_EQ("5:1/7/8/9/u,0/1/2/3/u|",
              merge_bucket_lists(
                      lib::ClusterState("distributor:2 storage:3"),
                      "0:5/1/2/3|1:5/7/8/9",
                      lib::ClusterState("distributor:1 storage:3"),
                      "0:5/1/2/3|1:5/7/8/9", true));
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

TEST_F(TopLevelBucketDBUpdaterTest, non_owned_buckets_moved_to_read_only_db_on_ownership_change) {
    set_stale_reads_enabled(true);

    lib::ClusterState initial_state("distributor:1 storage:4"); // All buckets owned by us by definition
    set_cluster_state_bundle(lib::ClusterStateBundle(initial_state, {}, false)); // Skip activation step for simplicity

    ASSERT_EQ(message_count(4), _sender.commands().size());
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(initial_state, message_count(4), n_buckets));
    _sender.clear();

    EXPECT_EQ(n_buckets, mutable_default_dbs_size());
    EXPECT_EQ(n_buckets, mutable_global_dbs_size());
    EXPECT_EQ(0u, read_only_default_dbs_size());
    EXPECT_EQ(0u, read_only_global_dbs_size());

    lib::ClusterState pending_state("distributor:2 storage:4");

    std::unordered_set<Bucket, Bucket::hash> buckets_not_owned_in_pending_state;
    for (auto* s : distributor_stripes()) {
        for_each_bucket(mutable_repo(*s), [&](const auto& space, const auto& entry) {
            if (!distributor_bucket_space(entry.getBucketId()).owns_bucket_in_state(pending_state, entry.getBucketId())) {
                buckets_not_owned_in_pending_state.insert(Bucket(space, entry.getBucketId()));
            }
        });
    }
    EXPECT_FALSE(buckets_not_owned_in_pending_state.empty());

    set_cluster_state_bundle(lib::ClusterStateBundle(pending_state, {}, true)); // Now requires activation

    const auto buckets_not_owned_per_space = (buckets_not_owned_in_pending_state.size() / 2); // 2 spaces
    const auto expected_mutable_buckets = n_buckets - buckets_not_owned_per_space;
    EXPECT_EQ(expected_mutable_buckets, mutable_default_dbs_size());
    EXPECT_EQ(expected_mutable_buckets, mutable_global_dbs_size());
    EXPECT_EQ(buckets_not_owned_per_space, read_only_default_dbs_size());
    EXPECT_EQ(buckets_not_owned_per_space, read_only_global_dbs_size());

    for (auto* s : distributor_stripes()) {
        for_each_bucket(read_only_repo(*s), [&](const auto& space, const auto& entry) {
            EXPECT_TRUE(buckets_not_owned_in_pending_state.find(Bucket(space, entry.getBucketId()))
                        != buckets_not_owned_in_pending_state.end());
        });
    }
}

TEST_F(TopLevelBucketDBUpdaterTest, buckets_no_longer_available_are_not_moved_to_read_only_database) {
    constexpr uint32_t n_buckets = 10;
    // No ownership change, just node down. Test redundancy is 2, so removing 2 nodes will
    // cause some buckets to be entirely unavailable.
    trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                       "version:2 distributor:1 storage:4 .0.s:d .1.s:m", n_buckets, 0);

    EXPECT_EQ(0u, read_only_default_dbs_size());
    EXPECT_EQ(0u, read_only_global_dbs_size());
}

TEST_F(TopLevelBucketDBUpdaterTest, non_owned_buckets_purged_when_read_only_support_is_config_disabled) {
    set_stale_reads_enabled(false);

    lib::ClusterState initial_state("distributor:1 storage:4"); // All buckets owned by us by definition
    set_cluster_state_bundle(lib::ClusterStateBundle(initial_state, {}, false)); // Skip activation step for simplicity

    ASSERT_EQ(message_count(4), _sender.commands().size());
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(initial_state, message_count(4), n_buckets));
    _sender.clear();

    // Nothing in read-only DB after first bulk load of buckets.
    EXPECT_EQ(0u, read_only_default_dbs_size());
    EXPECT_EQ(0u, read_only_global_dbs_size());

    set_cluster_state("distributor:2 storage:4");
    // No buckets should be moved into read only db after ownership changes.
    EXPECT_EQ(0u, read_only_default_dbs_size());
    EXPECT_EQ(0u, read_only_global_dbs_size());
}

TEST_F(TopLevelBucketDBUpdaterTest, deferred_activated_state_does_not_enable_state_until_activation_received) {
    set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:2 storage:4", 0, 4,
                                                               "version:2 distributor:1 storage:4", n_buckets, 4));

    // Version should not be switched over yet
    EXPECT_EQ(1u, current_cluster_state_bundle().getVersion());

    EXPECT_EQ(0u, mutable_default_dbs_size());
    EXPECT_EQ(0u, mutable_global_dbs_size());

    EXPECT_FALSE(activate_cluster_state_version(2));

    EXPECT_EQ(2u, current_cluster_state_bundle().getVersion());
    EXPECT_EQ(n_buckets, mutable_default_dbs_size());
    EXPECT_EQ(n_buckets, mutable_global_dbs_size());
}

TEST_F(TopLevelBucketDBUpdaterTest, read_only_db_cleared_once_pending_state_is_activated) {
    set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                               "version:2 distributor:2 storage:4", n_buckets, 0));
    EXPECT_FALSE(activate_cluster_state_version(2));

    EXPECT_EQ(0u, read_only_default_dbs_size());
    EXPECT_EQ(0u, read_only_global_dbs_size());
}

TEST_F(TopLevelBucketDBUpdaterTest, read_only_db_is_populated_even_when_self_is_marked_down) {
    set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                               "version:2 distributor:1 .0.s:d storage:4", n_buckets, 0));

    // State not yet activated, so read-only DBs have got all the buckets we used to have.
    EXPECT_EQ(0u, mutable_default_dbs_size());
    EXPECT_EQ(0u, mutable_global_dbs_size());
    EXPECT_EQ(n_buckets, read_only_default_dbs_size());
    EXPECT_EQ(n_buckets, read_only_global_dbs_size());
}

TEST_F(TopLevelBucketDBUpdaterTest, activate_cluster_state_request_with_mismatching_version_returns_actual_version) {
    set_stale_reads_enabled(true);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:4 distributor:1 storage:4", n_buckets, 4,
                                                               "version:5 distributor:2 storage:4", n_buckets, 0));

    EXPECT_TRUE(activate_cluster_state_version(4)); // Too old version
    ASSERT_NO_FATAL_FAILURE(assert_has_activate_cluster_state_reply_with_actual_version(5));

    EXPECT_TRUE(activate_cluster_state_version(6)); // More recent version than what has been observed
    ASSERT_NO_FATAL_FAILURE(assert_has_activate_cluster_state_reply_with_actual_version(5));
}

TEST_F(TopLevelBucketDBUpdaterTest, activate_cluster_state_request_without_pending_transition_passes_message_through) {
    set_stale_reads_enabled(true);
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

TEST_F(TopLevelBucketDBUpdaterTest, pending_cluster_state_getter_is_non_null_only_when_state_is_pending) {
    auto initial_baseline = std::make_shared<lib::ClusterState>("distributor:1 storage:2 .0.s:d");
    auto initial_default = std::make_shared<lib::ClusterState>("distributor:1 storage:2 .0.s:m");

    lib::ClusterStateBundle initial_bundle(*initial_baseline, {{FixedBucketSpaces::default_space(), initial_default},
                                                               {FixedBucketSpaces::global_space(), initial_baseline}});
    set_cluster_state_bundle(initial_bundle);

    for (auto* s : distributor_stripes()) {
        auto* state = s->bucket_db_updater().pendingClusterStateOrNull(FixedBucketSpaces::default_space());
        ASSERT_TRUE(state != nullptr);
        EXPECT_EQ(*initial_default, *state);

        state = s->bucket_db_updater().pendingClusterStateOrNull(FixedBucketSpaces::global_space());
        ASSERT_TRUE(state != nullptr);
        EXPECT_EQ(*initial_baseline, *state);
    }

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(*initial_baseline, message_count(1), 0));

    for (auto* s : distributor_stripes()) {
        auto* state = s->bucket_db_updater().pendingClusterStateOrNull(FixedBucketSpaces::default_space());
        EXPECT_TRUE(state == nullptr);

        state = s->bucket_db_updater().pendingClusterStateOrNull(FixedBucketSpaces::global_space());
        EXPECT_TRUE(state == nullptr);
    }
}

TEST_F(TopLevelBucketDBUpdaterTest, node_feature_sets_are_aggregated_from_nodes_and_propagated_to_stripes) {
    lib::ClusterState state("distributor:1 storage:3");
    set_cluster_state(state);
    uint32_t expected_msgs = message_count(3), dummy_buckets_to_return = 1;

    // Known feature sets are initially empty.
    auto stripes = distributor_stripes();
    for (auto* s : stripes) {
        for (uint16_t i : {0, 1, 2}) {
            EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(i).unordered_merge_chaining);
            EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(i).two_phase_remove_location);
            EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(i).no_implicit_indexing_of_active_buckets);
            EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(i).document_condition_probe);
        }
    }

    ASSERT_EQ(expected_msgs, _sender.commands().size());
    for (uint32_t i = 0; i < _sender.commands().size(); i++) {
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(state, *_sender.command(i),
                                                  dummy_buckets_to_return, [i](auto& reply) noexcept {
            // Pretend nodes 1 and 2 are on a shiny version with support for new features.
            // Node 0 does not support the fanciness.
            if (i > 0) {
                reply.supported_node_features().unordered_merge_chaining = true;
                reply.supported_node_features().two_phase_remove_location = true;
                reply.supported_node_features().no_implicit_indexing_of_active_buckets = true;
                reply.supported_node_features().document_condition_probe = true;
            }
        }));
    }

    // Node features should be propagated to all stripes
    for (auto* s : stripes) {
        EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(0).unordered_merge_chaining);
        EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(0).two_phase_remove_location);
        EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(0).no_implicit_indexing_of_active_buckets);
        EXPECT_FALSE(s->node_supported_features_repo().node_supported_features(0).document_condition_probe);

        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(1).unordered_merge_chaining);
        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(1).two_phase_remove_location);
        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(1).no_implicit_indexing_of_active_buckets);
        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(1).document_condition_probe);

        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(2).unordered_merge_chaining);
        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(2).two_phase_remove_location);
        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(2).no_implicit_indexing_of_active_buckets);
        EXPECT_TRUE(s->node_supported_features_repo().node_supported_features(2).document_condition_probe);
    }
}

TEST_F(TopLevelBucketDBUpdaterTest, outdated_bucket_info_reply_is_ignored) {
    set_cluster_state("version:1 distributor:1 storage:1");
    ASSERT_EQ(message_count(1), _sender.commands().size());
    auto req = std::dynamic_pointer_cast<api::RequestBucketInfoCommand>(_sender.commands().front());
    _sender.clear();
    // Force a new pending cluster state which overwrites the pending one.
    lib::ClusterState new_state("version:2 distributor:1 storage:2");
    set_cluster_state(new_state);

    const api::StorageMessageAddress& address(*req->getAddress());
    bool handled = bucket_db_updater().onRequestBucketInfoReply(
            make_fake_bucket_reply(new_state, *req, address.getIndex(), 0, 0));
    EXPECT_TRUE(handled); // Should be returned as handled even though it's technically ignored.
}


struct BucketDBUpdaterSnapshotTest : TopLevelBucketDBUpdaterTest {
    lib::ClusterState empty_state;
    std::shared_ptr<lib::ClusterState> initial_baseline;
    std::shared_ptr<lib::ClusterState> initial_default;
    lib::ClusterStateBundle initial_bundle;
    Bucket default_bucket;
    Bucket global_bucket;

    BucketDBUpdaterSnapshotTest()
        : TopLevelBucketDBUpdaterTest(),
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
        TopLevelBucketDBUpdaterTest::SetUp();
        set_stale_reads_enabled(true);
    };

    // Assumes that the distributor owns all buckets, so it may choose any arbitrary bucket in the bucket space
    uint32_t buckets_in_snapshot_matching_current_db(bool check_mutable_repo, BucketSpace bucket_space) {
        uint32_t found_buckets = 0;
        for (auto* s : distributor_stripes()) {
            auto rs = s->bucket_db_updater().read_snapshot_for_bucket(Bucket(bucket_space, BucketId(16, 1234)));
            if (!rs.is_routable()) {
                return 0;
            }
            auto guard = rs.steal_read_guard();
            auto& repo = check_mutable_repo ? mutable_repo(*s) : read_only_repo(*s);
            for_each_bucket(repo, [&](const auto& space, const auto& entry) {
                if (space == bucket_space) {
                    auto entries = guard->find_parents_and_self(entry.getBucketId());
                    if (entries.size() == 1) {
                        ++found_buckets;
                    }
                }
            });
        }
        return found_buckets;
    }
};

BucketDBUpdaterSnapshotTest::~BucketDBUpdaterSnapshotTest() = default;

TEST_F(BucketDBUpdaterSnapshotTest, default_space_snapshot_prior_to_activated_state_is_non_routable) {
    auto rs = stripe_of_bucket(default_bucket).bucket_db_updater().read_snapshot_for_bucket(default_bucket);
    EXPECT_FALSE(rs.is_routable());
}

TEST_F(BucketDBUpdaterSnapshotTest, global_space_snapshot_prior_to_activated_state_is_non_routable) {
    auto rs = stripe_of_bucket(global_bucket).bucket_db_updater().read_snapshot_for_bucket(global_bucket);
    EXPECT_FALSE(rs.is_routable());
}

TEST_F(BucketDBUpdaterSnapshotTest, read_snapshot_returns_appropriate_cluster_states) {
    set_cluster_state_bundle(initial_bundle);
    // State currently pending, empty initial state is active

    auto def_rs = stripe_of_bucket(default_bucket).bucket_db_updater().read_snapshot_for_bucket(default_bucket);
    EXPECT_EQ(def_rs.context().active_cluster_state()->toString(), empty_state.toString());
    EXPECT_EQ(def_rs.context().default_active_cluster_state()->toString(), empty_state.toString());
    ASSERT_TRUE(def_rs.context().has_pending_state_transition());
    EXPECT_EQ(def_rs.context().pending_cluster_state()->toString(), initial_default->toString());

    auto global_rs = stripe_of_bucket(global_bucket).bucket_db_updater().read_snapshot_for_bucket(global_bucket);
    EXPECT_EQ(global_rs.context().active_cluster_state()->toString(), empty_state.toString());
    EXPECT_EQ(global_rs.context().default_active_cluster_state()->toString(), empty_state.toString());
    ASSERT_TRUE(global_rs.context().has_pending_state_transition());
    EXPECT_EQ(global_rs.context().pending_cluster_state()->toString(), initial_baseline->toString());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(*initial_baseline, message_count(1), 0));
    // State now activated, no pending

    def_rs = stripe_of_bucket(default_bucket).bucket_db_updater().read_snapshot_for_bucket(default_bucket);
    EXPECT_EQ(def_rs.context().active_cluster_state()->toString(), initial_default->toString());
    EXPECT_EQ(def_rs.context().default_active_cluster_state()->toString(), initial_default->toString());
    EXPECT_FALSE(def_rs.context().has_pending_state_transition());

    global_rs = stripe_of_bucket(global_bucket).bucket_db_updater().read_snapshot_for_bucket(global_bucket);
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
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(true, FixedBucketSpaces::default_space()), n_buckets);
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(true, FixedBucketSpaces::global_space()), n_buckets);
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_returns_unroutable_for_non_owned_bucket_in_current_state) {
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:2 storage:4", 0, 4,
                                                               "version:2 distributor:2 .0.s:d storage:4", 0, 0));
    EXPECT_FALSE(activate_cluster_state_version(2));
    // We're down in state 2 and therefore do not own any buckets
    auto def_rs = stripe_of_bucket(default_bucket).bucket_db_updater().read_snapshot_for_bucket(default_bucket);
    EXPECT_FALSE(def_rs.is_routable());
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_with_pending_state_returns_read_only_guard_for_bucket_only_owned_in_current_state) {
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                               "version:2 distributor:2 .0.s:d storage:4", 0, 0));
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(false, FixedBucketSpaces::default_space()), n_buckets);
    EXPECT_EQ(buckets_in_snapshot_matching_current_db(false, FixedBucketSpaces::global_space()), n_buckets);
}

TEST_F(BucketDBUpdaterSnapshotTest, snapshot_is_unroutable_if_stale_reads_disabled_and_bucket_not_owned_in_pending_state) {
    set_stale_reads_enabled(false);
    constexpr uint32_t n_buckets = 10;
    ASSERT_NO_FATAL_FAILURE(
            trigger_completed_but_not_yet_activated_transition("version:1 distributor:1 storage:4", n_buckets, 4,
                                                               "version:2 distributor:2 .0.s:d storage:4", 0, 0));
    auto def_rs = stripe_of_bucket(default_bucket).bucket_db_updater().read_snapshot_for_bucket(default_bucket);
    EXPECT_FALSE(def_rs.is_routable());
}

}
