// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "doc_vector_access.h"
#include "hnsw_index_base.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::tensor {

class DistanceFunction;
class DocVectorAccess;
class RandomLevelGenerator;

/**
 * Concrete implementation of a hierarchical navigable small world graph (HNSW)
 * that is used for approximate K-nearest neighbor search.
 *
 * See HnswIndexBase for more details.
 *
 * The FloatType template argument specifies the data type used in the vectors (4 byte float or 8 byte double).
 */
template <typename FloatType = float>
class HnswIndex : public HnswIndexBase {
public:
    HnswIndex(const DocVectorAccess& vectors, const DistanceFunction& distance_func,
              RandomLevelGenerator& level_generator, const Config& cfg);
    ~HnswIndex() override;
};

template class HnswIndex<float>;
template class HnswIndex<double>;

}

