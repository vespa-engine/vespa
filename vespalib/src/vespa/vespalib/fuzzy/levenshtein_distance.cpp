// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Levenshtein distance algorithm is based off Java implementation from apache commons-text library licensed under the Apache 2.0 license.

#include "levenshtein_distance.h"

#include <limits>
#include <vector>

std::optional<uint32_t>
vespalib::LevenshteinDistance::calculate(std::span<const uint32_t> left, std::span<const uint32_t> right, uint32_t threshold)
{
    uint32_t n = left.size();
    uint32_t m = right.size();

    if (n > m) {
        return calculate(right, left, threshold);
    }

    // if one string is empty, the edit distance is necessarily the length
    // of the other
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
    for (uint32_t j = 1; j <= m; ++j) {
        uint32_t rightJ = right[j - 1]; // jth character of right
        d[0] = j;

        int32_t min = std::max(1, static_cast<int32_t>(j) - static_cast<int32_t>(threshold));

        uint32_t max = j > std::numeric_limits<uint32_t>::max() - threshold ?
                       n : std::min(n, j + threshold);

        // ignore entry left of leftmost
        if (min > 1) {
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
            return std::nullopt;
        }
        std::swap(p, d);
    }
    if (p[n] <= threshold) {
        return {p[n]};
    }
    return std::nullopt;
}
