// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onear_query_node.h"
#include "hit_iterator_pack.h"
#include <vespa/searchlib/queryeval/near_search_utils.h>
#include <span>

using search::queryeval::near_search_utils::BoolMatchResult;
using search::queryeval::near_search_utils::ElementIdMatchResult;

namespace search::streaming {

ONearQueryNode::~ONearQueryNode() = default;

template <typename MatchResult>
void
ONearQueryNode::evaluate_helper(MatchResult& match_result) const
{
    auto& children = getChildren();
    if (num_negative_terms() >= children.size()) {
        return; // No positive terms
    }
    size_t num_positive_terms = children.size() - num_negative_terms();
    HitIteratorPack itr_pack(std::span(children.begin(), num_positive_terms));
    if (!itr_pack.all_valid()) {
        return; // Empty positive term found
    }
    std::vector<HitList> negative_hit_lists;
    negative_hit_lists.resize(num_negative_terms());
    NegativeTermChecker filter(*this);
    for (size_t i = 0; i < num_negative_terms(); ++i) {
        filter.add(children[num_positive_terms + i]->evaluateHits(negative_hit_lists[i]));
    }
    std::span<HitIterator> others(itr_pack.begin() + 1, itr_pack.end());
    if (others.empty()) {
        auto& front = itr_pack.front();
        while (front.valid()) {
            if (filter.check_window(*front, *front)) {
                match_result.register_match(front->key().element_id());
                if constexpr (MatchResult::shortcut_return) {
                    return;
                }
            }
            ++front;
        }
        return; // A single term
    }
    HitKey cur_term_pos(0, 0, 0);
    for (auto& front = itr_pack.front(); front.valid(); ++front) {
        auto last_allowed = calc_window_end_pos(*front);
        if (last_allowed < cur_term_pos) {
            continue;
        }
        auto prev_term_pos = front->key();
        bool match = true;
        for (auto& it : others) {
            while (!(prev_term_pos < it->key())) {
                ++it;
                if (!it.valid()) {
                    return;
                }
            }
            cur_term_pos = it->key();
            if (last_allowed < cur_term_pos) {
                match = false;
                break;
            }
            prev_term_pos = cur_term_pos;
        }
        if (match) {
            if (filter.check_window(*front, *others.back())) {
                match_result.register_match(front->element_id());
                if constexpr (MatchResult::shortcut_return) {
                    return;
                }
            }
        }
    }
}

bool
ONearQueryNode::evaluate()
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
ONearQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    // Retrieve the elements that matched
    ElementIdMatchResult match_result(element_ids);;
    evaluate_helper(match_result);
    match_result.maybe_sort_element_ids();
}

}
