// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "distance_function_factory.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates the square of the standard Euclidean distance.
 * Will use instruction optimal for the cpu it is running on
 * after converting both vectors to an optimal cell type.
 */
template <typename FloatType>
class EuclideanDistanceFunctionFactory : public DistanceFunctionFactory {
public:
    EuclideanDistanceFunctionFactory() = default;
    BoundDistanceFunction::UP for_query_vector(const vespalib::eval::TypedCells& lhs) override;
    BoundDistanceFunction::UP for_insertion_vector(const vespalib::eval::TypedCells& lhs) override;
};

}
