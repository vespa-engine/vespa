// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_factory.h"

namespace search::tensor {

/**
 * Factory that instantiates the production hnsw index.
 */
class DefaultNearestNeighborIndexFactory : public NearestNeighborIndexFactory {
public:
    std::unique_ptr<NearestNeighborIndex> make(const DocVectorAccess& vectors,
                                               size_t vector_size,
                                               vespalib::eval::CellType cell_type,
                                               const search::attribute::HnswIndexParams& params) const override;
};

}
