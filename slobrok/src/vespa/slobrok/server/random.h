// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdlib>

namespace {

const double randmax = RAND_MAX;

// standard uniform distribution
double uniformRandom() {
    return random() / randmax;
}

double randomIn(double min, double max) {
    return min + (uniformRandom() * (max - min));
}

} // namespace <unnamed>
