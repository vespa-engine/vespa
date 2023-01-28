// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace vespalib { class Rand48; }

namespace search::fakedata { class FakeWordSet; }

namespace postinglistbm {

class StressRunner {
public:
    enum class OperatorType {
        Direct,
        And,
        Or
    };

    static void run(vespalib::Rand48 &rnd,
                    search::fakedata::FakeWordSet &wordSet,
                    const std::vector<std::string> &postingTypes,
                    OperatorType operatorType,
                    uint32_t loops,
                    uint32_t skipCommonPairsRate,
                    uint32_t numTasks,
                    uint32_t stride,
                    bool unpack);
};

}
