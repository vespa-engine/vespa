// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"
#include "hit_iterator.h"
#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search::streaming {

/**
   N-ary Near operator. All terms must be within the given distance.
*/
class NearQueryNode : public AndQueryNode
{
    uint32_t                   _distance;
    uint32_t                   _num_negative_terms;
    uint32_t                   _exclusion_distance;
    const search::queryeval::IElementGapInspector& _element_gap_inspector;
    template <typename MatchResult>
    void evaluate_helper(MatchResult& match_result) const;
protected:
    search::fef::ElementGap get_element_gap(uint32_t field_id) const {
        return _element_gap_inspector.get_element_gap(field_id);
    }
    HitKey calc_window_end_pos(const Hit &hit, uint32_t dist) const {
        auto element_gap = get_element_gap(hit.field_id());
        if (!element_gap.has_value() || hit.element_length() + element_gap.value() > hit.position() + dist) {
            return { hit.field_id(), hit.element_id(), hit.position() + dist };
        } else {
            return { hit.field_id(), hit.element_id() + 1,
                     hit.position() + dist - hit.element_length() - element_gap.value() };
        }
    }
    HitKey calc_window_end_pos(const Hit &hit) const { return calc_window_end_pos(hit, _distance); }
    HitKey calc_last_unsafe_after(const Hit &hit) const { return calc_window_end_pos(hit, _exclusion_distance); }

    // Helper class to efficiently check if negative terms break windows
    // Uses a priority queue to iterate through negative term positions in sorted order
    class NegativeTermChecker {
    private:
        const NearQueryNode& _parent;
        vespalib::PriorityQueue<HitIterator> _queue;

    public:
        explicit NegativeTermChecker(const NearQueryNode& p) : _parent(p), _queue() {}

        void add(const HitList& hits) {
            if (!hits.empty()) {
                _queue.push(hits);
            }
        }

        // Check if the window [window_start, window_end] is ok (not broken by negative terms)
        bool check_window(const Hit& window_start, const Hit& window_end) {
            while (!_queue.empty()) {
                auto& front = _queue.front();
                const auto& pos = *front;
                auto last_unsafe_after_neg = _parent.calc_last_unsafe_after(pos);
                if (last_unsafe_after_neg < window_start.key()) {
                    ++front;
                    if (front.valid()) {
                        _queue.adjust();
                    } else {
                        _queue.pop_front();
                    }
                    continue;
                }
                auto last_unsafe_after_window = _parent.calc_last_unsafe_after(window_end);
                return (last_unsafe_after_window < pos.key());
            }
            return true;
        }
    };
public:
    explicit NearQueryNode(const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
        : AndQueryNode("NEAR"),
          _distance(0),
          _num_negative_terms(0),
          _exclusion_distance(0),
          _element_gap_inspector(element_gap_inspector)
    { }
    explicit NearQueryNode(const char * opName, const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
        : AndQueryNode(opName),
          _distance(0),
          _num_negative_terms(0),
          _exclusion_distance(0),
          _element_gap_inspector(element_gap_inspector)
    { }
    ~NearQueryNode() override;
    bool evaluate() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void distance(size_t dist)       { _distance = dist; }
    size_t distance()          const { return _distance; }
    void num_negative_terms(uint32_t value) { _num_negative_terms = value; }
    uint32_t num_negative_terms() const { return _num_negative_terms; }
    void exclusion_distance(uint32_t value) { _exclusion_distance = value; }
    uint32_t exclusion_distance() const { return _exclusion_distance; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
};

}
