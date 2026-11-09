// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "scores.h"

#include <cmath>
#include <cstdint>
#include <limits>

namespace search::queryeval {

/*
 * Rescore hits not selected for second phase to prevent them from getting
 * a better score than hits selected for second phase ranking.
 *
 * ===== Idea =====
 * The hits selected for second phase are those with the highest first-phase scores:
 *  first_1  >=  first_2  >=  first_3  >=  first_4  >=  first_5  >=  first_6
 * |-----------hits selected-----------|----------hits not selected----------|
 *  first_high                first_low
 *
 * This is turned into:
 *  second_1  >= second_2 >=  second_3 >=  second_4 >=  second_5 >=  second_6
 * |------by second phase ranking------|--------by FirstPhaseRescorer--------|
 *  second_high               second_low
 *
 * ===== Linear Interpolation =====
 * This is achieved by a linear "interpolation" that computes scores for the hits not selected for second phase:
 *      x |-> (x - first_low) * scale + second_low
 *            where scale := (second_high - second_low) / (first_high - first_low)
 * This way, first_high is mapped to second_high, first_low to second_low,
 * and everything below first_low is mapped to something below second_low.
 *
 * ===== Handling of special cases =====
 * How the rescore method handles special values:
 * - +/-NaN is treated as -Inf
 * - +Inf is always mapped to +Inf
 * - -Inf is always mapped to -Inf
 *
 * How special values in the bounds are handled:
 * - If all four bounds are finite, then apply the linear transformation from above.
 * - If first_low and second_low are finite, but one of the other bounds is not, set scale := 1.0.
 * - Otherwise, just map all finite values to -Inf.
 *
 * Note that values coming out of first-phase ranking are either finite or negative infinity.
 * For such values, the following invariant holds:
 *     If x <= first_low, then rescore(x) <= second_low.
 */
class FirstPhaseRescorer {
    double _scale;
    double _first_low;
    double _second_low;

public:
    FirstPhaseRescorer(const std::pair<Scores, Scores>& ranges);
    static bool need_rescore(const std::pair<Scores, Scores>& ranges);
    double rescore(uint32_t, double score) const noexcept {
        constexpr static double inf = std::numeric_limits<double>::infinity();

        if (score == inf) [[unlikely]] {
            return inf;
        }
        if (std::isnan(score) || score == -inf) [[unlikely]] {
            return -inf;
        }

        double rescored = (score - _first_low) * _scale + _second_low;
        return std::isfinite(rescored) ? rescored : -inf;
    }
};

} // namespace search::queryeval
