// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace search::bmcluster {

/*
 * Class used to calculate average feed rate.
 */
class AvgSampler {
private:
    uint64_t _ops;
    double   _elapsed;

public:
    AvgSampler() : _ops(0), _elapsed(0.0) {}
    void sample(uint64_t ops, double elapsed) {
        _ops += ops;
        _elapsed += elapsed;
    }
    double avg() const { return valid() ? (_ops / _elapsed) : 0.0; }
    bool valid() const { return _elapsed != 0.0; }
};

}
