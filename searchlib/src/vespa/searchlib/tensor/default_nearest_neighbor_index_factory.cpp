// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "default_nearest_neighbor_index_factory.h"
#include "hnsw_index.h"
#include "random_level_generator.h"
#include "inv_log_level_generator.h"
#include "distance_function_factory.h"
#include <vespa/searchcommon/attribute/config.h>

namespace search::tensor {

using vespalib::eval::ValueType;

namespace {

class LevelZeroGenerator : public RandomLevelGenerator {
    uint32_t max_level() override { return 0; }
};

RandomLevelGenerator::UP
make_random_level_generator(uint32_t m)
{
    return std::make_unique<InvLogLevelGenerator>(m);
}

} // namespace <unnamed>

std::unique_ptr<NearestNeighborIndex>
DefaultNearestNeighborIndexFactory::make(const DocVectorAccess& vectors,
                                         size_t vector_size,
                                         vespalib::eval::CellType cell_type,
                                         const search::attribute::HnswIndexParams& params) const
{
    (void) vector_size;
    uint32_t m = params.max_links_per_node();
    HnswIndexConfig cfg(m * 2,
                        m,
                        params.neighbors_to_explore_at_insert(),
                        10000,
                        true);
    return std::make_unique<HnswIndex>(vectors,
                                       make_distance_function(params.distance_metric(), cell_type),
                                       make_random_level_generator(m),
                                       cfg);
}

}

