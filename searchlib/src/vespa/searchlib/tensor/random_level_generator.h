// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::tensor {

/**
 * Interface used to randomly draw the max level a new hnsw node should exist in.
 */
class RandomLevelGenerator {
public:
    using UP = std::unique_ptr<RandomLevelGenerator>;
    virtual ~RandomLevelGenerator() {}
    virtual uint32_t max_level() = 0;
};

}
