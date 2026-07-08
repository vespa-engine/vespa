// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function_factory.h"

namespace search::tensor {

/**
 * Calculates inner-product "distance" between vectors assuming a common norm.
 * Should give same ordering as Angular distance, but is less expensive.
 *
 * When reference_insertion_vector == true:
 *   - Vectors passed to for_insertion_vector() and BoundDistanceFunction::calc() are assumed to have the same type as
 * FloatType.
 *   - The TypedCells memory is just referenced and used directly in calculations,
 *     and thus no transformation via a temporary memory buffer occurs.
 */
template <typename FloatType> class PrenormalizedAngularDistanceFunctionFactory : public DistanceFunctionFactory {
private:
    bool _reference_insertion_vector;

public:
    PrenormalizedAngularDistanceFunctionFactory() noexcept : PrenormalizedAngularDistanceFunctionFactory(false) {}
    PrenormalizedAngularDistanceFunctionFactory(bool reference_insertion_vector) noexcept
        : _reference_insertion_vector(reference_insertion_vector) {}
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

/**
 * Calculates inner-product "distance" between vectors assuming a common norm,
 * where the left hand side may be either a float32 (full precision) vector or
 * a quantized vector in int8 format, and the right hand side is always a quantized
 * vector in int8 format.
 *
 * Query vectors are always converted to float32 form. That means a _query_ vector
 * of int8 values will be elementwise promoted to float and will _not_ be treated
 * as the quantized representation of a query vector.
 *
 * Insertion vectors are expected to be in pre-quantized int8 format.
 */
class QuantizedPrenormalizedAngularDistanceFunctionFactory : public DistanceFunctionFactory {
    const size_t   _dimensions;
    const uint64_t _seed;
    const uint8_t  _bits;

public:
    QuantizedPrenormalizedAngularDistanceFunctionFactory(size_t dimensions, uint8_t bits, uint64_t seed) noexcept
        : _dimensions(dimensions), _seed(seed), _bits(bits) {}
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    // Only supports int8 insertion vectors
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

} // namespace search::tensor
