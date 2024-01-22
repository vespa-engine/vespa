// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <vector>

namespace search::streaming {

class Hit
{
    uint32_t _field_id;
    uint32_t _element_id;
    int32_t  _element_weight;
    uint32_t _position;
public:
    Hit(uint32_t field_id_, uint32_t element_id_, int32_t element_weight_, uint32_t position_) noexcept
        : _field_id(field_id_),
          _element_id(element_id_),
          _element_weight(element_weight_),
          _position(position_)
    { }
    uint32_t field_id() const noexcept { return _field_id; }
    uint32_t element_id() const { return _element_id; }
    int32_t element_weight() const { return _element_weight; }
    uint32_t position() const { return _position; }
};

using HitList = std::vector<Hit>;

}
