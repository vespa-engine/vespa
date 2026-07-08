// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "temporary_vector_store.h"

#include <vespa/eval/eval/int8float.h>
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/quant/eden.h>

#include <type_traits>

namespace search::tensor {

// Wrappers around temporary vector stores that further abstract away how distance functions
// deal with type conversions and low-level distance computations. This is an indirection
// created in the honor of vector quantization, as efficient distance computations on
// quantized vectors require operating (in a quantization-aware manner) on the bitwise
// representation without needing to go via an explicit (and expensive) dequantization step.
// It also allows for heterogeneous types for the left and right hand sides of the
// comparison, which means a full precision query can be compared with a reduced precision
// internal representation. This is currently only implemented for quantized vectors,
// and in that case double-precision queries will be narrowed to full (float) precision.

// Simple wrapper around the provided VectorStoreType with homogenous lhs/rhs types
// (via implicit conversion of the input lhs vector).
template <typename VectorStoreType>
class DefaultDistanceFunctionVectorAccess {
    using FloatType = VectorStoreType::FloatType;

    VectorStoreType                  _tmp_space;
    const std::span<const FloatType> _lhs;

public:
    explicit DefaultDistanceFunctionVectorAccess(vespalib::eval::TypedCells lhs)
        : _tmp_space(lhs.size), _lhs(_tmp_space.storeLhs(lhs)) {}

    [[nodiscard]] std::span<const FloatType> lhs() const noexcept { return _lhs; }
    [[nodiscard]] std::span<const FloatType> convert_rhs(vespalib::eval::TypedCells rhs) noexcept {
        return _tmp_space.convertRhs(rhs);
    }
    template <typename T>
    [[nodiscard]] double dot_product(const T* a, const T* b) const noexcept {
        return vespalib::hwaccelerated::dot_product(a, b, _lhs.size());
    }
    template <typename T>
    [[nodiscard]] double squared_euclidean_distance(const T* a, const T* b) const noexcept {
        return vespalib::hwaccelerated::squared_euclidean_distance(a, b, _lhs.size());
    }
};

template <typename FloatType>
using TemporaryVectorAccess = DefaultDistanceFunctionVectorAccess<TemporaryVectorStore<FloatType>>;
template <typename FloatType>
using ReferenceVectorAccess = DefaultDistanceFunctionVectorAccess<ReferenceVectorStore<FloatType>>;

// Wrapper used when operating on a quantized tensor where the right hand (tensor) side is
// always quantized, but the left hand side may be either quantized or a full precision
// float vector.
template <typename QuantAccessParams>
class QuantizedDistanceFunctionVectorAccess {
    using LhsVectorStoreType = typename QuantAccessParams::VectorStoreType;
    using LhsFloatType = typename QuantAccessParams::LhsFloatType;

    // A distance function instance shall only be used by one thread at a time,
    // so this can be mutable without violating constness assumptions.
    // FIXME this one is relatively expensive to create and keep around...!
    //  Make as much as possible into free-standing functions and only create quantizer
    //  when pre-rotating.
    mutable vespalib::quant::EdenQuantizer _quantizer;
    LhsVectorStoreType                     _tmp_space;
    const std::span<const LhsFloatType>    _lhs;

public:
    QuantizedDistanceFunctionVectorAccess(vespalib::eval::TypedCells lhs, const size_t dimensions, const uint8_t bits,
                                          const uint64_t seed)
        : _quantizer(dimensions, bits, seed), _tmp_space(lhs.size), _lhs(_tmp_space.storeLhs(lhs)) {
        if constexpr (!QuantAccessParams::pre_quantized_lhs) { // full precision format
            assert(lhs.size == _quantizer.dimensions());
            // Quantized vectors are all in rotated space, so pre-rotate the query vector once
            // so that it is in the same frame of reference. This avoids having to rotate the
            // _quantized_ vectors when performing distance calcs. Note: this aliases `_lhs`.
            _quantizer.rotate_vector_inplace(_tmp_space.mutable_lhs_buf());
        } else {
            static_assert(std::is_same_v<LhsFloatType, vespalib::eval::Int8Float>);
            assert(lhs.type == vespalib::eval::CellType::INT8);
            assert(lhs.size == _quantizer.quantized_size());
        }
    }
    ~QuantizedDistanceFunctionVectorAccess();

