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
 *
 * Conventions for the rendered fields: limits taken from the request (top_n, max_groups,
 * precision) are always present and use -1 for "no limit". Fields that only apply when the result
 * was produced from the session (session_done, time_ms, session_groups) are left out when
 * from_session is false.
 **/
struct GroupingPassDetails {
    struct Level {
        int64_t max_groups;
        int64_t precision;
        // Groups at this level in the result returned from this pass.
        size_t groups;
        // Groups at this level in the full result kept by the session at the start of this pass,
        // that is before this pass pruned it down to the groups the container asked for. The
        // effect of that pruning shows up in the session_groups of the next pass. Only meaningful
        // when from_session is set.
        size_t session_groups;
    };

    uint32_t id;
    uint32_t first_level;
    uint32_t last_level;
    int64_t  top_n;
    // Number of document hits (from hits aggregations, e.g. each(output(summary()))) in the
    // returned result. This is not the number of documents that were grouped.
    size_t summary_hits;
    // The result was produced from the full result kept by the session. This is the case for every
    // pass of a grouping that needs more than one pass, including the first. It is false when the
    // grouping was completed in a single pass (all levels were requested at once, or the request
    // had no session id), in which case hits were aggregated directly into the request, and on a
    // later pass for a grouping the session no longer holds.
    bool from_session;
    // The session keeps no state for this grouping after this pass. Only meaningful when
    // from_session is set.
    bool session_done;
    // Time spent pruning the session's full result and merging it into the request for this
    // grouping. Only meaningful when from_session is set.
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
     * Render these details as fields of the given object.
     **/
    void as_slime(vespalib::slime::Cursor& obj) const;
};

/**
 * Everything collected for query tracing from one pass of GroupingSession::continueExecution().
 **/
struct GroupingPassTrace {
    std::vector<GroupingPassDetails> groupings;
    // Time spent serializing the result of this pass.
    double serialize_ms;

    GroupingPassTrace() noexcept;
    ~GroupingPassTrace();

    /**
     * Render as fields of the given object: serialize_ms, and groupings as an array with one
     * object per grouping.
     **/
    void as_slime(vespalib::slime::Cursor& obj) const;
};

} // namespace search::grouping
