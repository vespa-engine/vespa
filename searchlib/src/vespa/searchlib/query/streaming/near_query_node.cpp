// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_query_node.h"

#include "hit_iterator_pack.h"

#include <vespa/searchlib/queryeval/near_search_flags.h>
#include <vespa/searchlib/queryeval/near_search_utils.h>

#include <vespa/vespalib/objects/visit.hpp>

using search::queryeval::MatchSpan;
using search::queryeval::NearSearchFlags;
using search::queryeval::near_search_utils::BoolMatchResult;
using search::queryeval::near_search_utils::ElementIdMatchResult;
using search::queryeval::near_search_utils::SpanMatchResult;
using vespalib::PriorityQueue;

namespace search::streaming {

HitKey NearQueryNode::NegativeTermChecker::max_window_end(const Hit& window_end, const HitKey& last_allowed) {
    // This function is only called if check_window has succeeded for same window_end. Any negative terms that limits
    // the expansion of the match span is at least (exclusion distance + 1) positions after window_end.
    if (_queue.empty()) {
        return last_allowed;
    }
    const auto& pos = *_queue.front();
    if (pos.field_id() != window_end.field_id() || pos.element_id() > window_end.element_id() + 1) {
        // This is an approximation. last_allowed might be in the element following window_end, while pos might be
        // in the next element. Without information about the length of the middle element, this might lead to
        // a too high value for last_allowed when element gap is specified to have a finite value.
        return last_allowed;
    }
    if (pos.position() > _parent.exclusion_distance()) {
        HitKey avoid_negative_term_window_end(pos.field_id(), pos.element_id(),
                                              pos.position() - _parent.exclusion_distance() - 1);
        return std::min(avoid_negative_term_window_end, last_allowed);
    }
    auto element_gap = _parent.get_element_gap(window_end.field_id());
    if (element_gap.has_value() && pos.element_id() == window_end.element_id() + 1) {
        HitKey avoid_negative_term_window_end(window_end.field_id(), window_end.element_id(),
                                              pos.position() + window_end.element_length() + element_gap.value() -
                                                  _parent.exclusion_distance() - 1);
        return std::min(avoid_negative_term_window_end, last_allowed);
    }
    return last_allowed;
}

NearQueryNode::NearQueryNode(const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
    : AndQueryNode("NEAR"),
      _distance(0),
      _num_negative_terms(0),
      _exclusion_distance(0),
      _element_gap_inspector(element_gap_inspector),
      _match_spans(),
      _filtered_match_spans() {
}

NearQueryNode::NearQueryNode(const char*                                    opName,
                             const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
    : AndQueryNode(opName),
      _distance(0),
      _num_negative_terms(0),
      _exclusion_distance(0),
      _element_gap_inspector(element_gap_inspector),
      _match_spans(),
      _filtered_match_spans() {
}

NearQueryNode::~NearQueryNode() = default;

template <typename MatchResult> void NearQueryNode::evaluate_helper(MatchResult& match_result) const {
    PriorityQueue<HitIterator> queue;
    std::vector<HitList>       hit_lists;
    Hit                        max_pos(0, 0, 0, 0);
    auto&                      children = getChildren();
    if (num_negative_terms() >= children.size()) {
        return; // No positive terms
    }
    hit_lists.resize(children.size());
    NegativeTermChecker filter(*this);
    size_t              num_positive_terms = children.size() - num_negative_terms();
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
        auto  last_allowed = calc_window_end_pos(*front);
        if (!(last_allowed < max_pos.key())) {
            if (filter.check_window(*front, max_pos)) {
                if constexpr (MatchResult::collect_spans) {
                    auto  adjusted_last_allowed = filter.max_window_end(max_pos, last_allowed);
                    auto& backing_vector = queue.backing_vector();
                    auto  extended_max_pos = max_pos;
                    for (auto& itr : backing_vector) {
                        if (&itr != &front) {
                            extended_max_pos = std::max(extended_max_pos, itr.get_max_pos(adjusted_last_allowed));
                        }
                    }
                    match_result.register_match(MatchSpan(*front, extended_max_pos));
                } else {
                    match_result.register_match(front.get_field_element().second);
                }
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

bool NearQueryNode::evaluate() {
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    BoolMatchResult match_result;
    evaluate_helper(match_result);
    _cached_evaluate_result.emplace(match_result.is_match());
    return match_result.is_match();
}

void NearQueryNode::get_element_ids(std::vector<uint32_t>& element_ids) {
    // Retrieve the elements that matched
    ElementIdMatchResult match_result(element_ids);
    evaluate_helper(match_result);
    match_result.maybe_sort_element_ids();
}

void NearQueryNode::get_match_spans(std::vector<MatchSpan>& match_spans) {
    // Retrieve the matching spans
    SpanMatchResult match_result(match_spans);
    evaluate_helper(match_result);
}

void NearQueryNode::unpack_match_data(uint32_t docid, fef::MatchData& match_data,
                                      const fef::IIndexEnvironment& index_env,
                                      search::common::ElementIds    element_ids) {
    if (evaluate()) {
        if (NearSearchFlags::filter_terms()) {
            _match_spans.clear();
            get_match_spans(_match_spans);
            auto match_spans = _filtered_match_spans.intersection(_match_spans, element_ids);
            for (const auto& node : getChildren()) {
                node->unpack_match_data(docid, match_data, index_env, match_spans);
            }
        } else {
            for (const auto& node : getChildren()) {
                node->unpack_match_data(docid, match_data, index_env, element_ids);
            }
        }
    }
}

void NearQueryNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", static_cast<uint64_t>(_distance));
    visit(visitor, "num_negative_terms", static_cast<uint64_t>(_num_negative_terms));
    visit(visitor, "exclusion_distance", static_cast<uint64_t>(_exclusion_distance));
}

} // namespace search::streaming
