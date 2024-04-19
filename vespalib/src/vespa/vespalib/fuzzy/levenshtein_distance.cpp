// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Levenshtein distance algorithm is based off Java implementation from apache commons-text library licensed under the Apache 2.0 license.

#include "levenshtein_distance.h"

#include <cassert>
#include <limits>
#include <vector>

namespace vespalib {

std::optional<uint32_t>
LevenshteinDistance::calculate(std::span<const uint32_t> left, std::span<const uint32_t> right,
                               uint32_t threshold, bool prefix_match)
{
    assert(left.size() <= static_cast<size_t>(INT32_MAX));
    assert(right.size() <= static_cast<size_t>(INT32_MAX));
    threshold = std::min(threshold, static_cast<uint32_t>(std::numeric_limits<int32_t>::max()));
    uint32_t n = left.size();
    uint32_t m = right.size();

    if (!prefix_match) {
        // These optimizations are only valid when matching with target/source string symmetry.
        // Correctness of the main matrix calculation loop should not depend on these.
        if (n > m) {
            return calculate(right, left, threshold, false);
        }
        // if one string is empty, the edit distance is necessarily the length
        // of the other.
        if (n == 0) {
            return m <= threshold ? std::optional(m) : std::nullopt;
        }
        if (m == 0) {
            return n <= threshold ? std::optional(n) : std::nullopt;
        }
        // the edit distance cannot be less than the length difference
        if (m - n > threshold) {
            return std::nullopt;
        }
    } else {
        // A source (right) cannot be transformed into a target prefix (left) if doing
        // so would require inserting more than max edits number of characters.
        if ((n > m) && (n - m > threshold)) {
            return std::nullopt;
        }
    }

    std::vector<uint32_t> p(n+1); // 'previous' cost array, horizontally
    std::vector<uint32_t> d(n+1); // cost array, horizontally

    const uint32_t boundary = std::min(n, threshold) + 1;

    for (uint32_t i = 0; i < boundary; ++i) {
        p[i] = i;
    }
    // these fills ensure that the value above the rightmost entry of our
    // stripe will be ignored in following loop iterations
    for (uint32_t i = boundary; i < p.size(); ++i) {
        p[i] = std::numeric_limits<uint32_t>::max();
    }
    for (uint32_t i = 0; i < d.size(); ++i) {
        d[i] = std::numeric_limits<uint32_t>::max();
    }

    // iterates through t
    uint32_t min_edits = n; // prefix matching: worst-case to transform to target
    for (uint32_t j = 1; j <= m; ++j) {
        uint32_t rightJ = right[j - 1]; // jth character of right
        d[0] = j;

        int32_t min = std::max(1, static_cast<int32_t>(j) - static_cast<int32_t>(threshold));

        uint32_t max = j > std::numeric_limits<uint32_t>::max() - threshold ?
                       n : std::min(n, j + threshold);
        // ignore entry left of leftmost
        if (min > 1) {
            assert(static_cast<size_t>(min) <= d.size());
            d[min - 1] = std::numeric_limits<uint32_t>::max();
        }

        uint32_t lowerBound = std::numeric_limits<uint32_t>::max();

        for (uint32_t i = min; i <= max; ++i) {
            if (left[i - 1] == rightJ) {
                // diagonally left and up
                d[i] = p[i - 1];
            } else {
                // 1 + minimum of cell to the left, to the top, diagonally
                // left and up
                d[i] = 1 + std::min(std::min(d[i - 1], p[i]), p[i - 1]);
            }
            lowerBound = std::min(lowerBound, d[i]);
        }
        if (lowerBound > threshold) {
            if (!prefix_match) {
                return std::nullopt; // Cannot match
            } else {
                break; // May already have matched via min_edits
            }
        }
        std::swap(p, d);
        // For prefix matching:
        // By definition, the Levenshtein matrix cell at row `i`, column `j`
        // provides the minimum number of edits required to transform a prefix of
        // source string S (up to and including length `i`) into a prefix of target
        // string T (up to and including length `j`). Since we want to match against
        // the entire target (prefix query) string with length `n`, the problem is
        // reduced to finding the minimum value of the `n`th column that is `<= k`
        // (aggregated here and checked after the loop).
        min_edits = std::min(p[n], min_edits);
    }
    if (prefix_match) {
        return ((min_edits <= threshold) ? std::optional<uint32_t>{min_edits} : std::nullopt);
    } else if (p[n] <= threshold) {
        return {p[n]};
    }
    return std::nullopt;
}

std::optional<uint32_t>
LevenshteinDistance::calculate(std::span<const uint32_t> left, std::span<const uint32_t> right, uint32_t threshold)
{
    return calculate(left, right, threshold, false);
}

} // vespalib