    [[nodiscard]] size_t dimensions() const noexcept { return _quantizer.dimensions(); }
    [[nodiscard]] size_t quantized_size() const noexcept { return _quantizer.quantized_size(); }

    // Lhs is _either_ a f32 (raw query vector) or an i8 (quantized insertion vector)
    [[nodiscard]] std::span<const LhsFloatType> lhs() const noexcept { return _lhs; }
    // Rhs is always a quantized i8 vector
    [[nodiscard]] std::span<const vespalib::eval::Int8Float>
    convert_rhs(vespalib::eval::TypedCells rhs) const noexcept {
        assert(rhs.size == quantized_size());
        return rhs.typify<vespalib::eval::Int8Float>();
    }

    // These overloads are used for selecting the appropriate quantizer function
    // based on whether the lhs is quantized (i8) or not (float). The float vs float
    // overload is a convenience function to handle squared L2 norm computation of
    // a float query vector.
    // The functions make assumptions on vector lengths that should transitively hold
    // from the assertions present on the lhs/rhs conversions.
    // Note: all functions that operate on quantized representations will implicitly
    // mutate the underlying _quantizer instance.
    [[nodiscard]] float dot_product(const int8_t* lhs, const int8_t* rhs) const noexcept {
        return _quantizer.quantized_lhs_rhs_dot_product(as_quantized_u8_vec(lhs), as_quantized_u8_vec(rhs));
    }
    [[nodiscard]] float dot_product(const float* lhs, const int8_t* rhs) const noexcept {
        return _quantizer.pre_rotated_query_dot_product(as_unquantized_f32_vec(lhs), as_quantized_u8_vec(rhs));
    }
    [[nodiscard]] float dot_product(const float* lhs, const float* rhs) const noexcept {
        return vespalib::hwaccelerated::dot_product(lhs, rhs, dimensions());
    }
    [[nodiscard]] float squared_euclidean_distance(const int8_t* lhs, const int8_t* rhs) const noexcept {
        return _quantizer.quantized_lhs_rhs_squared_euclidean_distance(as_quantized_u8_vec(lhs),
                                                                       as_quantized_u8_vec(rhs));
    }
    [[nodiscard]] float squared_euclidean_distance(const float* lhs, const int8_t* rhs) const noexcept {
        return _quantizer.pre_rotated_query_squared_euclidean_distance(as_unquantized_f32_vec(lhs),
                                                                       as_quantized_u8_vec(rhs));
    }

private:
    [[nodiscard]] std::span<const float> as_unquantized_f32_vec(const float* buf) const noexcept {
        return std::span<const float>(buf, dimensions());
    }
    [[nodiscard]] std::span<const uint8_t> as_quantized_u8_vec(const int8_t* buf) const noexcept {
        return std::span<const uint8_t>(reinterpret_cast<const uint8_t*>(buf), quantized_size());
    }
};

template <typename QuantAccessParams>
QuantizedDistanceFunctionVectorAccess<QuantAccessParams>::~QuantizedDistanceFunctionVectorAccess() = default;

struct Float32LhsQuantizedRhs {
    using VectorStoreType = MutableSingleTemporaryVectorStore<float>;
    using LhsFloatType = float;
    constexpr static bool pre_quantized_lhs = false;
};
// To be used when the left hand (query) side of the distance calculation is a full precision
// float vector. We need mutable storage for this since the query vector must be rotated into
// the same frame of reference as the one used by the quantized vectors. Note that we use a
// custom temporary vector store that only allocates room for a single float vector since we
// know that the right hand side is always quantized.
using Float32LhsQuantizedRhsVectorAccess = QuantizedDistanceFunctionVectorAccess<Float32LhsQuantizedRhs>;

struct QuantizedLhsAndRhs {
    using VectorStoreType = ReferenceVectorStore<vespalib::eval::Int8Float>;
    using LhsFloatType = vespalib::eval::Int8Float;
    constexpr static bool pre_quantized_lhs = true;
};
// To be used when both the left and right hand sides of the distance calculation are quantized.
// In this case we can directly reference the left hand side vector, as no rotation or conversion
// is needed.
using QuantizedLhsAndRhsVectorAccess = QuantizedDistanceFunctionVectorAccess<QuantizedLhsAndRhs>;

} // namespace search::tensor
