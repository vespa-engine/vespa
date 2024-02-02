// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hit_iterator_pack.h"

namespace search::streaming {


HitIteratorPack::HitIteratorPack(const QueryNodeList& children)
    : _iterators(),
      _field_element(std::make_pair(0, 0))
{
    auto num_children = children.size();
    _iterators.reserve(num_children);
    for (auto& child : children) {
        auto& curr = dynamic_cast<const QueryTerm&>(*child);
        _iterators.emplace_back(curr.getHitList());
    }
}

HitIteratorPack::HitIteratorPack(const std::vector<std::unique_ptr<QueryTerm>>& children)
    : _iterators(),
      _field_element(std::make_pair(0, 0))
{
    auto num_children = children.size();
    _iterators.reserve(num_children);
    for (auto& child : children) {
        _iterators.emplace_back(child->getHitList());
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
