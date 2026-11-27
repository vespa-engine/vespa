// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eden_evaluator.h"

#include <vespa/check_require.h>
#include <vespa/vespalib/hwaccelerated/eden_codebooks.h>
#include <vespa/vespalib/hwaccelerated/functions.h>

#include <cmath>

namespace vespalib::quant {

namespace codebooks = hwaccelerated::codebooks;

namespace {

[[nodiscard]] size_t compute_quantized_buffer_size(const size_t dimensions, const uint8_t bits) noexcept {
    const size_t dim_bytes = hwaccelerated::packed_bytes(bits, dimensions);
    return EdenConstants::scale_bytes + dim_bytes;
}

} // namespace

EdenEvaluator::EdenEvaluator(size_t dimensions, uint8_t bits) noexcept
    : _dimensions(dimensions), _quantized_size(compute_quantized_buffer_size(dimensions, bits)), _bits(bits) {
}

float EdenEvaluator::pre_rotated_query_dot_product(std::span<const float>   query,
                                                   std::span<const uint8_t> quant_vec) const noexcept {
    CHECK(query.size() == _dimensions);
    CHECK(quant_vec.size() == _quantized_size);
    const float    rhs_scale = extract_scale_factor(quant_vec.data());
    const uint8_t* rhs_bit_buf = packed_bits_buf(quant_vec.data());
    return rhs_scale * hwaccelerated::eden_f32_q_dot_product(query.data(), rhs_bit_buf, _bits, _dimensions);
}

float EdenEvaluator::quantized_lhs_rhs_dot_product(std::span<const uint8_t> lhs,
                                                   std::span<const uint8_t> rhs) const noexcept {
    CHECK(lhs.size() == _quantized_size);
    CHECK(rhs.size() == _quantized_size);
    const float    lhs_scale = extract_scale_factor(lhs.data());
    const uint8_t* lhs_bit_buf = packed_bits_buf(lhs.data());
    const float    rhs_scale = extract_scale_factor(rhs.data());
    const uint8_t* rhs_bit_buf = packed_bits_buf(rhs.data());
    float          dot;
    if (_bits == 1) {
        // 1-bit quantized vs. quantized dot product can be optimized substantially by mapping it
        // to a Hamming distance problem, i.e. popcount(lhs XOR rhs).
        // Any 1-bit index maps to a single absolute centroid value `C`, i.e. only the sign differs.
        // Assuming 0 maps to -C and 1 maps to +C (the inverse can be the case, it doesn't matter):
        //
        //  lhs   rhs   lhs XOR rhs    expr    result
        //   0     0         0        -C*-C      C*C
        //   0     1         1        -C* C    -(C*C)
        //   1     0         1         C*-C    -(C*C)
        //   1     1         0         C* C      C*C
        //
        // From this we can see that per-dimension products are always negative when lhs XOR rhs
        // is 1, which in turn means the Hamming distance is exactly equal to the number of -(C*C)
        // contributions. Conversely, the number of dimensions minus the Hamming distance is the
        // number of +(C*C) contributions.
        // Note that we require that any padding bits at the end of the vectors shall always be
        // zero, so these shall never spuriously add to the Hamming distance.
        const size_t    bit_buf_size = _quantized_size - EdenConstants::scale_bytes;
        const size_t    n_neg = hwaccelerated::binary_hamming_distance(lhs_bit_buf, rhs_bit_buf, bit_buf_size);
        constexpr float c = codebooks::eden_bf16_codebook(1)[1]; // +C centroid value
        dot = static_cast<float>(_dimensions - n_neg) * (c * c) + static_cast<float>(n_neg) * -(c * c);
    } else {
        dot = hwaccelerated::eden_q_q_dot_product(lhs_bit_buf, rhs_bit_buf, _bits, _dimensions);
    }
    return lhs_scale * rhs_scale * dot;
}

float EdenEvaluator::pre_rotated_query_squared_euclidean_distance(std::span<const float>   query,
                                                                  std::span<const uint8_t> quant_vec) const noexcept {
    CHECK(query.size() == _dimensions);
    CHECK(quant_vec.size() == _quantized_size);
    const float    rhs_scale = extract_scale_factor(quant_vec.data());
    const uint8_t* rhs_bit_buf = packed_bits_buf(quant_vec.data());
    return hwaccelerated::eden_f32_q_squared_euclidean_distance(query.data(), rhs_bit_buf, rhs_scale, _bits,
                                                                _dimensions);
}

float EdenEvaluator::quantized_lhs_rhs_squared_euclidean_distance(std::span<const uint8_t> lhs,
                                                                  std::span<const uint8_t> rhs) const noexcept {
    CHECK(lhs.size() == _quantized_size);
    CHECK(rhs.size() == _quantized_size);
    const float    lhs_scale = extract_scale_factor(lhs.data());
    const uint8_t* lhs_bit_buf = packed_bits_buf(lhs.data());
    const float    rhs_scale = extract_scale_factor(rhs.data());
    const uint8_t* rhs_bit_buf = packed_bits_buf(rhs.data());
    return hwaccelerated::eden_q_q_squared_euclidean_distance(lhs_bit_buf, rhs_bit_buf, lhs_scale, rhs_scale, _bits,
                                                              _dimensions);
}

} // namespace vespalib::quant
