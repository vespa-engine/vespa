// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"

namespace search::streaming {

/**
   N-ary Near operator. All terms must be within the given distance.
*/
class NearQueryNode : public AndQueryNode
{
    uint32_t                _distance;
    std::optional<uint32_t> _element_gap;
protected:
    void update_element_gap() const { } // Noop for now
    HitKey calc_window_end_pos(const Hit &hit) const {
        update_element_gap();
        if (!_element_gap.has_value() || hit.element_length() + _element_gap.value() > hit.position() + _distance) {
            return { hit.field_id(), hit.element_id(), hit.position() + _distance };
        } else {
            return { hit.field_id(), hit.element_id() + 1,
                     hit.position() + _distance - hit.element_length() - _element_gap.value() };
        }
    }
public:
    NearQueryNode() noexcept : AndQueryNode("NEAR"), _distance(0) { }
    explicit NearQueryNode(const char * opName) noexcept : AndQueryNode(opName), _distance(0) { }
    bool evaluate() const override;
    void distance(size_t dist)       { _distance = dist; }
    size_t distance()          const { return _distance; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
    // Currently only for testing
    void set_element_gap(std::optional<uint32_t> element_gap) noexcept { _element_gap = element_gap; }
};

}
