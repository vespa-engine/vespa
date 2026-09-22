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
    // The actual range/interval
    std::string left;
    bool        left_closed = true;
    bool        left_unbounded = false;
    std::string right;
    bool        right_closed = true;
    bool        right_unbounded = false;

    // Range limit specified via hitLimit annotation
    // Wired in from search protocol, but currently not used in matching
    int32_t range_limit = 0;

    StringRangeSpec();
    StringRangeSpec(std::string left_in, bool left_closed_in, bool left_unbounded_in, std::string right_in,
                    bool right_closed_in, bool right_unbounded_in);
    StringRangeSpec(std::string left_in, bool left_closed_in, bool left_unbounded_in, std::string right_in,
                    bool right_closed_in, bool right_unbounded_in, int32_t range_limit_in);
    StringRangeSpec(const StringRangeSpec&);
    ~StringRangeSpec();

    bool has_range_limit() const noexcept { return range_limit != 0; }

    constexpr auto operator<=>(const StringRangeSpec& rhs) const noexcept = default;
};

} // namespace search
