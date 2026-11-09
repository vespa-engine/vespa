// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_rescorer.h"

namespace search::queryeval {

namespace {
double sanitize_double(double value) {
    return std::isnan(value) ? -std::numeric_limits<double>::infinity() : value;
}

Scores sanitize_bounds(const Scores& bounds) {
    Scores sanitized = {sanitize_double(bounds.low), sanitize_double(bounds.high)};

    // The high value should not be strictly less than the low value
    // Handle this gracefully but taking the low value as the high value
    if (sanitized.high < sanitized.low) [[unlikely]] {
        sanitized.high = sanitized.low;
    }
    return sanitized;
}
} // namespace

FirstPhaseRescorer::FirstPhaseRescorer(const std::pair<Scores, Scores>& ranges)
    : _scale(1.0), _first_low(0.0), _second_low(0.0) {
    if (need_rescore(ranges)) {
        // Sanitize inputs
        auto first_phase_scores = sanitize_bounds(ranges.first);
        auto second_phase_scores = sanitize_bounds(ranges.second);

        if (std::isfinite(first_phase_scores.high) && std::isfinite(first_phase_scores.low) &&
            std::isfinite(second_phase_scores.high) && std::isfinite(second_phase_scores.low)) [[likely]]
        {
            // Scale and adjust the first phase score according to the
            // first phase and second phase heap score values to avoid that
            // a score from the first phase is larger than second_phase_scores.low
            double first_phase_range = first_phase_scores.high - first_phase_scores.low;
            if (first_phase_range < 1.0) {
                first_phase_range = 1.0;
            }
            double second_phase_range = second_phase_scores.high - second_phase_scores.low;
            if (second_phase_range < 1.0) {
                second_phase_range = 1.0;
            }
            _scale = second_phase_range / first_phase_range;
            _first_low = first_phase_scores.low;
            _second_low = second_phase_scores.low;
            return;
        }

        if (std::isfinite(first_phase_scores.low) && std::isfinite(second_phase_scores.low)) {
            // Shift everything to behind second_phase_scores.low
            _scale = 1.0;
            _first_low = first_phase_scores.low;
            _second_low = second_phase_scores.low;
            return;
        }

        // One of first_phase_scores.low and second_phase_scores.low is infinite
        // No reasonable handling of finite values, just map everything to -Infinity
        _scale = 0.0;
        _first_low = 0.0;
        _second_low = -std::numeric_limits<double>::infinity();
    }
}

bool FirstPhaseRescorer::need_rescore(const std::pair<Scores, Scores>& ranges) {
    auto& first_phase_scores = ranges.first;
    auto& second_phase_scores = ranges.second;
    return (first_phase_scores.low > second_phase_scores.low);
}

} // namespace search::queryeval
