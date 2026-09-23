// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_spec.h"

#include <vespa/vespalib/locale/c.h>

#include <charconv>
#include <cmath>
#include <cstdlib>

namespace search {

StringRangeSpec::StringRangeSpec() = default;

StringRangeSpec::StringRangeSpec(std::optional<std::string> left_in, bool left_closed_in,
                                 std::optional<std::string> right_in, bool right_closed_in)
    : StringRangeSpec(std::move(left_in), left_closed_in, std::move(right_in), right_closed_in, 0) {
}

StringRangeSpec::StringRangeSpec(std::optional<std::string> left_in, bool left_closed_in,
                                 std::optional<std::string> right_in, bool right_closed_in, int32_t range_limit_in)
    : left(std::move(left_in)),
      left_closed(left_closed_in),
      right(std::move(right_in)),
      right_closed(right_closed_in),
      range_limit(range_limit_in) {
}

StringRangeSpec::StringRangeSpec(const StringRangeSpec&) = default;
StringRangeSpec::~StringRangeSpec() = default;

} // namespace search
