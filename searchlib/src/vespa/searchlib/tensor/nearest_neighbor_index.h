// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vector>
#include <vespa/eval/tensor/dense/typed_cells.h>

namespace search::tensor {

/**
 * Interface for an index that is used for (approximate) nearest neighbor search.
 */
class NearestNeighborIndex {
public:
    virtual ~NearestNeighborIndex() {}
    virtual void add_document(uint32_t docid) = 0;
    virtual void remove_document(uint32_t docid) = 0;
    virtual std::vector<uint32_t> find_top_k(vespalib::tensor::TypedCells vector, uint32_t k) = 0;
};

}
