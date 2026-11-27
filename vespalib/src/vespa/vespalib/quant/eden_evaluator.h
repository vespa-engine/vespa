// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <span>

namespace vespalib::quant {

struct EdenConstants {
    constexpr static size_t scale_bytes = sizeof(float);
};

/*
 * Thread-safe EDEN distance function evaluator with minimal construction overhead and
 * no need for temporary buffer state.
 *
 * This can be used when you only need to compute distances between pre-rotated or
 * pre-quantized vectors. Note that an instance of EdenQuantizer is needed to initially
 * rotate a floating point query vector into the correct frame of reference.
 *
 * See EdenQuantizer for algorithmic details.
 */
class EdenEvaluator {
protected:
    const size_t  _dimensions;
    const size_t  _quantized_size;
    const uint8_t _bits;

public:
    EdenEvaluator(size_t dimensions, uint8_t bits) noexcept;
    /*
     * Computes the dot product between a quantized vector and a _pre-rotated_ query
     * vector. This is much more efficient than doing dequantize() followed by an
     * explicit dot product, as we don't have to perform any expensive rotations.
     *
     * `query` must have had a spin in the `EdenQuantizer::rotate_vector_inplace` function
     * prior to use, i.e. it must be rotated into the same frame of reference as `quant_vec`.
     *
     * Only makes sense if `quant_vec` was originally quantized with `QuantMode::InnerProduct`.
     *
     * Preconditions:
     *  - query.size() == dimensions()
     *  - quant_vec.size() == quantized_size()
     *
     */
    [[nodiscard]] float pre_rotated_query_dot_product(std::span<const float>   query,
                                                      std::span<const uint8_t> quant_vec) const noexcept;

    /*
     * Computes the dot product between two quantized vectors that were both quantized
     * using the (logically) same quantizer as `this`. This is more efficient than
     * explicitly dequantizing the vectors (and then running a float32 dot product) since
     * it does not involve any rotations.
     *
     * Only makes sense if `lhs` and `rhs` were originally both quantized with
     * `QuantMode::InnerProduct`.
     *
     * Preconditions:
     *  - lhs.size() == quantized_size()
     *  - rhs.size() == quantized_size()
     */
    [[nodiscard]] float quantized_lhs_rhs_dot_product(std::span<const uint8_t> lhs,
                                                      std::span<const uint8_t> rhs) const noexcept;

    /*
     * Computes the squared Euclidean distance between a quantized vector and a
     * _pre-rotated_ query vector. This is much more efficient than doing dequantize()
     * followed by an explicit float32 squared Euclidean distance computation, as we
     * don't have to perform any expensive rotations.
     *
     * `query` must have had a spin in the `rotate_vector_inplace` function prior to use,
     * i.e. it must be rotated into the same frame of reference as `quant_vec`.
     *
     * Preconditions:
     *  - query.size() == dimensions()
     *  - quant_vec.size() == quantized_size()
     */
    [[nodiscard]] float
    pre_rotated_query_squared_euclidean_distance(std::span<const float>   query,
                                                 std::span<const uint8_t> quant_vec) const noexcept;

    /*
     * Computes the squared Euclidean distance between two quantized vectors that were both
     * quantized using the (logically) same quantizer as `this`. This is more efficient than
     * explicitly dequantizing the vectors (and then running a float32 squared Euclidean
     * distance) since it does not involve any rotations.
     *
     * Preconditions:
     *  - lhs.size() == quantized_size()
     *  - rhs.size() == quantized_size()
     */
    [[nodiscard]] float quantized_lhs_rhs_squared_euclidean_distance(std::span<const uint8_t> lhs,
                                                                     std::span<const uint8_t> rhs) const noexcept;

protected:
    // Precondition: buf must be valid for at least `scale_bytes` bytes
    [[nodiscard]] static float extract_scale_factor(const uint8_t* buf) noexcept {
        static_assert(EdenConstants::scale_bytes == sizeof(float));
        float scale;
        memcpy(&scale, buf, sizeof(float));
        return scale;
    }

    // Given an input ptr `buf` that is at the start of the "raw" quantized buffer, returns
    // an adjusted ptr that points to the first byte of the sequence of quantized bits.
    // Precondition: buf must be valid for at least `scale_bytes` bytes
    [[nodiscard]] static const uint8_t* packed_bits_buf(const uint8_t* buf) noexcept {
        return buf + EdenConstants::scale_bytes; // Just skip past the scale factor
    }

    [[nodiscard]] static uint8_t* packed_bits_buf(uint8_t* buf) noexcept { return buf + EdenConstants::scale_bytes; }
};

} // namespace vespalib::quant
