// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/content_node_message_stats_tracker.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>

namespace storage::distributor {

using namespace ::testing;

using Stats = ContentNodeMessageStats;

TEST(ContentNodeMessageStatsTest, stats_are_initially_zeroed) {
    Stats s;
    EXPECT_TRUE(s.all_zero());
    EXPECT_EQ(s.sum_errors(), 0);
    EXPECT_EQ(s.sum_received(), 0);
}

TEST(ContentNodeMessageStatsTest, category_summing_is_across_stats) {
    Stats s(39, 3, 5, 7, 11, 13);
    EXPECT_FALSE(s.all_zero());
    EXPECT_EQ(s.sum_errors(), 5+7+11);
    EXPECT_EQ(s.sum_received(), 3+5+7+11);
}

TEST(ContentNodeMessageStatsTest, subtraction_returns_delta_of_all_stats) {
    Stats s1( 1,  2,  3,  4,  5,  6);
    Stats s2(10, 20, 30, 40, 50, 60);
    EXPECT_EQ(s2.subtracted(s1), Stats(9, 18, 27, 36, 45, 54));
}

TEST(ContentNodeMessageStatsTest, merging_adds_across_stats) {
    Stats s1( 1,  2,  3,  4,  5,  6);
    Stats s2(10, 20, 30, 40, 50, 60);
    s1.merge(s2);
    EXPECT_EQ(s1, Stats(11, 22, 33, 44, 55, 66));
}

TEST(ContentNodeMessageStatsTest, errors_are_categorized_based_on_result_code) {
    constexpr auto id = api::MessageType::PUT_REPLY_ID;
    Stats s;
    s.observe_incoming_response_result(id, api::ReturnCode::OK);
    EXPECT_EQ(s.recv_ok, 1);
    // See content_node_message_stats.cpp for rationales on the interpretation of these error codes.
    s.observe_incoming_response_result(id, api::ReturnCode::TEST_AND_SET_CONDITION_FAILED);
    s.observe_incoming_response_result(id, api::ReturnCode::ABORTED);
    s.observe_incoming_response_result(id, api::ReturnCode::BUSY);
    s.observe_incoming_response_result(id, api::ReturnCode::BUCKET_NOT_FOUND);
    s.observe_incoming_response_result(id, api::ReturnCode::BUCKET_DELETED);
    EXPECT_EQ(s.recv_ok, 6);
    s.observe_incoming_response_result(id, static_cast<api::ReturnCode::Result>(mbus::ErrorCode::CONNECTION_ERROR));
    s.observe_incoming_response_result(id, static_cast<api::ReturnCode::Result>(mbus::ErrorCode::NETWORK_ERROR));
    s.observe_incoming_response_result(id, static_cast<api::ReturnCode::Result>(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE));
    s.observe_incoming_response_result(id, api::ReturnCode::TIMEOUT);
    s.observe_incoming_response_result(id, api::ReturnCode::NOT_CONNECTED);
    EXPECT_EQ(s.recv_network_error, 5);
    s.observe_incoming_response_result(id, api::ReturnCode::STALE_TIMESTAMP);
    EXPECT_EQ(s.recv_clock_skew_error, 1);
    s.observe_incoming_response_result(id, api::ReturnCode::DISK_FAILURE);
    EXPECT_EQ(s.recv_other_error, 1);
}

TEST(ContentNodeMessageStatsTest, do_not_attribute_possible_transitive_errors_to_node) {
    // Visitors inherit errors from client data pushes and can't necessarily be
    // attributed to the receiving node.
    constexpr auto maybe_transitive_id = api::MessageType::VISITOR_CREATE_REPLY_ID;
    Stats s;
    s.observe_incoming_response_result(maybe_transitive_id, api::ReturnCode::NOT_CONNECTED);
    EXPECT_EQ(s.recv_other_error, 1); // _not_ counted as network error
}

void PrintTo(const ContentNodeMessageStatsTracker::NodeStats& stats, std::ostream* os) {
    std::vector<std::pair<uint16_t, ContentNodeMessageStats>> ordered_stats(stats.per_node.begin(), stats.per_node.end());
    std::sort(ordered_stats.begin(), ordered_stats.end(), [](const auto& lhs, const auto& rhs) noexcept {
        return lhs.first < rhs.first;
    });
    *os << "Stats{";
    bool first = true;
    for (const auto& s : ordered_stats) {
        if (!first) {
            *os << ", ";
        } else {
            first = false;
        }
        *os << s.first << ": " << s.second;
    }
    *os << "}";
}

struct ContentNodeMessageStatsTrackerTest : Test {
    using Tracker   = ContentNodeMessageStatsTracker;
    using NodeStats = Tracker::NodeStats;

