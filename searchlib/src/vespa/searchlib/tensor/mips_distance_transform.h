// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "distance_function_factory.h"

#include <vespa/eval/eval/typed_cells.h>

#include <memory>
#include <mutex>

namespace search::tensor {

/**
 * Thread-safe storage of maximum value for squared vector norm.
 * sq_norm = |x|^2 = sum(x[i]*x[i]) = dotproduct(x,x)
 * Note that the initial value is 1.0; so even if all
 * vectors seen have 0 or very small length, you will never
 * get a value < 1.0.
 */
class MaximumSquaredNormStore {
private:
    std::mutex _lock;
    double     _max_sq_norm;

public:
    MaximumSquaredNormStore() noexcept : _lock(), _max_sq_norm(1.0) {}
    /**
     * Fetch the maximum value seen so far.
     * Usually you will also supply a value computed for a newly seen
     * vector, which may update the maximum value.
     */
    double get_max(double value = 0.0) {
        std::lock_guard<std::mutex> guard(_lock);
        if (value > _max_sq_norm) [[unlikely]] {
            _max_sq_norm = value;
        }
        return _max_sq_norm;
    }
};

class MipsDistanceFunctionFactoryBase : public DistanceFunctionFactory {
protected:
    std::shared_ptr<MaximumSquaredNormStore> _sq_norm_store;

public:
    MipsDistanceFunctionFactoryBase() : _sq_norm_store(std::make_shared<MaximumSquaredNormStore>()) {}
    ~MipsDistanceFunctionFactoryBase() override = default;
    MaximumSquaredNormStore& get_max_squared_norm_store() noexcept { return *_sq_norm_store; }
};

/**
 * Factory for distance functions which can apply a transformation
 * mapping Maximum Inner Product Search to a nearest neighbor
 * problem.  When inserting vectors, an extra dimension is
 * added ensuring behavior "as if" all vectors had length equal
 * to the longest vector inserted so far, or at least length 1.
 *
 * When reference_insertion_vector == true:
 *   - Vectors passed to for_insertion_vector() and BoundDistanceFunction::calc() are assumed to have the same type as
 * FloatType.
 *   - The TypedCells memory is just referenced and used directly in calculations,
 *     and thus no transformation via a temporary memory buffer occurs.
 */
template <typename FloatType> class MipsDistanceFunctionFactory : public MipsDistanceFunctionFactoryBase {
private:
    bool _reference_insertion_vector;

public:
    MipsDistanceFunctionFactory() noexcept : MipsDistanceFunctionFactory(false) {}
    MipsDistanceFunctionFactory(bool reference_insertion_vector) noexcept
        : _reference_insertion_vector(reference_insertion_vector) {}
    ~MipsDistanceFunctionFactory() override = default;

    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

/**
 * Factory for distance functions which can apply a transformation mapping Maximum
 * Inner Product Search to a nearest neighbor problem.  When inserting vectors, an
 * extra dimension is added ensuring behavior "as if" all vectors had length equal
 * to the longest vector inserted so far, or at least length 1.
 *
 * The left hand side may be either a float32 (full precision) vector or a quantized
 * vector in int8 format, and the right hand side is always a quantized vector in
 * int8 format.
 *
 * Query vectors are always converted to float32 form. That means a _query_ vector
 * of int8 values will be elementwise promoted to float and will _not_ be treated
 * as the quantized representation of a query vector.
 *
 * Insertion vectors are expected to be in pre-quantized int8 format.
 */
class QuantizedMipsDistanceFunctionFactory : public MipsDistanceFunctionFactoryBase {
    const size_t   _dimensions;
    const uint64_t _seed;
    const uint8_t  _bits;

public:
    QuantizedMipsDistanceFunctionFactory(size_t dimensions, uint8_t bits, uint64_t seed) noexcept
        : _dimensions(dimensions), _seed(seed), _bits(bits) {}
    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
};

} // namespace search::tensor
