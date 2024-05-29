// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_rescorer.h"

namespace search::queryeval {

FirstPhaseRescorer::FirstPhaseRescorer(const std::pair<Scores,Scores>& ranges)
    : _scale(1.0),
      _adjust(0.0)
{
    if (need_rescore(ranges)) {
        auto& first_phase_scores = ranges.first;
        auto& second_phase_scores = ranges.second;
        // scale and adjust the first phase score according to the
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
        _adjust = first_phase_scores.low * _scale - second_phase_scores.low;
    }
}

bool
FirstPhaseRescorer::need_rescore(const std::pair<Scores,Scores>& ranges)
{
    auto& first_phase_scores = ranges.first;
    auto& second_phase_scores = ranges.second;
    return (first_phase_scores.low > second_phase_scores.low);
}

}
