// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "default_nearest_neighbor_index_factory.h"
#include "distance_functions.h"
#include "hnsw_index.h"
#include "random_level_generator.h"
#include <vespa/searchcommon/attribute/config.h>

namespace search::tensor {

using vespalib::eval::ValueType;

namespace {

class LevelZeroGenerator : public RandomLevelGenerator {
    uint32_t max_level() override { return 0; }
};

DistanceFunction::UP
make_distance_function(ValueType::CellType cell_type)
{
    if (cell_type == ValueType::CellType::FLOAT) {
        return std::make_unique<SquaredEuclideanDistance<float>>();
    } else {
        return std::make_unique<SquaredEuclideanDistance<double>>();
    }
}

RandomLevelGenerator::UP
make_random_level_generator()
{
    // TODO: Make generator that results in hierarchical graph.
    return std::make_unique<LevelZeroGenerator>();
}

}

std::unique_ptr<NearestNeighborIndex>
DefaultNearestNeighborIndexFactory::make(const DocVectorAccess& vectors,
                                         vespalib::eval::ValueType::CellType cell_type,
                                         const search::attribute::HnswIndexParams& params) const
{
    HnswIndex::Config cfg(params.max_links_per_node() * 2,
                          params.max_links_per_node(),
                          params.neighbors_to_explore_at_insert(),
                          true);
    return std::make_unique<HnswIndex>(vectors, make_distance_function(cell_type), make_random_level_generator(), cfg);
}

}

