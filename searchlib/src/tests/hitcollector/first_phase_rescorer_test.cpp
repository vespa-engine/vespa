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

                if ((std::isfinite(score) || -score == std::numeric_limits<double>::infinity()) && score <= first_low)
                {
                    EXPECT_LE(rescorer.rescore(0, score), second_low);
                }
            }
        }
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
