// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function_factory.h"

namespace search::tensor {

/**
 * Calculates angular distance between vectors
 * Will use instruction optimal for the cpu it is running on
 * after converting both vectors to an optimal cell type.
 */
template <typename FloatType>
class AngularDistanceFunctionFactory : public DistanceFunctionFactory {
public:
    AngularDistanceFunctionFactory() = default;
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) override;
};

}
