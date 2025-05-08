// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_node_message_stats_tracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>

namespace storage::distributor {

ContentNodeMessageStatsTracker::NodeStats::NodeStats() = default;
ContentNodeMessageStatsTracker::NodeStats::~NodeStats() = default;

ContentNodeMessageStatsTracker::NodeStats::NodeStats(
        std::initializer_list<std::pair<uint16_t, ContentNodeMessageStats>> node_stats)
    : per_node(node_stats)
{}

ContentNodeMessageStatsTracker::NodeStats::NodeStats(const NodeStats&) = default;
ContentNodeMessageStatsTracker::NodeStats&
ContentNodeMessageStatsTracker::NodeStats::operator=(const NodeStats&) = default;

ContentNodeMessageStatsTracker::NodeStats::NodeStats(NodeStats&&) noexcept = default;
ContentNodeMessageStatsTracker::NodeStats&
ContentNodeMessageStatsTracker::NodeStats::operator=(NodeStats&&) noexcept = default;

void ContentNodeMessageStatsTracker::NodeStats::merge(const NodeStats& rhs) {
    for (const auto& s : rhs.per_node) {
        per_node[s.first].merge(s.second);
    }
}

ContentNodeMessageStatsTracker::NodeStats
ContentNodeMessageStatsTracker::NodeStats::sparse_subtracted(const NodeStats& rhs) const {
    ContentNodeMessageStatsTracker::NodeStats ret;
    for (const auto& s : per_node) {
        auto rhs_node = rhs.per_node.find(s.first);
        if (rhs_node != rhs.per_node.end()) {
            const auto delta = s.second.subtracted(rhs_node->second);
            if (!delta.all_zero()) { // "Sparsify" the result set by not including zero-deltas
                ret.per_node[s.first] = delta;
            }
        } else {
            ret.per_node[s.first] = s.second; // As-if subtracting zero from all fields
        }
    }
    return ret;
}

bool ContentNodeMessageStatsTracker::NodeStats::operator==(const NodeStats&) const noexcept = default;

ContentNodeMessageStatsTracker::ContentNodeMessageStatsTracker() = default;
ContentNodeMessageStatsTracker::~ContentNodeMessageStatsTracker() = default;

ContentNodeMessageStatsTracker::NodeStats
ContentNodeMessageStatsTracker::node_stats() const {
    return _node_stats;
}

ContentNodeMessageStats& ContentNodeMessageStatsTracker::stats_for(uint16_t node) {
    return _node_stats.per_node[node];
}

// TODO needed?
const ContentNodeMessageStats& ContentNodeMessageStatsTracker::stats_for(uint16_t node) const {
    static const ContentNodeMessageStats empty_sentinel;
    auto it = _node_stats.per_node.find(node);
    return (it != _node_stats.per_node.end() ? it->second : empty_sentinel);
}

}
