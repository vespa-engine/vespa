// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <span>

namespace vespalib::hwaccelerated::codebooks {

/*
 * Precomputed codebooks that are optimal for minimizing Mean Squared Error (MSE)
 * when quantizing a unit normal, i.e. N(0, 1), probability distribution into
 * 2^B parts. B is here the number of bits used for quantization.
 *
 * The optimal quantized centroid for any input coordinate is the one that has the
 * minimal absolute distance from the coordinate.
 *
 * The array at index 0 is for 1 bit quantization, index 1 for 2 bits and so on.
 * This is a "full" centroid array that is symmetric around zero, i.e. the first
 * half elements are negative, the second half is their positive mirror.
 *
 * For unit_norm_centroids[B-1], note that only the first 2^B entries are specified.
 *
 * Important: for a vector with `d` dimensions, these centroids must be scaled with
 * 1/sqrt(d) to fit the expected distribution
 */
extern const float eden_unit_norm_centroids_f32[4][16];

/*
 * These are the same centroids as `unit_norm_centroids_f32`, but each centroid
 * value has been truncated to BFloat16 precision.
 */
extern const float eden_unit_norm_centroids_bf16_as_f32[4][16];

// Precondition: `bits` is in [1, 4]
[[nodiscard]] inline std::span<const float> eden_f32_codebook(const uint8_t bits) noexcept {
    const float* bits_buf = eden_unit_norm_centroids_f32[bits - 1];
    return {bits_buf, 1u << bits};
}

// Precondition: `bits` is in [1, 4]
[[nodiscard]] inline std::span<const float> eden_bf16_codebook(const uint8_t bits) noexcept {
    const float* bits_buf = eden_unit_norm_centroids_bf16_as_f32[bits - 1];
    return {bits_buf, 1u << bits};
}

} // namespace vespalib::hwaccelerated::codebooks
