// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace vespalib::slime {
struct Cursor;
}
namespace search::aggregation {
class Grouping;
}
namespace search::grouping {

/**
 * Details about how a single grouping request was handled by one pass of
 * GroupingSession::continueExecution(), collected for query tracing.
 **/
struct GroupingPassDetails {
    struct Level {
        int64_t max_groups;
        int64_t precision;
        // Groups at this level in the result returned from this pass.
        size_t groups;
        // Groups at this level in the full result kept by the session, before it was pruned down
        // to the groups the container asked for. Only meaningful when from_session is set.
        size_t session_groups;
    };

    uint32_t id;
    uint32_t first_level;
    uint32_t last_level;
    int64_t  top_n;
    // Number of hits in the returned result.
    size_t hits;
    // The result was produced from the full result kept by the session (multi-pass grouping),
    // rather than aggregated directly into the request.
    bool from_session;
    // The session keeps no state for this grouping after this pass. Only meaningful when
    // from_session is set.
    bool session_done;
    // Time spent producing the result for this grouping in this pass (pruning the session's full
    // result and merging it into the request), excluding serialization.
    double             time_ms;
    std::vector<Level> levels;

    GroupingPassDetails() noexcept;
    GroupingPassDetails(const GroupingPassDetails&);
    GroupingPassDetails& operator=(const GroupingPassDetails&);
    GroupingPassDetails(GroupingPassDetails&&) noexcept;
    GroupingPassDetails& operator=(GroupingPassDetails&&) noexcept;
    ~GroupingPassDetails();

    /**
     * Count the visible groups per level of the grouping tree. The returned vector has one entry
     * per grouping level; the root group is not counted.
     **/
    static std::vector<size_t> count_groups_per_level(const aggregation::Grouping& grouping);

    /**
     * Render the details of each grouping as an object appended to the given array.
     **/
    static void as_slime(const std::vector<GroupingPassDetails>& details, vespalib::slime::Cursor& array);
};

} // namespace search::grouping
