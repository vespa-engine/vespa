// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "scores.h"

#include <cstdint>

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
 * Note:
 *   - In first-phase ranking, +/-NaN and +/-Infinity are turned into -Infinity
 *   - In second-phase ranking, no such thing happens
 *   - The container turns +/-NaN into -Infinity, but +Infinity (from second phase) stays +Infinity
 * Meaningful handling of some special cases:
 *   - second_low is -Infinity: FirstPhaseRescorer maps everything to -Infinity
 *   - second_low is +Infinity (and, hence, so is second_high): keep everything at current score
 *   - second_low is finite, but one of the other bounds is not: set scale := 1.0
 *       - finite values are moved below first_low
 *       - -Infinity stays -Infinity
 *   - all bounds are finite: this is the regular case, see above
 * If we encounter +/-NaN, we treat that as -Infinity.
 */
class FirstPhaseRescorer {
    double _scale;
    double _adjust;

public:
    FirstPhaseRescorer(const std::pair<Scores, Scores>& ranges);
    static bool need_rescore(const std::pair<Scores, Scores>& ranges);
    double rescore(uint32_t, double score) const noexcept { return ((score * _scale) - _adjust); }
};

} // namespace search::queryeval
