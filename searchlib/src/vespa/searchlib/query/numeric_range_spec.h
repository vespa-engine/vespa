// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>
#include <limits>

namespace search {

/**
 * Basic representation of a numeric range specificatino
 */

struct NumericRangeSpec {
    /* what we really want:
       double           fpLowerLimit = 0;
       double           fpUpperLimit = 0;
       int64_t          integerLowerLimit = 0;
       int64_t          integerUpperLimit = 0;
    */
    std::string_view lowerLimitTxt = "";
    std::string_view upperLimitTxt = "";
    bool             lower_inclusive = false;
    bool             upper_inclusive = false;
    bool             diversityCutoffStrict = false;
    std::string_view diversityAttribute = "";
    int32_t          rangeLimit = 0;
    uint32_t         maxPerGroup = 0;
    uint32_t         diversityCutoffGroups = std::numeric_limits<uint32_t>::max();

    bool has_lower_limit() const noexcept { return ! lowerLimitTxt.empty(); }
    bool has_upper_limit() const noexcept { return ! upperLimitTxt.empty(); }
    bool has_range_limit() const noexcept { return rangeLimit != 0; }
    bool with_diversity() const noexcept { return ! diversityAttribute.empty(); }
    bool with_diversity_cutoff() const noexcept {
        return diversityCutoffGroups != std::numeric_limits<uint32_t>::max();
    }

    void initFrom(const std::string &term);
};

} // namespace
