// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <iosfwd>
#include <vector>

namespace search::streaming {

class Hit
{
    uint32_t _field_id;
    uint32_t _element_id;
    int32_t  _element_weight;
    uint32_t _element_length;
    uint32_t _position;
public:
    Hit(uint32_t field_id_, uint32_t element_id_, int32_t element_weight_, uint32_t position_) noexcept
        : _field_id(field_id_),
          _element_id(element_id_),
          _element_weight(element_weight_),
          _element_length(0),
          _position(position_)
    { }
    uint32_t field_id() const noexcept { return _field_id; }
    uint32_t element_id() const { return _element_id; }
    int32_t element_weight() const { return _element_weight; }
    uint32_t element_length() const { return _element_length; }
    uint32_t position() const { return _position; }
    void set_element_length(uint32_t value) { _element_length = value; }
    bool operator<(const Hit& rhs) const noexcept {
        if (_field_id != rhs._field_id) {
            return _field_id < rhs._field_id;
        }
        if (_element_id != rhs._element_id) {
            return _element_id < rhs._element_id;
        }
        if (_position != rhs._position) {
                return _position < rhs._position;
        }
        if (_element_weight != rhs._element_weight) {
            return _element_weight > rhs._element_weight;
        }
        return _element_length < rhs._element_length;
    }
    bool at_same_pos(const Hit& rhs) const noexcept {
        return (_field_id == rhs._field_id) &&
            (_element_id == rhs._element_id) &&
            (_position == rhs._position);
    }
    bool operator==(const Hit& rhs) const noexcept {
        return (_field_id == rhs._field_id) &&
            (_element_id == rhs._element_id) &&
            (_position == rhs._position) &&
            (_element_weight == rhs._element_weight) &&
            (_element_length == rhs._element_length);
    }
};

std::ostream& operator<<(std::ostream& os, const Hit& hit);

using HitList = std::vector<Hit>;

}
