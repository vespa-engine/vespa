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
    : id(0), first_level(0), last_level(0), top_n(-1), hits(0), from_session(false), session_done(false), levels() {
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

void GroupingPassDetails::as_slime(const std::vector<GroupingPassDetails>& details, vespalib::slime::Cursor& array) {
    for (const auto& d : details) {
        auto& obj = array.addObject();
        obj.setLong("id", d.id);
        obj.setLong("first_level", d.first_level);
        obj.setLong("last_level", d.last_level);
        if (d.top_n >= 0) {
            obj.setLong("top_n", d.top_n);
        }
        obj.setLong("hits", d.hits);
        obj.setBool("from_session", d.from_session);
        if (d.from_session) {
            obj.setBool("session_done", d.session_done);
        }
        auto& levels = obj.setArray("levels");
        for (const auto& level : d.levels) {
            auto& lobj = levels.addObject();
            lobj.setLong("max_groups", level.max_groups);
            lobj.setLong("precision", level.precision);
            lobj.setLong("groups", level.groups);
            if (d.from_session) {
                lobj.setLong("session_groups", level.session_groups);
            }
        }
    }
}

} // namespace search::grouping
