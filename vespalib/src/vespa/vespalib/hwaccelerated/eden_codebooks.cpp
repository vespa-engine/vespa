// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eden_codebooks.h"

namespace vespalib::hwaccelerated::codebooks {

/* Codebooks have been computed offline using an iterative Lloyd-Max algorithm
 * implementation with a fixed iteration count of 1000.
 *
 * See lloyd_max.py in this directory, which was used for this computation.
 *
 * Our Lloyd-Max codebook results have been crosschecked with the centroids in:
 *  - https://github.com/securefederatedai/openfederatedlearning/blob/develop/openfl/pipelines/eden_pipeline.py
 *    (EDEN quantization)
 *  - https://github.com/VectorDB-NTU/rabitq-turboquant-comparison/blob/main/accuracy/turbo_quant.py
 *    (TurboQuant comparison by RaBitQ authors)
 *
 * MSE of our codebook centroids with the above is:
 * 1 bit:
 *   EDEN:   1.232595164407831e-32
 *   RaBitQ: 1.9289412309414601e-13
 * 2 bits:
 *   EDEN:   1.3801577665466222e-17
 *   RaBitQ: 8.902629970902564e-08
 * 3 bits:
 *   EDEN:   6.964738710623366e-16
 *   RaBitQ: 3.869521778101932e-07
 * 4 bits:
 *   EDEN:   1.328846476913042e-13
 *   RaBitQ: 4.591929788084447e-05
 *
 * We shall treat this as "close enough", noting that we're much closer to the EDEN
 * centroids, which also are specified with much greater precision than the ones from
 * the RaBitQ TurboQuant comparison.
 *
 * Note that centroids are always symmetric around zero, so each centroid has a
 * "twin" with the opposite sign. Centroids are laid out "per sign" in increasing
 * absolute distance from zero (rather than being ordered in strict numeric order).
 * This more easily allows for optimizations that only have to consider half the
 * number of distinct centroid values, implicitly negating a centroid value if its
 * index is less than |centroids|/2
 *
 * Hex floats are used to more easily show that the BF16 values are in fact prefixes
 * of the full precision float32 values.
 */
// clang-format off: intentional array formatting
const float eden_unit_norm_centroids_f32[4][16] = {
    // 1 bit:
    // -0.797884583, 0.797884583
    {-0x1.988454p-1, 0x1.988454p-1},
    // 2 bits:
    // -0.452780038, -1.510417581,
    //  0.452780038,  1.510417581
    {-0x1.CFA592p-2, -0x1.82AABAp+0,
      0x1.CFA592p-2,  0x1.82AABAp+0},
    // 3 bits:
    // -0.245094180, -0.756005287, -1.343909264, -2.151945591,
    //  0.245094180,  0.756005287,  1.343909264,  2.151945591
    {-0x1.F5F3Fp-3, -0x1.83132p-1, -0x1.580A7p+0, -0x1.1372F4p+1,
      0x1.F5F3Fp-3,  0x1.83132p-1,  0x1.580A7p+0,  0x1.1372F4p+1},
    // 4 bits:
    // -0.128395036, -0.388048291, -0.656759143, -0.942340434, -1.256231189, -1.618046403, -2.069017172, -2.732589483
    //  0.128395036,  0.388048291,  0.656759143,  0.942340434,  1.256231189,  1.618046403,  2.069017172,  2.732589483
    {-0x1.06F3FAp-3, -0x1.8D5C88p-2, -0x1.5042BCp-1, -0x1.E27A72p-1, -0x1.41985Ep+0, -0x1.9E384Ap+0, -0x1.08D58Ep+1,
     -0x1.5DC57EP+1,
      0x1.06F3FAp-3,  0x1.8D5C88p-2,  0x1.5042BCp-1,  0x1.E27A72p-1,  0x1.41985Ep+0,  0x1.9E384Ap+0,  0x1.08D58Ep+1,
      0x1.5DC57Ep+1}};

const float eden_unit_norm_centroids_bf16_as_f32[4][16] = {
    // 1 bit:
    // -0.796875, 0.796875
    {-0x1.98p-1, 0x1.98p-1},
    // 2 bits:
    // -0.45117188, -1.5078125,
    //  0.45117188,  1.5078125
    {-0x1.CEp-2, -0x1.82p+0,
      0x1.CEp-2,  0x1.82p+0},
    // 3 bits:
    // -0.24414062, -0.75390625, -1.34375, -2.140625,
    //  0.24414062 , 0.75390625,  1.34375,  2.140625
    {-0x1.F4p-3, -0x1.82p-1, -0x1.58p+0, -0x1.12p+1,
      0x1.F4p-3,  0x1.82p-1,  0x1.58p+0,  0x1.12p+1},
    // 4 bits:
    // -0.12792969, -0.38671875, -0.65625, -0.94140625, -1.25, -1.6171875, -2.0625, -2.71875,
    //  0.12792969,  0.38671875,  0.65625,  0.94140625,  1.25,  1.6171875,  2.0625,  2.71875
    {-0x1.06p-3, -0x1.8Cp-2, -0x1.5p-1, -0x1.E2p-1, -0x1.4p+0, -0x1.9Ep+0, -0x1.08p+1, -0x1.5Cp+1,
      0x1.06p-3,  0x1.8Cp-2,  0x1.5p-1,  0x1.E2p-1,  0x1.4p+0,  0x1.9Ep+0,  0x1.08p+1,  0x1.5Cp+1}};
// clang-format on

} // namespace vespalib::hwaccelerated::codebooks
