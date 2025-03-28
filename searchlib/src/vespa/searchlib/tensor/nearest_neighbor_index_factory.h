// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <memory>

namespace search::attribute { class HnswIndexParams; }

namespace search::tensor {

class DocVectorAccess;
class NearestNeighborIndex;

/**
 * Factory interface used to instantiate an index used for (approximate) nearest neighbor search.
 */
class NearestNeighborIndexFactory {
public:
    virtual ~NearestNeighborIndexFactory() = default;
    virtual std::unique_ptr<NearestNeighborIndex> make(const DocVectorAccess& vectors,
                                                       size_t vector_size,
                                                       bool multi_vector_index,
                                                       vespalib::eval::CellType cell_type,
                                                       const search::attribute::HnswIndexParams& params) const = 0;
};

}
