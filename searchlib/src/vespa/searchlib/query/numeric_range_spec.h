// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>
#include <limits>
#include <memory>

namespace search {

/**
 * Basic representation of a numeric range specificatino
 */

struct NumericRangeSpec {
    bool             valid = false;
    bool             valid_integers = false;
    bool             lower_inclusive = true;
    bool             upper_inclusive = true;
    bool             diversityCutoffStrict = false;
    // what we really want:
    double           fp_lower_limit = -std::numeric_limits<double>::infinity();
    double           fp_upper_limit = std::numeric_limits<double>::infinity();
    int64_t          int64_lower_limit = std::numeric_limits<int64_t>::min();
    int64_t          int64_upper_limit = std::numeric_limits<int64_t>::max();
    int32_t          rangeLimit = 0;
    uint32_t         maxPerGroup = 0;
    uint32_t         diversityCutoffGroups = std::numeric_limits<uint32_t>::max();
    std::string      diversityAttribute = "";

    bool has_lower_limit() const noexcept { return fp_lower_limit > -std::numeric_limits<double>::infinity(); }
    bool has_upper_limit() const noexcept { return fp_upper_limit < std::numeric_limits<double>::infinity(); }
    bool has_range_limit() const noexcept { return rangeLimit != 0; }

    bool with_diversity() const noexcept { return ! diversityAttribute.empty(); }
    bool with_diversity_cutoff() const noexcept {
        return diversityCutoffGroups != std::numeric_limits<uint32_t>::max();
    }

    constexpr auto operator <=>(const NumericRangeSpec &rhs) const noexcept = default;

    static std::unique_ptr<NumericRangeSpec> fromString(std::string_view stringRep);
};

} // namespace
