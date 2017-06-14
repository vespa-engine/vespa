// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once


#include "poss_count.h"

struct ChildInfo {
    uint32_t maxNodes;
    uint32_t activeNodes;
    uint32_t maxParts;
    uint32_t activeParts;
    PossCount activeDocs;

    ChildInfo()
        : maxNodes(0),
          activeNodes(0),
          maxParts(0),
          activeParts(0),
          activeDocs()
    {}
};
