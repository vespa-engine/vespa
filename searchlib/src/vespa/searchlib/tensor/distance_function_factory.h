// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "bound_distance_function.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/distance_metric.h>

namespace search::tensor {

/**
 * API for binding the LHS of a distance calculation
 * This allows keeping global state in the factory itself, and state
 * for one particular vector in the distance function object.
 */
struct DistanceFunctionFactory {
    const vespalib::eval::CellType expected_cell_type;
    DistanceFunctionFactory(vespalib::eval::CellType ct) : expected_cell_type(ct) {}
    virtual ~DistanceFunctionFactory() {}
    virtual BoundDistanceFunction::UP for_query_vector(const vespalib::eval::TypedCells& lhs) = 0;
    virtual BoundDistanceFunction::UP for_insertion_vector(const vespalib::eval::TypedCells& lhs) = 0;
    using UP = std::unique_ptr<DistanceFunctionFactory>;
};

/**
 * Create a distance function object customized for the given metric
 * variant and cell type.
 **/
DistanceFunction::UP
make_distance_function(search::attribute::DistanceMetric variant,
                       vespalib::eval::CellType cell_type);

DistanceFunctionFactory::UP
make_distance_function_factory(search::attribute::DistanceMetric variant,
                               vespalib::eval::CellType cell_type);

}
