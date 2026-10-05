// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "grouping_pass_details.h"

#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/vespalib/data/slime/cursor.h>

namespace search::grouping {

using search::aggregation::Group;
using search::aggregation::Grouping;

namespace {

void count_children(const Group& group, size_t level, std::vector<size_t>& counts) {
    if (level >= counts.size()) {
        return;
    }
    counts[level] += group.getChildrenSize();
    for (size_t i = 0; i < group.getChildrenSize(); ++i) {
        count_children(group.getChild(i), level + 1, counts);
    }
}

} // namespace

GroupingPassDetails::GroupingPassDetails() noexcept
    : id(0),
      first_level(0),
      last_level(0),
      top_n(-1),
      summary_hits(0),
      from_session(false),
      session_done(false),
      time_ms(0.0),
      levels() {
}

GroupingPassDetails::GroupingPassDetails(const GroupingPassDetails&) = default;
GroupingPassDetails& GroupingPassDetails::operator=(const GroupingPassDetails&) = default;
GroupingPassDetails::GroupingPassDetails(GroupingPassDetails&&) noexcept = default;
GroupingPassDetails& GroupingPassDetails::operator=(GroupingPassDetails&&) noexcept = default;
GroupingPassDetails::~GroupingPassDetails() = default;

std::vector<size_t> GroupingPassDetails::count_groups_per_level(const Grouping& grouping) {
    std::vector<size_t> counts(grouping.getLevels().size(), 0);
    count_children(grouping.getRoot(), 0, counts);
    return counts;
}

void GroupingPassDetails::as_slime(vespalib::slime::Cursor& obj) const {
    obj.setLong("id", id);
    obj.setLong("first_level", first_level);
    obj.setLong("last_level", last_level);
    obj.setLong("top_n", top_n);
    obj.setLong("summary_hits", summary_hits);
    obj.setBool("from_session", from_session);
    if (from_session) {
        obj.setBool("session_done", session_done);
        obj.setDouble("time_ms", time_ms);
    }
    auto& level_array = obj.setArray("levels");
    for (const auto& level : levels) {
        auto& lobj = level_array.addObject();
        lobj.setLong("max_groups", level.max_groups);
        lobj.setLong("precision", level.precision);
        lobj.setLong("groups", level.groups);
        if (from_session) {
            lobj.setLong("session_groups", level.session_groups);
        }
    }
}

GroupingPassTrace::GroupingPassTrace() noexcept : groupings(), serialize_ms(0.0) {
}

GroupingPassTrace::~GroupingPassTrace() = default;

void GroupingPassTrace::as_slime(vespalib::slime::Cursor& obj) const {
    obj.setDouble("serialize_ms", serialize_ms);
    auto& array = obj.setArray("groupings");
    for (const auto& details : groupings) {
        details.as_slime(array.addObject());
    }
}

} // namespace search::grouping
