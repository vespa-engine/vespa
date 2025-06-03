// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "content_node_message_stats.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace storage::distributor {

/**
*  Maintains per content node message statistics. These statistics are kept for the
*  lifetime of the distributor process, which enables higher-level components to
*  easily perform deltas on the current vs. previous statistics snapshots.
 *
 * Not thread safe.
 */
class ContentNodeMessageStatsTracker {
public:
    struct NodeStats {
        vespalib::hash_map<uint16_t, ContentNodeMessageStats> per_node;

        // Hide away the hash_map linkage
        NodeStats();
        explicit NodeStats(std::initializer_list<std::pair<uint16_t, ContentNodeMessageStats>> node_stats);
        ~NodeStats();
        NodeStats(const NodeStats&);
        NodeStats& operator=(const NodeStats&);
        NodeStats(NodeStats&&) noexcept;
        NodeStats& operator=(NodeStats&&) noexcept;

        // Merge the per-node statistics in `rhs` into `this`.
        void merge(const NodeStats& rhs);
        // Returns the delta of all statistics in `this` and `rhs` as a copy.
        // If a node exists in the mapping in `this` and not in `rhs` it will be retained
        // unchanged in the returned value (it is as-if a node exists in `rhs` with all
        // fields equal to 0).
        // If the delta for a given node is all zeroes, it will not be present in the
        // returned state.
        // Precondition: fields in `this` are >= those of `rhs`.
        [[nodiscard]] NodeStats sparse_subtracted(const NodeStats& rhs) const;

        bool operator==(const NodeStats&) const noexcept;
    };

    ContentNodeMessageStatsTracker();
    ~ContentNodeMessageStatsTracker();

    [[nodiscard]] NodeStats node_stats() const;

    // Returned references only valid until next call to stats_for()
    [[nodiscard]] ContentNodeMessageStats& stats_for(uint16_t node);
    [[nodiscard]] const ContentNodeMessageStats& stats_for(uint16_t node) const;
private:
    NodeStats _node_stats;
};

}