    Tracker _tracker;

    NodeStats node_stats() const { return _tracker.node_stats(); }
};

TEST_F(ContentNodeMessageStatsTrackerTest, snapshot_is_intially_empty) {
    EXPECT_EQ(node_stats(), NodeStats());
}

TEST_F(ContentNodeMessageStatsTrackerTest, counters_are_monotonic) {
    _tracker.stats_for(0).observe_outgoing_request();
    EXPECT_EQ(node_stats(), NodeStats({{0, Stats(1, 0, 0, 0, 0, 0)}}));
    _tracker.stats_for(0).observe_outgoing_request();
    EXPECT_EQ(node_stats(), NodeStats({{0, Stats(2, 0, 0, 0, 0, 0)}}));
    _tracker.stats_for(0).observe_cancelled();
    EXPECT_EQ(node_stats(), NodeStats({{0, Stats(2, 0, 0, 0, 0, 1)}}));
    _tracker.stats_for(0).observe_cancelled();
    EXPECT_EQ(node_stats(), NodeStats({{0, Stats(2, 0, 0, 0, 0, 2)}}));
}

TEST_F(ContentNodeMessageStatsTrackerTest, stats_are_tracked_across_nodes) {
    _tracker.stats_for(0).observe_outgoing_request();
    _tracker.stats_for(2).observe_outgoing_request();
    _tracker.stats_for(5).observe_outgoing_request();
    _tracker.stats_for(2).observe_cancelled();
    _tracker.stats_for(5).observe_incoming_response_result(api::MessageType::PUT_REPLY_ID, api::ReturnCode::NOT_CONNECTED);

    EXPECT_EQ(node_stats(), NodeStats({{0, Stats(1, 0, 0, 0, 0, 0)},
                                       {2, Stats(1, 0, 0, 0, 0, 1)},
                                       {5, Stats(1, 0, 1, 0, 0, 0)}}));
}

TEST_F(ContentNodeMessageStatsTrackerTest, stats_can_be_merged_across_nodes) {
    Tracker t1;
    Tracker t2;
    Tracker t3;

    t1.stats_for(0).observe_outgoing_request();
    t2.stats_for(2).observe_outgoing_request();
    t3.stats_for(0).observe_outgoing_request();
    t3.stats_for(5).observe_outgoing_request();

    NodeStats stats;
    stats.merge(t1.node_stats());
    stats.merge(t2.node_stats());
    stats.merge(t3.node_stats());

    EXPECT_EQ(stats, NodeStats({{0, Stats(2, 0, 0, 0, 0, 0)},
                                {2, Stats(1, 0, 0, 0, 0, 0)},
                                {5, Stats(1, 0, 0, 0, 0, 0)}}));
}

TEST_F(ContentNodeMessageStatsTrackerTest, node_stats_subtraction_returns_per_node_delta) {
    _tracker.stats_for(0).observe_outgoing_request();
    _tracker.stats_for(0).observe_incoming_response_result(api::MessageType::PUT_REPLY_ID, api::ReturnCode::NOT_CONNECTED);
    _tracker.stats_for(1).observe_outgoing_request();
    _tracker.stats_for(2).observe_outgoing_request();
    const auto stats_before = node_stats();

    _tracker.stats_for(0).observe_outgoing_request();
    _tracker.stats_for(1).observe_outgoing_request();
    _tracker.stats_for(2).observe_incoming_response_result(api::MessageType::PUT_REPLY_ID, api::ReturnCode::NOT_CONNECTED);
    const auto stats_after = node_stats();
    const auto delta = stats_after.sparse_subtracted(stats_before);

    EXPECT_EQ(delta, NodeStats({{0, Stats(1, 0, 0, 0, 0, 0)},
                                {1, Stats(1, 0, 0, 0, 0, 0)},
                                {2, Stats(0, 0, 1, 0, 0, 0)}}));
}

TEST_F(ContentNodeMessageStatsTrackerTest, nodes_with_zero_deltas_are_not_included_in_subtraction_result) {
    _tracker.stats_for(0).observe_outgoing_request();
    _tracker.stats_for(1).observe_outgoing_request();
    const auto stats_before = node_stats();
    _tracker.stats_for(1).observe_outgoing_request();
    const auto stats_after = node_stats();
    const auto delta = stats_after.sparse_subtracted(stats_before);
    // Only node 1 has a non-zero delta
    EXPECT_EQ(delta, NodeStats({{1, Stats(1, 0, 0, 0, 0, 0)}}));
}

}
