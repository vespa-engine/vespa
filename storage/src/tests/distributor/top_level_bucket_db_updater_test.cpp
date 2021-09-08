// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/top_level_bucket_db_updater.h>
#include <vespa/storage/distributor/bucket_space_distribution_context.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/pending_bucket_space_db_transition.h>
#include <vespa/storage/distributor/outdated_nodes_map.h>
#include <vespa/storage/storageutil/distributorstatecache.h>
#include <tests/distributor/top_level_distributor_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/simpleclusterinformation.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
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

class TopLevelBucketDBUpdaterTest : public Test,
                                    public TopLevelDistributorTestUtil
{
public:
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

    std::string verifyBucket(document::BucketId id, const lib::ClusterState& state) {
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

    void set_cluster_state(const vespalib::string& state_str) {
        set_cluster_state(lib::ClusterState(state_str));
    }

    bool activate_cluster_state_version(uint32_t version) {
        return bucket_db_updater().onActivateClusterStateVersion(
                std::make_shared<api::ActivateClusterStateVersionCommand>(version));
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

    api::StorageMessageAddress storage_address(uint16_t node) {
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

TEST_F(TopLevelBucketDBUpdaterTest, normal_usage) {
    set_cluster_state(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"));

    ASSERT_EQ(message_count(3), _sender.commands().size());

    // Ensure distribution hash is set correctly
    ASSERT_EQ(_component->getDistribution()->getNodeGraph().getDistributionConfigHash(),
              dynamic_cast<const RequestBucketInfoCommand&>(*_sender.command(0)).getDistributionHash());

    ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
                                              *_sender.command(0), 10));

    _sender.clear();

    // Optimization for not refetching unneeded data after cluster state
    // change is only implemented after completion of previous cluster state
    set_cluster_state("distributor:2 .0.s:i storage:3");

    ASSERT_EQ(message_count(3), _sender.commands().size());
    // Expect reply of first set SystemState request.
    ASSERT_EQ(size_t(1), _sender.replies().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(
            lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
            message_count(3), 10));
    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(10, "distributor:2 storage:3"));
}

TEST_F(TopLevelBucketDBUpdaterTest, distributor_change) {
    int num_buckets = 100;

    // First sends request
    set_cluster_state("distributor:2 .0.s:i .1.s:i storage:3");
    ASSERT_EQ(message_count(3), _sender.commands().size());
    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:2 .0.s:i .1.s:i storage:3"),
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
    set_cluster_state("distributor:1 .0.s:i storage:1 .0.s:i");

    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    // Not yet passing on system state.
    ASSERT_EQ(size_t(0), _sender_down.commands().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                           _bucket_spaces.size(), 10, 10));

    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(10, "distributor:1 storage:1"));

    for (int i = 10; i < 20; ++i) {
        ASSERT_NO_FATAL_FAILURE(verify_invalid(document::BucketId(16, i), 0));
    }

    // Pass on cluster state and recheck buckets now.
    ASSERT_EQ(size_t(1), _sender_down.commands().size());

    _sender.clear();
    _sender_down.clear();

    set_cluster_state("distributor:1 .0.s:i storage:1");

    // Send a new request bucket info up.
    ASSERT_EQ(_bucket_spaces.size(), _sender.commands().size());

    ASSERT_NO_FATAL_FAILURE(complete_bucket_info_gathering(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                           _bucket_spaces.size(), 20));

    // Pass on cluster state and recheck buckets now.
    ASSERT_EQ(size_t(1), _sender_down.commands().size());

    ASSERT_NO_FATAL_FAILURE(assert_correct_buckets(20, "distributor:1 storage:1"));
}

TEST_F(TopLevelBucketDBUpdaterTest, failed_request_bucket_info) {
    set_cluster_state("distributor:1 .0.s:i storage:1");

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
        ASSERT_NO_FATAL_FAILURE(fake_bucket_reply(lib::ClusterState("distributor:1 .0.s:i storage:1"),
                                                  *_sender.command(_bucket_spaces.size() + i), 10));
    }

    for (int i=0; i<10; i++) {
        EXPECT_EQ(std::string(""),
                  verifyBucket(document::BucketId(16, i),
                               lib::ClusterState("distributor:1 storage:1")));
    }

    // Set system state should now be passed on
    EXPECT_EQ(std::string("Set system state"), _sender_down.getCommands());
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

    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=0,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false), "
                          "node(idx=2,crc=0x3,docs=3/3,bytes=3/3,trusted=true,active=false,ready=false)"),
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

    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dump_bucket(bucketlist[0]));
    EXPECT_EQ(std::string("BucketId(0x4000000000000002) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
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

    EXPECT_EQ(std::string("BucketId(0x4000000000000000) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dump_bucket(document::BucketId(16, 0)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000001) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dump_bucket(document::BucketId(16, 1)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000002) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
              dump_bucket(document::BucketId(16, 2)));
    EXPECT_EQ(std::string("BucketId(0x4000000000000004) : "
                          "node(idx=0,crc=0xa,docs=1/1,bytes=1/1,trusted=true,active=false,ready=false)"),
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

}
