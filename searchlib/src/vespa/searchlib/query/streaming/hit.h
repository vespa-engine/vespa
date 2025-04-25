// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <iosfwd>
#include <vector>

namespace search::streaming {

/*
 * The key portion of a hit in streaming search.
 */
class HitKey {
    uint32_t _field_id;
    uint32_t _element_id;
    uint32_t _position;
public:
    HitKey(uint32_t field_id_, uint32_t element_id_, uint32_t position_) noexcept
        : _field_id(field_id_),
          _element_id(element_id_),
          _position(position_)
    { }
    uint32_t field_id() const noexcept { return _field_id; }
    uint32_t element_id() const noexcept { return _element_id; }
    uint32_t position() const noexcept { return _position; }
    auto operator<=>(const HitKey& rhs) const noexcept = default;
};

/*
 * A hit in streaming search.
 */
class Hit {
    HitKey   _key;
    int32_t  _element_weight;
    uint32_t _element_length;
public:
    Hit(uint32_t field_id_, uint32_t element_id_, int32_t element_weight_, uint32_t position_) noexcept
        : _key(field_id_, element_id_, position_),
          _element_weight(element_weight_),
          _element_length(0)
    { }
    const HitKey& key() const noexcept { return _key; }
    uint32_t field_id() const noexcept { return _key.field_id(); }
    uint32_t element_id() const noexcept { return _key.element_id(); }
    int32_t element_weight() const noexcept { return _element_weight; }
    uint32_t element_length() const noexcept { return _element_length; }
    uint32_t position() const noexcept { return _key.position(); }
    void set_element_length(uint32_t value) { _element_length = value; }
    bool operator<(const Hit& rhs) const noexcept {
        auto cmp = _key <=> rhs.key();
        if (cmp != 0) {
            return cmp < 0;
        }
        if (_element_weight != rhs._element_weight) {
            // NOTE: Higher element weight is sorted earlier.
            return _element_weight > rhs._element_weight;
        }
        return _element_length < rhs._element_length;
    }
    bool at_same_pos(const Hit& rhs) const noexcept { return _key == rhs.key(); }
    bool operator==(const Hit& rhs) const noexcept = default;
};

std::ostream& operator<<(std::ostream& os, const Hit& hit);

using HitList = std::vector<Hit>;

}
