// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <algorithm>

namespace search::queryeval {

struct Scores {
    feature_t low;
    feature_t high;
    Scores() : low(1), high(0) {}
    Scores(feature_t l, feature_t h) : low(l), high(h) {}

    bool isValid() const { return low <= high; }

    void update(feature_t score) {
        if (isValid()) {
            low = std::min(low, score);
            high = std::max(high, score);
        } else {
            low = score;
            high = score;
        }
    }
};

}
