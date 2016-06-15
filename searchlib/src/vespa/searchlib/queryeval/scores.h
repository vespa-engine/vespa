// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>

namespace search {
namespace queryeval {

struct Scores {
    feature_t low;
    feature_t high;
    Scores() : low(1), high(0) {}
    Scores(feature_t l, feature_t h) : low(l), high(h) {}

    bool isValid() const { return low <= high; }
};

}  // namespace queryeval
}  // namespace search

