// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/first_phase_rescorer.h>
#include <vespa/searchlib/queryeval/scores.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <cmath>

using search::queryeval::FirstPhaseRescorer;
using search::queryeval::Scores;

namespace {
constexpr double inf = std::numeric_limits<double>::infinity();
constexpr double nan = std::numeric_limits<double>::quiet_NaN();
constexpr double max = std::numeric_limits<double>::max();
constexpr double lowest = std::numeric_limits<double>::lowest();

using ScorePair = std::pair<Scores, Scores>;

// Scores to test rescoring with
std::vector<double> second_phase_scores = {inf,   max,   1'000'000.0,  50.0,   40.0,  30.0,  20.0,
                                           10.0,  1.0,   0.0,          -1.0,   -10.0, -20.0, -30.0,
                                           -40.0, -50.0, -1'000'000.0, lowest, -inf};
// Bounds to test the FirstPhaseRescorer with
std::vector<ScorePair> bounds = {
    // "Normal" range and range under zero
    {{10.0, 20.0}, {30.0, 40.0}},
    {{10.0, 20.0}, {-20.0, -10.0}},
    {{-20.0, -10.0}, {30.0, 40.0}},
    {{-20.0, -10.0}, {-20.0, -10.0}},
    // "Normal" range and "small" range
    {{10.0, 20.0}, {30.0, 40.0}},
    {{10.0, 20.0}, {1.5, 2.0}},
    {{0.5, 1.0}, {30.0, 40.0}},
    {{0.5, 1.0}, {1.5, 2.0}},
    // Ranges crossing 0.0
    {{-10.0, 10.0}, {-20.0, 20.0}}};

// Takes the bounds from above and produces all variations obtained by substituting
// values by special values like +/-Infinity and +/-NaN
std::vector<ScorePair> get_bounds_with_special_values() {
    std::vector<double> special = {nan, inf, max, 0.0, -inf, lowest, -nan};

    std::vector<ScorePair> result;
    for (const auto& pair : bounds) {
        for (double first_low : special) {
            for (double first_high : special) {
                for (double second_low : special) {
                    for (double second_high : special) {
                        ScorePair new_pair = pair;
                        if (first_low != 0.0) {
                            new_pair.first.low = first_low;
                        }
                        if (first_high != 0.0) {
                            new_pair.first.high = first_high;
                        }
                        if (second_low != 0.0) {
                            new_pair.second.low = second_low;
                        }
                        if (second_high != 0.0) {
                            new_pair.second.high = second_high;
                        }
                        result.emplace_back(new_pair);
                    }
                }
            }
        }
    }

    return result;
}

double nan_to_neg_inf(double value) {
    return std::isnan(value) ? -inf : value;
}

} // namespace

TEST(FirstPhaseRescorerTest, verify_invariant) {
    // Verifies that the FirstPhaseRescorer satisfies the following invariant:
    //    If x <= first_low, then rescore(x) <= second_low.
    for (auto& pair : get_bounds_with_special_values()) {
        FirstPhaseRescorer rescorer(pair);
        for (double score : second_phase_scores) {
            double first_low = nan_to_neg_inf(pair.first.low);
            double second_low = nan_to_neg_inf(pair.second.low);

            if (score <= first_low) {
                EXPECT_LE(rescorer.rescore(0, score), second_low);
            }
        }
    }
}

TEST(FirstPhaseRescorerTest, verify_rescorer_does_not_produce_nan) {
    // Whatever the FirstPhaseRescorer yields, it must not be NaN
    for (auto& pair : get_bounds_with_special_values()) {
        FirstPhaseRescorer rescorer(pair);
        for (double score : second_phase_scores) {
            EXPECT_FALSE(std::isnan(rescorer.rescore(0, score)));
        }
    }
}

TEST(FirstPhaseRescorerTest, verify_invariant_is_robust_against_rounding) {
    // Verifies that rescore(first_low) <= second_low also holds when the
    // computation is subject to floating-point rounding errors.
    std::vector<ScorePair> rounding_bounds = {
        // second_low is absorbed when computed together with a much larger first_low
        {{1e16, 1e16}, {-1.0, 0.0}},
        // Rounding error with "ordinary" values
        {{44.82237721780038, 84.7846254520088}, {-63.711917948293866, -42.99091007416019}},
    };
    for (auto& pair : rounding_bounds) {
        ASSERT_TRUE(FirstPhaseRescorer::need_rescore(pair));
        FirstPhaseRescorer rescorer(pair);
        EXPECT_LE(rescorer.rescore(0, pair.first.low), pair.second.low);
    }
}

TEST(FirstPhaseRescorerTest, verify_finite_bounds_with_overflow) {
    // Verifies that finite bounds where the bound ranges or the quotient of the ranges
    // overflow neither produce NaN nor violate the "rescore(first_low) <= second_low" invariant.
    std::vector<ScorePair> overflow_bounds = {
        // second_high - second_low overflows, giving an infinite quotient
        {{1.0, 2.0}, {-1e308, 1e308}},
        // first_low * quotient overflows, could be a problem in rescore()
        {{1e300, 1e300}, {0.0, 1e10}},
        // first_high - first_low overflows, giving a zero quotient
        {{-1e308, 1e308}, {-1.5e308, 1.0}},
        // Both first_high - first_low and second_high - second_low overflow, giving Inf / Inf
        {{-1e308, 1e308}, {-1.5e308, 1e308}},
        {{-1e308, std::numeric_limits<double>::max()},
         {-std::numeric_limits<double>::max(), std::numeric_limits<double>::max()}},
    };
    for (auto& pair : overflow_bounds) {
        ASSERT_TRUE(FirstPhaseRescorer::need_rescore(pair));
        FirstPhaseRescorer rescorer(pair);

        for (double score : second_phase_scores) {
            double new_score = rescorer.rescore(0, score);
            EXPECT_FALSE(std::isnan(new_score));

            // Check invariant
            if (score <= pair.first.low) {
                EXPECT_LE(new_score, pair.second.low);
            }
        }
        EXPECT_FALSE(std::isnan(rescorer.rescore(0, pair.first.low)));
        EXPECT_LE(rescorer.rescore(0, pair.first.low), pair.second.low);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
