// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <limits>
#include <memory>
#include <string>

namespace search {

/**
 * Basic representation of a string range specification
 */

struct StringRangeSpec {
    std::string left;
    bool        left_closed = true;
    bool        left_unbounded = false;
    std::string right;
    bool        right_closed = true;
    bool        right_unbounded = false;
    int32_t     range_limit = 0;

    ~StringRangeSpec();

    bool has_range_limit() const noexcept { return range_limit != 0; }

    constexpr auto operator<=>(const StringRangeSpec& rhs) const noexcept = default;
};

} // namespace search
