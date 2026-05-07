// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::quant::codebooks {

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
extern const float unit_norm_centroids_f32[4][16];

} // namespace vespalib::quant::codebooks
