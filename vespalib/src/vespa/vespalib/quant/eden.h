// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rotator.h"

#include <cstddef>
#include <cstdint>
#include <span>
#include <utility>
#include <vector>

namespace vespalib::quant {

enum class QuantMode : uint8_t {
    // Quantization optimized for reconstruction accuracy, aiming to minimize the
    // mean of squared errors (MSE) between the original vector and its dequantized
    // representation.
    MSE,
    // Quantization optimized for inner product accuracy. MSE-oriented quantization
    // introduces a bias when used for inner products; `InnerProduct` is designed
    // to eliminate this bias.
    InnerProduct
};

/*
 * Vector (de-)quantizer using the EDEN ("Efficient Distributed Mean Estimation for
 * diverse Networks") algorithm, supporting 1-4 bits (inclusive) per coordinate.
 * See [0] and [1].
 *
 * Two distinct quantization "modes" are supported; one optimizing for minimizing
 * reconstruction mean squared error (MSE) and one optimizing for unbiased inner
 * product computations. See `QuantMode`.
 *
 * An EdenQuantizer instance is bound to a particular vector dimensionality for the
 * duration of its lifetime.
 *
 * Quantization inherently involves pseudo-randomness that is derived from the
 * seed value provided during construction. The same seed value shall result in the
 * ~same output (modulo floating point errors) for a given vector across distinct
 * instances, even across different process lifetimes. Consequently, it is vital
 * that the same seed value is provided when different vectors are to be used in the
 * same context. Note that this only applies to a given dimensionality; quantizers
 * using the same seed but different dimensionality values are not interoperable.
 *
 * Assuming `d` dimensions and `b` bits, the footprint of a quantized vector is
 * d*b bits (rounded up to nearest whole byte) + a single 4-byte f32 scalar value.
 * Use `quantized_size()` to get the exact value; both quantization and dequantization
 * expects exact input/output size matches.
 *
 * Thread safety: non-const/static methods are NOT thread safe since certain
 * internal operations require the use of object-level temporary data structures.
 *
 * References:
 *  [0] Shay Vargaftik, Ran Ben Basat, Amit Portnoy, Gal Mendelson, Yaniv Ben-Itzhak
 *      and Michael Mitzenmacher. EDEN: Communication-Efficient and Robust Distributed
 *      Mean Estimation for Federated Learning, 2022. https://arxiv.org/abs/2108.08842v3
 *
 *  [1] Ran Ben-Basat, Yaniv Ben-Itzhak, Gal Mendelson, Michael Mitzenmacher, Amit
 *      Portnoy and Shay Vargaftik. A Note on TurboQuant and the Earlier DRIVE/EDEN
 *      Line of Work, 2026. https://arxiv.org/abs/2604.18555
 */
class EdenQuantizer {
    const size_t         _dimensions;
    const size_t         _quantized_size;
    const Rotator        _rotator;
    std::vector<float>   _rot_tmp;
    std::vector<uint8_t> _idx_tmp;
    const uint64_t       _seed;
    const float          _sqrt_d; // Precomputed sqrt(dimensions)
    const uint8_t        _bits;

    // Returns the shared, fixed N(0, 1) codebook for the configured quantizer bit count
    [[nodiscard]] std::span<const float> my_codebook() const noexcept;

    struct ScaleAndCentroidsPtr {
        float          scale;
        const uint8_t* centroid_idx;
    };
    // Precondition: `bits.size() == quantized_size()`
    // Returns [scale factor, unpacked centroid index ptr]
    [[nodiscard]] ScaleAndCentroidsPtr unary_unpack_bits_to_scratch_space(std::span<const uint8_t> bits) noexcept;
    // Precondition: `lhs.size() == rhs.size() == quantized_size()`
    // Returns [[lhs scale, lhs unpacked index ptr], [rhs scale, rhs unpacked index ptr]]
    [[nodiscard]] std::pair<ScaleAndCentroidsPtr, ScaleAndCentroidsPtr>
    binary_unpack_bits_to_scratch_space(std::span<const uint8_t> lhs, std::span<const uint8_t> rhs) noexcept;

public:
    EdenQuantizer(size_t dimensions, uint8_t bits, uint64_t seed);
    ~EdenQuantizer();

