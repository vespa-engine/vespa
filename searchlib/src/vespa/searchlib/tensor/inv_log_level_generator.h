// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "random_level_generator.h"
#include <random>

namespace search::tensor {

/**
 * Geometric distribution for level selection in HnswIndex.
 * Pr(level=k) is (1/M)^k * (1 - 1/M)
 * Note that the level is theoretically unbounded, but in
 * practice less than 30.
 * Generated using floor(ln(U)/ln(1-p)), see
 * https://en.wikipedia.org/wiki/Geometric_distribution#Related_distributions
 **/

class InvLogLevelGenerator : public RandomLevelGenerator {
    std::mt19937_64 _rng;
    std::uniform_real_distribution<double> _uniform;
    double _levelMultiplier;
public:
    InvLogLevelGenerator(uint32_t m)
      : _rng(0x1234deadbeef5678uLL),
        _uniform(0.0, 1.0),
        _levelMultiplier(1.0 / log(1.0 * m))
    {}

    uint32_t max_level() override {
        double unif = _uniform(_rng);
        double r = -log(1.0-unif) * _levelMultiplier;
        return (uint32_t) r;
    }
};

}
