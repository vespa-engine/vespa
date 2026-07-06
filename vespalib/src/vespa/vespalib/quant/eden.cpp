// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eden.h"

#include "centroid.h"
#include "codebooks.h"
#include "hadamard.h"
#include "multi_bit_packer.h"

#include <vespa/vespalib/hwaccelerated/autovec_unrolled.h>
#include <vespa/vespalib/hwaccelerated/functions.h>

#include <cassert>
#include <cmath>
#include <cstring>

namespace vespalib::quant {

using hwaccelerated::squared_euclidean_length;
using hwaccelerated::sum_indexed_unrolled;

namespace {

[[nodiscard]] size_t compute_quantized_buffer_size(const size_t dimensions, const uint8_t bits) noexcept {
    (void)bits;
    constexpr size_t scale_bytes = sizeof(float); // TODO configurable scale factor precision (f16 vs f32)?
    const size_t     dim_bytes = with_packer_for_bit_count(bits, [dimensions](auto bp) {
        // type of bp will be MultiBitPacker<N>
        return decltype(bp)::packed_bytes(dimensions);
    });
    return scale_bytes + dim_bytes;
}

} // namespace

EdenQuantizer::EdenQuantizer(const size_t dimensions, const uint8_t bits, const uint64_t seed)
    : _dimensions(dimensions),
      _quantized_size(compute_quantized_buffer_size(dimensions, bits)),
      _rotator(dimensions, seed),
      _rot_tmp(dimensions),
      _idx_tmp(dimensions * 2), // also leave room for rhs bit unpacking storage
      _seed(seed),
      _sqrt_d(std::sqrtf(static_cast<float>(dimensions))),
      _bits(bits) {
    assert(bits >= 1 && bits <= 4);
}

EdenQuantizer::~EdenQuantizer() = default;

std::span<const float> EdenQuantizer::my_codebook() const noexcept {
    return {codebooks::unit_norm_centroids_f32[_bits - 1], 1u << _bits};
}

EdenQuantizer::ScaleAndCentroidIndexesPtr
EdenQuantizer::unary_unpack_bits_to_scratch_space(QuantizedVector q_x) noexcept {
    assert(q_x.size() == _quantized_size);
    const float      scale = q_x.scale();
    const PackedBits in_bits = q_x.packed_bits();
    with_packer_for_bit_count(_bits, [&](auto bp) {
        // type of bp will be MultiBitPacker<N>
        decltype(bp)::unpack(lhs_scratch_idx_space(), in_bits.data(), _dimensions);
    });
    return {scale, lhs_scratch_idx_space()};
}

std::pair<EdenQuantizer::ScaleAndCentroidIndexesPtr, EdenQuantizer::ScaleAndCentroidIndexesPtr>
EdenQuantizer::binary_unpack_bits_to_scratch_space(QuantizedVector lhs, QuantizedVector rhs) noexcept {
    assert(lhs.size() == _quantized_size);
    assert(rhs.size() == _quantized_size);
    // We have left room for an additional vector bit unpacking run in _idx_tmp
    uint8_t* lhs_idx = lhs_scratch_idx_space();
    uint8_t* rhs_idx = rhs_scratch_idx_space();
    with_packer_for_bit_count(_bits, [&](auto bp) {
        decltype(bp)::unpack(lhs_idx, lhs.packed_bits().data(), _dimensions);
        decltype(bp)::unpack(rhs_idx, rhs.packed_bits().data(), _dimensions);
    });
    const float lhs_scale = lhs.scale();
    const float rhs_scale = rhs.scale();
    return {{lhs_scale, lhs_idx}, {rhs_scale, rhs_idx}};
}

// Note for quantize() and dequantize(): the algorithm implementations try to closely
// follow the pseudocode outlined in [1], with some additional normalization factor
// details from [0].

void EdenQuantizer::quantize(std::span<const float> x, MutableQuantizedVector q_x,
                             const QuantMode quant_mode) noexcept {
    assert(x.size() == _dimensions);
    assert(q_x.size() == _quantized_size);
    const size_t d = _dimensions;
    // We compute the vector norm _prior_ to rotation to minimize errors introduced by
    // floating point arithmetic. Our rotation abstraction is mathematically speaking
    // length-preserving, but this is implemented with a FWH transform that increases
    // the magnitude by sqrt(d), which is then subsequently divided away in a subsequent
    // normalization step. Changes in magnitude are more likely to cause precision loss
    // since exponent adjustments can truncate mantissa LSBs.
    float x_norm2 = squared_euclidean_length(x.data(), d);
    if (x_norm2 == 0) [[unlikely]] {
        // Zero norm vectors must be special-cased, or we'll end up dividing by zero
        // when computing `nx` below. Set the scale explicitly to zero. For consistency,
        // also set all centroid indexes to zero, although this technically doesn't
        // matter since they are nullified by the scale.
        memset(q_x.data(), 0, q_x.size());
        return; // scale factor is (part of) the zeroed prefix
    }
    std::copy(x.begin(), x.end(), _rot_tmp.begin());
    _rotator.rotate_forward(_rot_tmp);
    // By convention, `y <- Rx`, i.e. the result of rotating `x` by matrix `R`
    const float* y = _rot_tmp.data();
    const auto   codebook = my_codebook();
    // Normalization factor to bring each rotated coordinate into expected N(0, 1).
    // Moving the sqrt(d) factor here means that we don't have to precompute scaled
    // centroids specifically for our dimensionality, but can use the N(0, 1) ones.
    // The scale factor subsumes this normalization so that the dequantization step
    // does not have to explicitly care about it (i.e. it can also just use the
    // N(0, 1) codebook centroids directly). x_norm2 is guaranteed to be non-zero.
    // This corresponds to "eta_x" in the [1] pseudocode, but I refuse to use Unicode
    // symbols in source code, so you'll have your ASCII, and you'll like it!
    const float nx = _sqrt_d / std::sqrtf(x_norm2);

    // By convention, `q` is the quantized vector as represented by each input
    // coordinate replaced by the value of the centroid its quantized index refers
    // to. We don't explicitly keep around `q` (just the _indexes_ that we then bit
    // pack), but we maintain a running dot product of it vs. the unquantized (but
    // rotated) vector, which is used to derive a scale factor for both EDEN-biased
    // and EDEN-unbiased.
    float    yq_dot = 0; // <y, q>
    float    scale = 0;
    uint8_t* idx_buf = lhs_scratch_idx_space();
    if (quant_mode == QuantMode::InnerProduct) { // EDEN-unbiased
        for (size_t i = 0; i < d; ++i) {
            const uint8_t idx = closest_centroid_index<float>(y[i] * nx, codebook);
            const float   c_i = codebook[idx];
            yq_dot += y[i] * c_i;
            idx_buf[i] = idx;
        }
        scale = x_norm2 / yq_dot;
    } else /* MSE/EDEN-biased */ {
        float q_norm2 = 0; // _squared_ Euclidean norm
        for (size_t i = 0; i < d; ++i) {
            const uint8_t idx = closest_centroid_index<float>(y[i] * nx, codebook);
            const float   c_i = codebook[idx];
            yq_dot += y[i] * c_i;
            q_norm2 += c_i * c_i;
            idx_buf[i] = idx;
        }
        scale = yq_dot / q_norm2;
    }
    with_packer_for_bit_count(_bits, [&](auto bp) {
        uint8_t* out_bits = q_x.packed_bits().data();
        decltype(bp)::pack(out_bits, idx_buf, _dimensions);
    });
    q_x.set_scale(scale);
}

void EdenQuantizer::dequantize(QuantizedVector q_x, std::span<float> dq_x) noexcept {
    assert(dq_x.size() == _dimensions);
    const auto [scale, centroid_idx] = unary_unpack_bits_to_scratch_space(q_x);
    const auto codebook = my_codebook();
    for (size_t i = 0; i < _dimensions; ++i) {
        dq_x[i] = codebook[centroid_idx[i]] * scale;
    }
    _rotator.rotate_inverse(dq_x);
}

void EdenQuantizer::rotate_vector_inplace(std::span<float> vec) const noexcept {
    assert(vec.size() == _dimensions);
    _rotator.rotate_forward(vec);
}

float EdenQuantizer::pre_rotated_query_dot_product(std::span<const float> query, QuantizedVector quant_vec) noexcept {
    assert(query.size() == _dimensions);
    const auto [scale, centroid_idx] = unary_unpack_bits_to_scratch_space(quant_vec);
    const auto codebook = my_codebook();
    // Taunt the auto-vectorizer by explicitly running parallel fp accumulators.
    // 8x is only slightly faster than 4x (on M3 AArch64), but 4x is by itself ~4x
    // faster than the naive scalar loop version.
    // clang-format off: lambda formatting
    return scale * sum_indexed_unrolled<8, float>(_dimensions, [&](size_t idx) noexcept {
        return query[idx] * codebook[centroid_idx[idx]];
    });
    // clang-format on
}

float EdenQuantizer::quantized_lhs_rhs_dot_product(QuantizedVector lhs, QuantizedVector rhs) noexcept {
    const auto [lhs_unp, rhs_unp] = binary_unpack_bits_to_scratch_space(lhs, rhs);
    const auto codebook = my_codebook();
    // clang-format off: lambda formatting
    const float dot = sum_indexed_unrolled<8, float>(_dimensions, [&](size_t idx) noexcept {
        return codebook[lhs_unp.centroid_idx[idx]] * codebook[rhs_unp.centroid_idx[idx]];
    });
    // clang-format on
    return lhs_unp.scale * rhs_unp.scale * dot;
}

float EdenQuantizer::pre_rotated_query_squared_euclidean_distance(std::span<const float> query,
                                                                  QuantizedVector        quant_vec) noexcept {
    assert(query.size() == _dimensions);
    const auto [scale, centroid_idx] = unary_unpack_bits_to_scratch_space(quant_vec);
    const auto codebook = my_codebook();
    return sum_indexed_unrolled<8, float>(_dimensions, [&](size_t idx) noexcept {
        const float d = query[idx] - (codebook[centroid_idx[idx]] * scale);
        return d * d;
    });
}

float EdenQuantizer::quantized_lhs_rhs_squared_euclidean_distance(QuantizedVector lhs, QuantizedVector rhs) noexcept {
    const auto [lhs_unp, rhs_unp] = binary_unpack_bits_to_scratch_space(lhs, rhs);
    const auto codebook = my_codebook();
    return sum_indexed_unrolled<8, float>(_dimensions, [&](const size_t i) noexcept {
        const float a = codebook[lhs_unp.centroid_idx[i]] * lhs_unp.scale;
        const float b = codebook[rhs_unp.centroid_idx[i]] * rhs_unp.scale;
        const float d = a - b;
        return d * d;
    });
}

} // namespace vespalib::quant
