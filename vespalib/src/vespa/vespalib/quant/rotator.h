// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/xoshiro.h>

#include <array>
#include <cstdint>
#include <span>

namespace vespalib::quant {

/*
 * Utility for performing pseudo-random rotations of `d`-dimensional vectors.
 *
 * This models the effects of multiplying a vector with a (pseudo-)random
 * rotation matrix (or its inverse/transpose), but does so in O(d lg d) instead
 * of O(d^2). Both forward and inverse (transposed) rotations are supported.
 * The rotated "space" induced by the rotator depends entirely on the seed value
 * provided in the Rotator constructor and must therefore be identical for all
 * related rotations (forward and/or inverse).
 *
 * `d` does not have to be a power of two, but the rotator will be most efficient
 * when it is.
 *
 * Rotations (both forward and inverse) are vector length-preserving.
 *
 * After a forward rotation, the expected distribution of each (normalized)
 * vector coordinate is expected to be N(0, 1/d) with high probability. I.e.
 * coordinate values follow a Gaussian distribution. This is _regardless_ of
 * the input coordinate distribution.
 *
 * Note about inverse rotations: although this is conceptually a lossless
 * transform, in reality the inverse will be very close, but not exactly equal,
 * to the original vector due precision loss incurred by floating point
 * arithmetic.
 *
 * Implementation details:
 *
 * We use the O(n lg n) fast Walsh-Hadamard transform (FWHT) as the main work
 * horse algorithm to implement rotations. The FWHT is a deterministic transform
 * and not a "true" matrix multiplication, but we can get results as if we had
 * used a random matrix multiplication if we pseudo-randomly flip the signs of
 * the input vector prior to rotation. We can then undo these sign flips after
 * the inverse rotation to get back the original vector. In the literature(tm)
 * this is often referred to as using a Rademacher distribution, as that is a
 * 50-50% probability distribution of {-1, +1}.
 *
 * Fast Hadamard transforms are only defined for power-of-two input/outputs, so
 * vectors that are not aligned at such a boundary gives us some challenges in
 * how randomized rotations should be done. Note that the FWHT requires all
 * dimensions to be present to be invertible.
 *
 * The FWHT can be seen as "distributing" the energy of every input dimension
 * across all the other dimensions. This is easy to get right when the vector
 * size is a power of two, because the FWHT can run across the entire range, and
 * we have to preserve all output dimensions anyway. But if d=513 we don’t want
 * to have to preserve 1024, i.e. 511 "redundant" quantized dimensions to be able
 * to run the FWHT in reverse.
 *
 * Our pragmatic solution to this is that non-power of two rotations are done
 * as multiple partially overlapping transforms of size 2^floor(log2(d)), i.e.
 * the highest power of two value that is < `d`. We'll call this `d_C` ("d capped").
 *
 *  1. FWH transform range [0, d_C), i.e. lower coordinates
 *  2. FWH transform range [d - d_C, d_C). i.e. upper coordinates
 *
 * The overlap between transforms 1 and 2 may range from d_C-1 down to 1,
 * depending on the value of `d`. The latter case is insufficient to distribute
 * well across high/low dimensions, so if the overlap (defined as d_C - (d - d_C))
 * is < d_C / 2, a 3rd transform is done:
 *
 *  3. FWH transform range [d/2 - d_C/2, (d/2 - d_C/2) + d_C), i.e. middle coords
 *
 * This transform is explicitly intended to distribute energy between the high
 * and low dimensions.
 *
 * We then run the entire process a second time so that energy distributed into
 * overlapping ranges in the first pass can be distributed _away_ from these
 * again. Empirically this seems to work pretty well.
 *
 * The high/low partial overlap approach is directly inspired by the RaBitQ
 * rotation algorithm. They use a different mechanism (random Kac's walk) to
 * "bridge the gap" across the sub-transforms. This is O(n) rather than O(n lg n),
 * but this operates on a padded output vector whereas we do not do any padding.
 * We may revisit the choice of algorithm if we _do_ add padding, assuming the
 * resulting distributions are at least as good as what we currently have.
 *
 * RaBitQ will also always do 4 rounds of sign flips+FWHT even when the vector
 * is a power of 2. Based on empirical observations we only do 2 rounds in this
 * case, though this also seems to be theoretically optimal for our particular
 * purposes; see the comments for `Rotator::RotationRounds`.
 *
 * A note on algorithm observations/experiments:
 *
 * The quality of the rotator algorithm has been estimated by sampling the
 * _expected_ Gaussian vs _actual_ post-rotation coordinate distributions for
 * several scenarios (sparse vectors, shifted mean normal distribution), and
 * then computing the Kullback-Leibler divergence between the two distributions.
 * The intuition and assumption(tm) being that a divergence close to zero means
 * that the rotation has the desired properties for our quantization purposes.
 *
 * Run-time complexity details (for one rotation round): The _lower_ bound is
 * always O(d lg d) (when d_C == d). When this is not the case the _upper_
 * bound is O(2d log d).
 */
class Rotator {
    using PrngType = Xoshiro256PlusPlusPrng;

    // The true goal(tm) of the rotator is to transform an arbitrary input
    // distribution into one where all coordinates are expected to follow a
    // Gaussian one.
    // According to the paper "Quantizing With Randomized Hadamard Transforms:
    // Efficient Heuristic Now Proven" (Ben-Basat et al., 2026), 2 randomized
    // Hadamard transforms (RHTs) suffices to achieve this:
    //
    //  "We show that after composing two RHTs on any d-sized input vector,
    //   the marginal distribution of every fixed coordinate of the normalized
    //   rotated vector is within O(d−1/2) of a standard Gaussian both in
    //   Kolmogorov distance and in 1-Wasserstein distance."
    //
    // We ensure that we always do at least 2 rotations covering every coordinate,
    // though non-power of two vectors will incur more rotations on average due
    // to our "workarounds" for handling these.
    constexpr static uint32_t RotationRounds = 2;

    // Use full PRNG states as sign flip seeds to avoid having to recompute
    // the internal state from a single seed for each flip round.
    // All internal PRNG seed derivations must be stable and deterministic
    // across Vespa versions for a given rotation strategy.
    struct RoundSeeds {
        const PrngType lo;
        const PrngType hi;
        const PrngType mid;

        explicit RoundSeeds(splitmix64&) noexcept;
    };
    const size_t                                 _dimensions;
    const size_t                                 _dimensions_capped;
    const float                                  _fwht_norm_scale;
    const std::array<RoundSeeds, RotationRounds> _sign_flip_seeds;

    Rotator(size_t dimensions, splitmix64 mixer) noexcept;

    void fwd_rand_rotate(std::span<float> v, const PrngType& seed) const noexcept;
    void inv_rand_rotate(std::span<float> v, const PrngType& seed) const noexcept;

public:
    Rotator(const size_t dimensions, const uint64_t seed) noexcept : Rotator(dimensions, splitmix64(seed)) {}

    [[nodiscard]] size_t dimensions() const noexcept { return _dimensions; }

    // Precondition: v.size() == dimensions()
    void rotate_forward(std::span<float> v) const noexcept;
    // Precondition: v.size() == dimensions()
    void rotate_inverse(std::span<float> v) const noexcept;
};

} // namespace vespalib::quant
