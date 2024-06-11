// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function_factory.h"

namespace search::tensor {

/**
 * Calculates inner-product "distance" between vectors assuming a common norm.
 * Should give same ordering as Angular distance, but is less expensive.
 *
 * When reference_insertion_vector == true:
 *   - Vectors passed to for_insertion_vector() and BoundDistanceFunction::calc() are assumed to have the same type as FloatType.
 *   - The TypedCells memory is just referenced and used directly in calculations,
 *     and thus no transformation via a temporary memory buffer occurs.
 */
template <typename FloatType>
class PrenormalizedAngularDistanceFunctionFactory : public DistanceFunctionFactory {
private:
    bool _reference_insertion_vector;
public:
    PrenormalizedAngularDistanceFunctionFactory() noexcept : PrenormalizedAngularDistanceFunctionFactory(false) {}
    PrenormalizedAngularDistanceFunctionFactory(bool reference_insertion_vector) noexcept : _reference_insertion_vector(reference_insertion_vector) {}
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

}
