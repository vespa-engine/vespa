// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hit_iterator.h"
#include "queryterm.h"
#include <span>

namespace search::streaming {

/*
 * Iterator pack used over hit list for a term to support near, onear,
 * phrase and same element query nodes.
 */
class HitIteratorPack
{
    using iterator = typename std::vector<HitIterator>::iterator;
    using FieldElement = HitIterator::FieldElement;
    std::vector<HitIterator> _iterators;
    std::vector<HitList> _hit_lists;
    FieldElement _field_element;
public:
    explicit HitIteratorPack(std::span<const std::unique_ptr<QueryNode>> children);
    explicit HitIteratorPack(std::span<const std::unique_ptr<QueryTerm>> children);
    HitIteratorPack(const HitIteratorPack&) = delete;
    ~HitIteratorPack();
    HitIteratorPack& operator=(const HitIteratorPack&) = delete;
    FieldElement& get_field_element_ref() noexcept { return _field_element; }
    HitIterator& front() noexcept { return _iterators.front(); }
    iterator begin() noexcept { return _iterators.begin(); }
    iterator end() noexcept { return _iterators.end(); }
    bool all_valid() const noexcept;
    bool seek_to_matching_field_element() noexcept;
};

}
