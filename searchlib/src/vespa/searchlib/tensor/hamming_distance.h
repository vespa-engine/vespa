// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function_factory.h"

namespace search::tensor {

/**
 * Calculates the Hamming distance defined as
 * "number of cells where the values are different"
 * or (for int8 cells, aka binary data only)
 * "number of bits that are different"
 */
template <typename FloatType>
class HammingDistanceFunctionFactory : public DistanceFunctionFactory {
public:
    HammingDistanceFunctionFactory() = default;
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

}
