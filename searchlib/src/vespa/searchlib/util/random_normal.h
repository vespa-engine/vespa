// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/rand48.h>
#include <cmath>

namespace search {

class RandomNormal
{
private:
    Rand48    _rnd;
    bool      _hasSpare;
    feature_t _spare;

    feature_t nextUniform() {
        return (_rnd.lrand48() / (feature_t)0x80000000u) * 2.0 - 1.0;
    }

public:
    RandomNormal() : _rnd(), _hasSpare(false), _spare(0.0) {}

    void seed(long seed) {
        _rnd.srand48(seed);
    }

    /**
     * Draws a random number from the Gaussian distribution
     * using the Marsaglia polar method.
     */
    feature_t next(bool useSpare = true) {
        feature_t result = _spare;
        if (_hasSpare && useSpare) {
            _hasSpare = false;
        } else {
            _hasSpare = true;

            feature_t u, v, s;
            do {
                u = nextUniform();
                v = nextUniform();
                s = u * u + v * v;
            } while ( (s >= 1.0) || (s == 0.0) );
            s = std::sqrt(-2.0 * std::log(s) / s);

            _spare = v * s; // saved for next invocation
            result = u * s;
        }
        return result;
    }

};

} // search

