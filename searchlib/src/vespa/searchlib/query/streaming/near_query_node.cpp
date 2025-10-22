// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_query_node.h"
#include "hit_iterator_pack.h"
#include <vespa/searchlib/queryeval/near_search_utils.h>
#include <vespa/vespalib/objects/visit.hpp>

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
    Hit max_pos(0, 0, 0, 0);
    auto& children = getChildren();
    if (num_negative_terms() >= children.size()) {
        return; // No positive terms
    }
    hit_lists.resize(children.size());
    NegativeTermChecker filter(*this);
    size_t num_positive_terms = children.size() - num_negative_terms();
    for (size_t i = 0; i < children.size(); ++i) {
        auto& hit_list = children[i]->evaluateHits(hit_lists[i]);
        if (i < num_positive_terms) {
            if (hit_list.empty()) {
                return; // Empty term
            }
            if (max_pos.key() < hit_list.front().key()) {
                max_pos = hit_list.front();
            }
            queue.push(HitIterator(hit_list));
        } else {
            filter.add(hit_list);
        }
    }
    for (;;) {
        auto& front = queue.front();
        auto last_allowed = calc_window_end_pos(*front);
        if (!(last_allowed < max_pos.key())) {
            if (filter.check_window(*front, max_pos)) {
                match_result.register_match(front.get_field_element().second);
                if constexpr (MatchResult::shortcut_return) {
                    return;
                }
            }
        }
        do {
            ++front;
            if (!front.valid()) {
                return;
            }
            last_allowed = calc_window_end_pos(*front);
        } while (last_allowed < max_pos.key());
        if (max_pos.key() < front->key()) {
            max_pos = *front;
        }
        queue.adjust();
    }
}

bool
NearQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    BoolMatchResult match_result;
    evaluate_helper(match_result);
    _cached_evaluate_result.emplace(match_result.is_match());
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
    visit(visitor, "num_negative_terms", static_cast<uint64_t>(_num_negative_terms));
    visit(visitor, "exclusion_distance", static_cast<uint64_t>(_exclusion_distance));
}

}
