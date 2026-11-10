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
constexpr double min = std::numeric_limits<double>::min();

using ScorePair = std::pair<Scores, Scores>;

std::vector<double>    second_phase_scores = {inf,   max,   1'000'000.0,  50.0, 40.0,  30.0,  20.0,
                                              10.0,  1.0,   0.0,          -1.0, -10.0, -20.0, -30.0,
                                              -40.0, -50.0, -1'000'000.0, min,  -inf};
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

std::vector<ScorePair> get_bounds_with_inf_and_nan() {
    std::vector<double> inf_and_nan = {nan, inf, max, 0.0, -inf, min, -nan};

    std::vector<ScorePair> result;
    for (const auto& pair : bounds) {
        for (double first_low : inf_and_nan) {
            for (double first_high : inf_and_nan) {
                for (double second_low : inf_and_nan) {
                    for (double second_high : inf_and_nan) {
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
    // Verifies that, for values that are finite or negative infinity, the following holds:
    //    If x <= first_low, then rescore(x) <= second_low.
    for (auto& pair : get_bounds_with_inf_and_nan()) {
        if (pair.first.high >= pair.first.low && pair.second.high >= pair.second.low) {
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
    // Verifies that finite bounds whose derived values (ranges, scale, adjust)
    // overflow to infinity neither produce NaN nor violate the invariant.
    std::vector<ScorePair> overflow_bounds = {
        // second_high - second_low overflows, giving an infinite scale
        {{1.0, 2.0}, {-1e308, 1e308}},
        // first_low * scale overflows, giving an infinite adjust
        {{1e300, 1e300}, {0.0, 1e10}},
        // first_high - first_low overflows, giving a zero scale
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
            EXPECT_FALSE(std::isnan(new_score)) << "score=" << score;
            if (score <= pair.first.low) {
                EXPECT_LE(new_score, pair.second.low) << "score=" << score;
            }
        }
        EXPECT_FALSE(std::isnan(rescorer.rescore(0, pair.first.low)));
        EXPECT_LE(rescorer.rescore(0, pair.first.low), pair.second.low);
    }
}

TEST(FirstPhaseRescorerTest, verify_new_score_is_not_nan) {
    for (auto& pair : get_bounds_with_inf_and_nan()) {
        FirstPhaseRescorer rescorer(pair);
        for (double score : second_phase_scores) {
            EXPECT_FALSE(std::isnan(rescorer.rescore(0, score)));
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
