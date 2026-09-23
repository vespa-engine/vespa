// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <optional>
#include <string>

namespace search {

/**
 * Basic representation of a string range specification
 */

struct StringRangeSpec {
    // The actual range/interval
    std::optional<std::string> left; // Not present means unbounded to the left
    bool                       left_closed = true;
    std::optional<std::string> right; // Not present means unbounded to the right
    bool                       right_closed = true;

    // Range limit specified via hitLimit annotation
    // Wired in from search protocol, but currently not used in matching
    int32_t range_limit = 0;

    StringRangeSpec();
    StringRangeSpec(std::optional<std::string> left_in, bool left_closed_in, std::optional<std::string> right_in,
                    bool right_closed_in);
    StringRangeSpec(std::optional<std::string> left_in, bool left_closed_in, std::optional<std::string> right_in,
                    bool right_closed_in, int32_t range_limit_in);
    StringRangeSpec(const StringRangeSpec&);
    ~StringRangeSpec();

    bool has_range_limit() const noexcept { return range_limit != 0; }

    constexpr auto operator<=>(const StringRangeSpec& rhs) const noexcept = default;
};

} // namespace search
