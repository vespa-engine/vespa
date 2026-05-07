// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "codebooks.h"

namespace vespalib::quant::codebooks {

// clang-format off: intentional array element indents

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
 */
const float unit_norm_centroids_f32[4][16] = {
    // 1 bit:
    {-0.7978845608028653,  0.7978845608028653},
    // 2 bits:
    {-1.510417608499096,  -0.45278003463649225, 0.45278003463649225, 1.510417608499096},
    // 3 bits:
    {-2.1519457045369887, -1.3439092785050009, -0.7560052812058778, -0.2450941789442218,
      0.2450941789442218 , 0.7560052812058778,  1.3439092785050009,  2.1519457045369887},
    // 4 bits:
    {-2.732589570995166,  -2.0690172265313915, -1.618046386021888,  -1.2562311973471827,
     -0.9423404564869668, -0.6567591185324675, -0.3880482994902925, -0.12839502985114778,
      0.12839502985114778, 0.3880482994902925,  0.6567591185324675,  0.9423404564869668,
      1.2562311973471827,  1.618046386021888,   2.0690172265313915,  2.732589570995166}
};

// clang-format on

} // namespace vespalib::quant::codebooks
