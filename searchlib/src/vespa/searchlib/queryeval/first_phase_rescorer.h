// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "scores.h"
#include <cstdint>

namespace search::queryeval {

/*
 * Rescore hits not selected for second phase to prevent them from getting
 * a better score than hits selected for second phase ranking.
 */
class FirstPhaseRescorer {
    double _scale;
    double _adjust;
public:
    FirstPhaseRescorer(const std::pair<Scores,Scores>& ranges);
    static bool need_rescore(const std::pair<Scores,Scores>& ranges);
    double rescore(uint32_t, double score) const noexcept {
        return ((score * _scale) - _adjust);
    }
};

}
