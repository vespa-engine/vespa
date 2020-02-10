// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function.h"
#include "hnsw_index.h"

namespace search::tensor {

template <typename FloatType>
HnswIndex<FloatType>::HnswIndex(const DocVectorAccess& vectors, const DistanceFunction& distance_func,
                                RandomLevelGenerator& level_generator, const Config& cfg)
    : HnswIndexBase(vectors, distance_func, level_generator, cfg)
{
}

template <typename FloatType>
HnswIndex<FloatType>::~HnswIndex() = default;

}

