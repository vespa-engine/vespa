// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace search::bmcluster {

/*
 * Class used to calculate average value of samples.
 */
class AvgSampler {
private:
    double _total;
    size_t _samples;

public:
    AvgSampler() : _total(0), _samples(0) {}
    void sample(double val) {
        _total += val;
        ++_samples;
    }
    double avg() const { return _total / (double)_samples; }
};

}
