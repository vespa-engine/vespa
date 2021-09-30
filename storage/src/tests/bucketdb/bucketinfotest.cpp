// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/storage/bucketdb/bucketinfo.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using namespace ::testing;

namespace storage::distributor {

BucketInfo
getBucketInfo(std::string nodeList, std::string order) {
    BucketInfo info;

    std::vector<uint16_t> ordering;
    {
        vespalib::StringTokenizer tokenizer(order, ",");
        for (uint32_t i = 0; i < tokenizer.size(); i++) {
            ordering.push_back(atoi(tokenizer[i].data()));
        }
    }

    vespalib::StringTokenizer tokenizer(nodeList, ",");
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        info.addNode(BucketCopy(0,
                                atoi(tokenizer[i].data()),
                                api::BucketInfo(1,1,1)),
                     ordering);
    }

    return info;
}

std::string
nodeList(const BucketInfo& info) {
    std::ostringstream ost;
    for (uint32_t i = 0; i < info.getNodeCount(); i++) {
        if (i != 0) {
            ost << ",";
        }
        ost << (int)info.getNodeRef(i).getNode();
    }
    return ost.str();
}

// Since we keep bucket info in memory for a period of time before applying
// to bucket db, we maintain timestamps to prevent external load happening
// in the meantime from having their updates lost when we perform a batch
// insert. This also applies for when we postpone db updates in persistence
// message tracker until we've received a reply from all copies.
TEST(BucketInfoTest, bucket_info_entries_with_newest_timestamps_are_kept) {
    BucketInfo bi;
    std::vector<uint16_t> idealState;
    idealState.push_back(0);

    bi.addNode(BucketCopy(5, 0, api::BucketInfo(1,1,1)), idealState);
    EXPECT_EQ(api::BucketInfo(1,1,1), bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(5, 0, api::BucketInfo(2,2,2)), idealState);
    EXPECT_EQ(api::BucketInfo(1,1,1), bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(4, 0, api::BucketInfo(3,3,3)), idealState);
    EXPECT_EQ(api::BucketInfo(1,1,1), bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(7, 0, api::BucketInfo(4,4,4)), idealState);
    EXPECT_EQ(api::BucketInfo(4,4,4), bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(2, 1, api::BucketInfo(4,4,4)), idealState);
    EXPECT_EQ(api::BucketInfo(4,4,4), bi.getNode(1)->getBucketInfo());
}

TEST(BucketInfoTest, node_ordering_is_preserved) {
    EXPECT_EQ("2,0,1", nodeList(getBucketInfo("0,1,2", "2,0,1")));
    EXPECT_EQ("2,0,1", nodeList(getBucketInfo("1,0,2", "2,0,1")));
    EXPECT_EQ("1,0,2", nodeList(getBucketInfo("1,2,0", "1")));
    EXPECT_EQ("2,1,0,3,4", nodeList(getBucketInfo("0,1,2,3,4", "2,1")));
}

TEST(BucketInfoTest, can_query_for_replica_with_invalid_info) {
    std::vector<uint16_t> order;

    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(10, 100, 1000)), order);
    EXPECT_FALSE(info.hasInvalidCopy());

    info.addNode(BucketCopy(0, 2, api::BucketInfo()), order);
    EXPECT_TRUE(info.hasInvalidCopy());

}

TEST(BucketInfoTest, add_node_sets_trusted_when_consistent) {
    std::vector<uint16_t> order;

    {
        BucketInfo info;
        info.addNode(BucketCopy(0, 0, api::BucketInfo(0x1, 2, 144)).setTrusted(), order);
        info.addNode(BucketCopy(0, 1, api::BucketInfo(0x1, 2, 144)), order);
        EXPECT_TRUE(info.getNode(1)->trusted());
    }

    {
        BucketInfo info;
        info.addNode(BucketCopy(0, 0, api::BucketInfo(0x1, 1, 2)).setTrusted(), order);
        info.addNode(BucketCopy(0, 1, api::BucketInfo(0x2, 2, 3)), order);
        info.addNode(BucketCopy(0, 2, api::BucketInfo(0x3, 3, 4)), order);

        BucketCopy copy(1, 1, api::BucketInfo(0x1, 1, 2));
        info.updateNode(copy);
        EXPECT_TRUE(info.getNode(1)->trusted());
        EXPECT_FALSE(info.getNode(2)->trusted());
    }
}

TEST(BucketInfoTest, testTrustedResetWhenTrustedCopiesGoOutOfSync) {
    std::vector<uint16_t> order;

    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)).setTrusted(), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(10, 100, 1000)), order);
    EXPECT_TRUE(info.getNode(0)->trusted());
    EXPECT_TRUE(info.getNode(1)->trusted());
    
    info.updateNode(BucketCopy(0, 1, api::BucketInfo(20, 200, 2000)).setTrusted());
    EXPECT_FALSE(info.getNode(0)->trusted());
    EXPECT_FALSE(info.getNode(1)->trusted());
}

