// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hit.h"

namespace search::streaming {

/*
 * Iterator used over hit list for a term to support near, onear, phrase and
 * same element query nodes.
 */
class HitIterator
{
    HitList::const_iterator _cur;
    HitList::const_iterator _end;
public:
    using FieldElement = std::pair<uint32_t, uint32_t>;
    HitIterator(const HitList& hl) noexcept
        : _cur(hl.begin()),
          _end(hl.end())
    { }
    bool valid() const noexcept { return _cur != _end; }
    const Hit* operator->() const noexcept { return _cur.operator->(); }
    const Hit& operator*() const noexcept { return _cur.operator*(); }
    FieldElement get_field_element() const noexcept { return std::make_pair(_cur->field_id(), _cur->element_id()); }
    bool seek_to_field_element(const FieldElement& field_element) noexcept {
        while (valid()) {
            if (!(get_field_element() < field_element)) {
                return true;
            }
            ++_cur;
        }
        return false;
    }
    bool step_in_field_element(FieldElement& field_element) noexcept {
        ++_cur;
        if (!valid()) {
            return false;
        }
        auto ife = get_field_element();
        if (field_element < ife) {
            field_element = ife;
            return false;
        }
        return true;
    }
    bool seek_in_field_element(uint32_t word_pos, FieldElement& field_element) {
        while (_cur->position() < word_pos) {
            if (!step_in_field_element(field_element)) {
                return false;
            }
        }
        return true;
    }
    HitIterator& operator++() { ++_cur; return *this; }
};

}