    [[nodiscard]] size_t dimensions() const noexcept { return _dimensions; }
    [[nodiscard]] uint8_t bits() const noexcept { return _bits; }
    [[nodiscard]] uint64_t seed() const noexcept { return _seed; }

    // Number of bytes used by a quantized vector representation.
    [[nodiscard]] size_t quantized_size() const noexcept { return _quantized_size; }

    /*
     * Quantize the input vector `x` into the receiving buffer `q_x`.
     *
     * `quant_mode` should be chosen based on the context in which the quantized vectors
     * are expected to be used; MSE for "general" vector reconstruction, InnerProduct for
     * inner products (perhaps unsurprisingly).
     *
     * Preconditions:
     *  - x.size() == dimensions()
     *  - q_x.size() == quantized_size()
     */
    void quantize(std::span<const float> x, std::span<uint8_t> q_x, QuantMode quant_mode) noexcept;
    /*
     * Takes in the quantized vector buffer `q_x` that is the result of a prior call to
     * `quantize(x, q_x)` and dequantizes an approximation of `x` into `dq_x`.
     *
     * It is important that quantize() and dequantize() pairs always happen on quantizer
     * instances that have been created with the _exact same_ seed value! Otherwise, the
     * dequantized output will have effectively no correlation with the original vector.
     *
     * Preconditions:
     *  - q_x.size() == quantized_size()
     *  - dq_x.size() == dimensions()
     */
    void dequantize(std::span<const uint8_t> q_x, std::span<float> dq_x) noexcept;

    /*
     * In-place rotates `vec` so that it is in the same frame of reference as the vectors
     * quantized by an EdenQuantizer using the same construction seed and dimensionality
     * as this one.
     *
     * Query vectors rotated by this function can be used in `pre_rotated_query_dot_product()`.
     *
     * Note: calling this twice on the same vector does _not_ invert the original rotation.
     * I.e. this is a one-way rotation only. Calling it more than once will further rotate
     * the vector into some completely uncorrelated goblin space.
     *
     * Preconditions:
     *  - vec.size() == dimensions()
     */
    void rotate_vector_inplace(std::span<float> vec) const noexcept;

    /*
     * Computes the dot product between a quantized vector and a _pre-rotated_ query
     * vector. This is much more efficient than doing dequantize() followed by an
     * explicit dot product, as we don't have to perform any expensive rotations.
     *
     * `query` must have had a spin in the `rotate_vector_inplace` function prior to use,
     * i.e. it must be rotated into the same frame of reference as `quant_vec`.
     *
     * Only makes sense if `quant_vec` was originally quantized with `QuantMode::InnerProduct`.
     *
     * Preconditions:
     *  - query.size() == dimensions()
     *  - quant_vec.size() == quantized_size()
     *
     * TODO with a vectorized "just in time" bit unpacking that doesn't need a temporary
     *  buffer we could make this const...
     */
    [[nodiscard]] float pre_rotated_query_dot_product(std::span<const float>   query,
                                                      std::span<const uint8_t> quant_vec) noexcept;

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
     *
     * TODO with a vectorized "just in time" bit unpacking that doesn't need a temporary
     *  buffer we could make this const...
     */
    [[nodiscard]] float quantized_lhs_rhs_dot_product(std::span<const uint8_t> lhs,
                                                      std::span<const uint8_t> rhs) noexcept;

    /*
     * Computes the squared Euclidean distance between a quantized vector and a
     * _pre-rotated_ query vector. This is much more efficient than doing dequantize()
     * followed by an explicit float32 squared Euclidean distance computation, as we
     * don't have to perform any expensive rotations.
     *
     * `query` must have had a spin in the `rotate_vector_inplace` function prior to use,
     * i.e. it must be rotated into the same frame of reference as `quant_vec`.
     *
     * TODO determine the best quantization mode for Euclidean distance.
     *
     * Preconditions:
     *  - query.size() == dimensions()
     *  - quant_vec.size() == quantized_size()
     */
    [[nodiscard]] float pre_rotated_query_squared_euclidean_distance(std::span<const float>   query,
                                                                     std::span<const uint8_t> quant_vec) noexcept;

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
                                                                     std::span<const uint8_t> rhs) noexcept;
};

} // namespace vespalib::quant
