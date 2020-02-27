// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "random_level_generator.h"
#include <random>

namespace search::tensor {

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
