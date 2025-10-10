// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_query_node.h"
#include "hit_iterator_pack.h"
#include <vespa/searchlib/queryeval/near_search_utils.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/util/priority_queue.h>

using search::queryeval::near_search_utils::BoolMatchResult;
using search::queryeval::near_search_utils::ElementIdMatchResult;
using vespalib::PriorityQueue;

namespace search::streaming {

NearQueryNode::~NearQueryNode() = default;

template <typename MatchResult>
void
NearQueryNode::evaluate_helper(MatchResult& match_result) const
{
    PriorityQueue<HitIterator> queue;
    std::vector<HitList> hit_lists;
    HitKey max_key(0, 0, 0);
    auto& children = getChildren();
    if (children.empty()) {
        return; // No terms
    }
    hit_lists.reserve(children.size());
    for (auto& child : children) {
        auto& hit_list = child->evaluateHits(hit_lists.emplace_back());
        if (hit_list.empty()) {
            return; // Empty term
        }
        if (max_key < hit_list.front().key()) {
            max_key = hit_list.front().key();
        }
        queue.push(HitIterator(hit_list));
    }
    for (;;) {
        auto& front = queue.front();
        auto last_allowed = calc_window_end_pos(*front);
        if (!(last_allowed < max_key)) {
            match_result.register_match(front.get_field_element().second);
            if constexpr (MatchResult::shortcut_return) {
                return;
            }
        }
        do {
            ++front;
            if (!front.valid()) {
                return;
            }
            last_allowed = calc_window_end_pos(*front);
        } while (last_allowed < max_key);
        if (max_key < front->key()) {
            max_key = front->key();
        }
        queue.adjust();
    }
}

bool
NearQueryNode::evaluate()
{
    BoolMatchResult match_result;
    evaluate_helper(match_result);
    return match_result.is_match();
}

void
NearQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    // Retrieve the elements that matched
    ElementIdMatchResult match_result(element_ids);;
    evaluate_helper(match_result);
    match_result.maybe_sort_element_ids();
}

void
NearQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", static_cast<uint64_t>(_distance));
}

}
