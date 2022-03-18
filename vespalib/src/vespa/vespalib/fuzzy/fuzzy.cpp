// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Levenstein distance algorithm is based off Java implementation from apache commons-text library licensed under the Apache 2.0 license.

#include "fuzzy.h"

#include <limits>

#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>

vespalib::Fuzzy vespalib::Fuzzy::from_term(std::string_view term) {
    return Fuzzy(folded_codepoints(term));
}

std::vector<uint32_t> vespalib::Fuzzy::folded_codepoints(const char* src, size_t srcSize) {
    std::vector<uint32_t> result;
    result.reserve(srcSize);
    Utf8ReaderForZTS srcReader(src);
    while (srcReader.hasMore()) {
        result.emplace_back(LowerCase::convert(srcReader.getChar()));
    }
    return result;
}

std::vector<uint32_t> vespalib::Fuzzy::folded_codepoints(std::string_view src) {
    return folded_codepoints(src.data(), src.size());
}

std::optional<uint32_t> vespalib::Fuzzy::levenstein_distance(std::string_view source, std::string_view target, uint32_t threshold) {
    std::vector<uint32_t> sourceCodepoints = folded_codepoints(source.data(), source.size());
    std::vector<uint32_t> targetCodepoints = folded_codepoints(target.data(), target.size());
    return levenstein_distance(sourceCodepoints, targetCodepoints, threshold);
}

std::optional<uint32_t> vespalib::Fuzzy::levenstein_distance(const std::vector<uint32_t>& left, const std::vector<uint32_t>& right, uint32_t threshold) {
    uint32_t n = left.size();
    uint32_t m = right.size();

    if (n > m) {
        return levenstein_distance(right, left, threshold);
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
        // printf("j = %d, min = %d, max = %d, n = %d\n", j, min, max, n);
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

bool vespalib::Fuzzy::isMatch(std::string_view src) const {
    std::vector<uint32_t> srcCodepoints = folded_codepoints(src);
    return levenstein_distance(_folded_term_codepoints, srcCodepoints, _edit_distance).has_value();
}

vespalib::string vespalib::Fuzzy::getPrefix() const {
    vespalib::string prefix;
    Utf8Writer writer(prefix);
    for (uint8_t i=0; i <_prefix_size && i <_folded_term_codepoints.size(); ++i) {
        writer.putChar(_folded_term_codepoints.at(i));
    }
    return prefix;
}
