// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function_factory.h"

namespace search::tensor {

/**
 * Calculates inner-product "distance" between vectors assuming a common norm.
 * Should give same ordering as Angular distance, but is less expensive.
 */
template <typename FloatType>
class PrenormalizedAngularDistanceFunctionFactory : public DistanceFunctionFactory {
public:
    PrenormalizedAngularDistanceFunctionFactory() = default;
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

}
