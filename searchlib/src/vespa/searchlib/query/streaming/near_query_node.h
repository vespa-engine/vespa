// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"
#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>

namespace search::streaming {

/**
   N-ary Near operator. All terms must be within the given distance.
*/
class NearQueryNode : public AndQueryNode
{
    uint32_t                   _distance;
    const search::queryeval::IElementGapInspector& _element_gap_inspector;
protected:
    search::fef::ElementGap get_element_gap(uint32_t field_id) const {
        return _element_gap_inspector.get_element_gap(field_id);
    }
    HitKey calc_window_end_pos(const Hit &hit) const {
        auto element_gap = get_element_gap(hit.field_id());
        if (!element_gap.has_value() || hit.element_length() + element_gap.value() > hit.position() + _distance) {
            return { hit.field_id(), hit.element_id(), hit.position() + _distance };
        } else {
            return { hit.field_id(), hit.element_id() + 1,
                     hit.position() + _distance - hit.element_length() - element_gap.value() };
        }
    }
public:
    explicit NearQueryNode(const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
        : AndQueryNode("NEAR"),
          _distance(0),
          _element_gap_inspector(element_gap_inspector)
    { }
    explicit NearQueryNode(const char * opName, const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
        : AndQueryNode(opName),
          _distance(0),
          _element_gap_inspector(element_gap_inspector)
    { }
    ~NearQueryNode() override;
    bool evaluate() const override;
    void distance(size_t dist)       { _distance = dist; }
    size_t distance()          const { return _distance; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
};

}
