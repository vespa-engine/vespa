// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <cmath>

namespace search::features {

/**
 * This class is used to calculate a logarithmic-shaped function that goes from 1 to 0.
 * The function is:
 * logscale(x, m, s) = (x > m ? 0 : (( log(m + s) - log(x + s)) / (log(m + s) - log(s)))),
 * where m specifies for which x the function should output 0 (max parameter),
 * and s controls the shape of the function (scale parameter).
 *
 * If you decide a value for x for when the function should output 0.5,
 * s can be calculated as -x^2/(2x - m).
 **/
class LogarithmCalculator {
private:
    feature_t _m;
    feature_t _s;
    feature_t _maxLog;
    feature_t _minLog;
    feature_t _divMult;

public:
    /**
     * Creates a calculator for the given values for m (max) and s (scale).
     **/
    LogarithmCalculator(feature_t m, feature_t s) :
        _m(m),
        _s(s),
        _maxLog(std::log(_m + _s)),
        _minLog(std::log(_s)),
        _divMult(1.0 / (_maxLog - _minLog))
    {
    }

    /**
     * Calculate the function for the given x.
     **/
    feature_t get(feature_t x) const {
        if (x > _m) x = _m;
        if (x < 0) x = 0;
        return (_maxLog - std::log(x + _s)) * _divMult;
    }

    /**
     * Calculate the scale parameter to use if the function should output 0.5
     * for the given x and max parameter.
     */
    static feature_t getScale(feature_t x, feature_t m) {
        return (x * x) / (m - 2*x);
    }
};

}
