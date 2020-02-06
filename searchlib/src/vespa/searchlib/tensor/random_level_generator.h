// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::tensor {

/**
 * Interface used to randomly draw the max level a new hnsw node should exist in.
 */
class RandomLevelGenerator {
public:
    virtual ~RandomLevelGenerator() {}
    virtual uint32_t max_level() = 0;
};

}
