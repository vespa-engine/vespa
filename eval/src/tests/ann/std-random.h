// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <random>

class RndGen {
private:
    std::mt19937_64 urng;
    std::normal_distribution<double> normRng;
    std::uniform_real_distribution<double> uf;
public:
    RndGen() : urng(0x1234deadbeef5678uLL), normRng(), uf(0.0, 1.0) {}

    double nextNormal() {
        return normRng(urng);
    }

    double nextUniform() {
        return uf(urng);
    }
};
