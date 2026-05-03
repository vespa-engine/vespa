// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "match_span.h"

#include <span>
#include <vector>

namespace search::common {
class ElementIds;
}

namespace search::queryeval {

/*
 * Class used to calculate intersection between match spans and element ids.
 */
class FilteredMatchSpans {
    std::vector<MatchSpan> _filtered_spans;
    template <typename MatchSpanFilter>
    [[nodiscard]] std::span<const MatchSpan> intersection_helper(std::span<const MatchSpan> spans,
                                                                 MatchSpanFilter            filter);

public:
    FilteredMatchSpans() noexcept;
    ~FilteredMatchSpans();
    [[nodiscard]] std::span<const MatchSpan> intersection(std::span<const MatchSpan> spans,
                                                          search::common::ElementIds element_ids);
};

} // namespace search::queryeval
