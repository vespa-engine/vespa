// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hit_iterator_pack.h"

namespace search::streaming {


HitIteratorPack::HitIteratorPack(std::span<const std::unique_ptr<QueryNode>> children)
    : _iterators(),
      _hit_lists(),
      _field_element(std::make_pair(0, 0))
{
    auto num_children = children.size();
    _iterators.reserve(num_children);
    _hit_lists.reserve(num_children);
    for (auto& child : children) {
        _iterators.emplace_back(child->evaluateHits(_hit_lists.emplace_back()));
    }
}

HitIteratorPack::HitIteratorPack(std::span<const std::unique_ptr<QueryTerm>> children)
    : _iterators(),
      _hit_lists(),
      _field_element(std::make_pair(0, 0))
{
    auto num_children = children.size();
    _iterators.reserve(num_children);
    _hit_lists.reserve(num_children);
    for (auto& child : children) {
        _iterators.emplace_back(child->evaluateHits(_hit_lists.emplace_back()));
    }
}

HitIteratorPack::~HitIteratorPack() = default;

bool
HitIteratorPack::all_valid() const noexcept
{
    if (_iterators.empty()) {
        return false;
    }
    for (auto& it : _iterators) {
        if (!it.valid()) {
            return false;
        }
    }
    return true;
}

bool
HitIteratorPack::seek_to_matching_field_element() noexcept
{
    bool retry = true;
    while (retry) {
        retry = false;
        for (auto& it : _iterators) {
            if (!it.seek_to_field_element(_field_element)) {
                return false;
            }
            auto ife = it.get_field_element();
            if (_field_element < ife) {
                _field_element = ife;
                retry = true;
                break;
            }
        }
    }
    return true;
}

}