TEST(BucketInfoTest, trusted_not_reset_when_non_trusted_copies_still_out_of_sync) {
    std::vector<uint16_t> order;

    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)).setTrusted(), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(20, 200, 2000)), order);
    info.addNode(BucketCopy(0, 2, api::BucketInfo(30, 300, 3000)), order);
    EXPECT_TRUE(info.getNode(0)->trusted());
    EXPECT_FALSE(info.getNode(1)->trusted());
    EXPECT_FALSE(info.getNode(2)->trusted());
    
    info.updateNode(BucketCopy(0, 1, api::BucketInfo(21, 201, 2001)));

    EXPECT_TRUE(info.getNode(0)->trusted());
    EXPECT_FALSE(info.getNode(1)->trusted());
    EXPECT_FALSE(info.getNode(2)->trusted());
}

TEST(BucketInfoTest, add_nodes_can_immediately_update_trusted_flag) {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000))}, order, TrustedUpdate::UPDATE);
    // Only one replica, so implicitly trusted iff trusted flag update is invoked.
    EXPECT_TRUE(info.getNode(0)->trusted());
}

TEST(BucketInfoTest, add_nodes_can_defer_update_of_trusted_flag) {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000))}, order, TrustedUpdate::DEFER);
    EXPECT_FALSE(info.getNode(0)->trusted());
}

TEST(BucketInfoTest, remove_node_can_immediately_update_trusted_flag) {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)),
                   BucketCopy(0, 1, api::BucketInfo(20, 200, 2000))},
                  order, TrustedUpdate::UPDATE);
    EXPECT_FALSE(info.getNode(0)->trusted());
    info.removeNode(1, TrustedUpdate::UPDATE);
    // Only one replica remaining after remove, so implicitly trusted iff trusted flag update is invoked.
    EXPECT_TRUE(info.getNode(0)->trusted());
}

TEST(BucketInfoTest, remove_node_can_defer_update_of_trusted_flag) {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)),
                   BucketCopy(0, 1, api::BucketInfo(20, 200, 2000))},
                  order, TrustedUpdate::UPDATE);
    info.removeNode(1, TrustedUpdate::DEFER);
    EXPECT_FALSE(info.getNode(0)->trusted());
}

TEST(BucketInfoTest, no_majority_consistent_bucket_for_too_few_replicas) {
    std::vector<uint16_t> order;
    BucketInfo info;
    // No majority with 0 nodes, for all the obvious reasons.
    EXPECT_FALSE(info.majority_consistent_bucket_info().valid());
    // 1 is technically a majority of 1, but it doesn't make sense from the perspective
    // of preventing activation of minority replicas.
    info.addNode(BucketCopy(0, 0, api::BucketInfo(0x1, 2, 144)), order);
    EXPECT_FALSE(info.majority_consistent_bucket_info().valid());
    // Similarly, for 2 out of 2 nodes in sync we have no minority (so no point in reporting),
    // and with 1 out of 2 nodes we have no idea which of the nodes to treat as "authoritative".
    info.addNode(BucketCopy(0, 1, api::BucketInfo(0x1, 2, 144)), order);
    EXPECT_FALSE(info.majority_consistent_bucket_info().valid());
}

TEST(BucketInfoTest, majority_consistent_bucket_info_can_be_inferred) {
    std::vector<uint16_t> order;
    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(0x1, 2, 144)), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(0x1, 2, 144)), order);
    info.addNode(BucketCopy(0, 2, api::BucketInfo(0x1, 2, 144)), order);

    auto maj_info = info.majority_consistent_bucket_info();
    ASSERT_TRUE(maj_info.valid());
    EXPECT_EQ(maj_info, api::BucketInfo(0x1, 2, 144));

    // 3 of 4 in sync, still majority.
    info.addNode(BucketCopy(0, 3, api::BucketInfo(0x1, 3, 255)), order);

    maj_info = info.majority_consistent_bucket_info();
    ASSERT_TRUE(maj_info.valid());
    EXPECT_EQ(maj_info, api::BucketInfo(0x1, 2, 144));

    // 3 of 5 in sync, still majority.
    info.addNode(BucketCopy(0, 4, api::BucketInfo(0x1, 3, 255)), order);

    maj_info = info.majority_consistent_bucket_info();
    ASSERT_TRUE(maj_info.valid());
    EXPECT_EQ(maj_info, api::BucketInfo(0x1, 2, 144));

    // 3 of 6 mutually in sync, no majority.
    info.addNode(BucketCopy(0, 5, api::BucketInfo(0x1, 3, 255)), order);

    maj_info = info.majority_consistent_bucket_info();
    EXPECT_FALSE(maj_info.valid());

    // 4 out of 7 in sync; majority.
    info.addNode(BucketCopy(0, 6, api::BucketInfo(0x1, 3, 255)), order);

    maj_info = info.majority_consistent_bucket_info();
    ASSERT_TRUE(maj_info.valid());
    EXPECT_EQ(maj_info, api::BucketInfo(0x1, 3, 255));
}

} // storage::distributor
