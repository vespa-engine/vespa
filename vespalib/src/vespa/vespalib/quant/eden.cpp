// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eden.h"

#include "hadamard.h"

#include <vespa/check_require.h>
#include <vespa/vespalib/hwaccelerated/autovec_unrolled.h>
#include <vespa/vespalib/hwaccelerated/eden_codebooks.h>
#include <vespa/vespalib/hwaccelerated/functions.h>

#include <cmath>
#include <cstring>

namespace vespalib::quant {

using hwaccelerated::squared_euclidean_length;
using hwaccelerated::sum_indexed_unrolled;
namespace codebooks = hwaccelerated::codebooks;

EdenQuantizer::EdenQuantizer(const size_t dimensions, const uint8_t bits, const uint64_t seed)
    : EdenEvaluator(dimensions, bits),
      _rotator(dimensions, seed),
      _rot_tmp(dimensions),
      _idx_tmp(dimensions),
      _seed(seed),
      _sqrt_d(std::sqrtf(static_cast<float>(dimensions))) {
    CHECK(bits >= 1 && bits <= 4);
}

EdenQuantizer::~EdenQuantizer() = default;

std::span<const float> EdenQuantizer::my_codebook() const noexcept {
    return codebooks::eden_bf16_codebook(_bits);
}

EdenQuantizer::ScaleAndCentroidIndexesPtr
EdenQuantizer::unpack_bits_to_scratch_space(std::span<const uint8_t> buf) noexcept {
    CHECK(buf.size() == _quantized_size);
    const float    scale = extract_scale_factor(buf.data());
    const uint8_t* in_bits = packed_bits_buf(buf.data());
    hwaccelerated::unpack_bits(in_bits, scratch_idx_space(), _bits, _dimensions);
    return {scale, scratch_idx_space()};
}

// Note for quantize() and dequantize(): the algorithm implementations try to closely
// follow the pseudocode outlined in [1], with some additional normalization factor
// details from [0].

void EdenQuantizer::quantize(std::span<const float> x, std::span<uint8_t> q_x, const QuantMode quant_mode) noexcept {
    CHECK(x.size() == _dimensions);
    CHECK(q_x.size() == _quantized_size);
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
        return;
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
    uint8_t* idx_buf = scratch_idx_space();
    hwaccelerated::eden_quantize_centroids(y, idx_buf, _bits, nx, d);
    if (quant_mode == QuantMode::InnerProduct) { // EDEN-unbiased
        for (size_t i = 0; i < d; ++i) {
            const float c_i = codebook[idx_buf[i]];
            yq_dot += y[i] * c_i;
        }
        scale = x_norm2 / yq_dot;
    } else /* MSE/EDEN-biased */ {
        float q_norm2 = 0; // _squared_ Euclidean norm
        for (size_t i = 0; i < d; ++i) {
            const float c_i = codebook[idx_buf[i]];
            yq_dot += y[i] * c_i;
            q_norm2 += c_i * c_i;
        }
        scale = yq_dot / q_norm2;
    }
    hwaccelerated::pack_bits(idx_buf, packed_bits_buf(q_x.data()), _bits, _dimensions);
    memcpy(q_x.data(), &scale, sizeof(float));
}

void EdenQuantizer::dequantize(std::span<const uint8_t> q_x, std::span<float> dq_x) noexcept {
    CHECK(dq_x.size() == _dimensions);
    const auto [scale, centroid_idx] = unpack_bits_to_scratch_space(q_x);
    const auto codebook = my_codebook();
    for (size_t i = 0; i < _dimensions; ++i) {
        dq_x[i] = codebook[centroid_idx[i]] * scale;
    }
    _rotator.rotate_inverse(dq_x);
}

void EdenQuantizer::rotate_vector_inplace(std::span<float> vec) const noexcept {
    CHECK(vec.size() == _dimensions);
    _rotator.rotate_forward(vec);
}

} // namespace vespalib::quant
