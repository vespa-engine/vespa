// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_spec.h"

#include <vespa/vespalib/locale/c.h>

#include <charconv>
#include <cmath>
#include <cstdlib>

namespace search {

StringRangeSpec::StringRangeSpec() = default;

StringRangeSpec::StringRangeSpec(std::string left_in, bool left_closed_in, bool left_unbounded_in,
                                 std::string right_in, bool right_closed_in, bool right_unbounded_in)
    : StringRangeSpec(std::move(left_in), left_closed_in, left_unbounded_in, std::move(right_in), right_closed_in,
                      right_unbounded_in, 0) {
}

StringRangeSpec::StringRangeSpec(std::string left_in, bool left_closed_in, bool left_unbounded_in,
                                 std::string right_in, bool right_closed_in, bool right_unbounded_in,
                                 int32_t range_limit_in)
    : left(std::move(left_in)),
      left_closed(left_closed_in),
      left_unbounded(left_unbounded_in),
      right(std::move(right_in)),
      right_closed(right_closed_in),
      right_unbounded(right_unbounded_in),
      range_limit(range_limit_in) {
}

StringRangeSpec::StringRangeSpec(const StringRangeSpec&) = default;
StringRangeSpec::~StringRangeSpec() = default;

} // namespace search
