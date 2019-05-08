// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>

namespace search {
class Rand48;

namespace fakedata { class FakeWordSet; }

}

namespace postinglistbm {

class AndStress {
public:
    AndStress();

    ~AndStress();

    void run(search::Rand48 &rnd,
             search::fakedata::FakeWordSet &wordSet,
             const std::vector<std::string> &postingTypes,
             unsigned int loops,
             unsigned int skipCommonPairsRate,
             uint32_t numTasks,
             uint32_t stride,
             bool unpack);
};

}
