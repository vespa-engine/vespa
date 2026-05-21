// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rotator.h"

#include "hadamard.h"
#include "sign_flip.h"

#include <bit>
#include <cassert>

namespace vespalib::quant {

// splitmix by _ref_ is intentional; must have different seeds per distinct round
Rotator::RoundSeeds::RoundSeeds(splitmix64& mixer) noexcept : lo(mixer()), hi(mixer()), mid(mixer()) {
}

Rotator::Rotator(const size_t dimensions, splitmix64 mixer) noexcept
    : _dimensions(dimensions),
      _dimensions_capped(std::bit_floor(dimensions)),
      _fwht_norm_scale(hadamard_normalization_factor<float>(_dimensions_capped)), // _not_ `_dimensions`
      // List element init of element N shall be sequenced before N+1, so this should be
      // well-defined even though the RoundSeeds constructor has side effects on its argument.
      _sign_flip_seeds({RoundSeeds(mixer), RoundSeeds(mixer)}) {
    assert(dimensions > 0);
}

// Precondition: v.size() is a power of 2
void Rotator::fwd_rand_rotate(std::span<float> v, const PrngType& seed) const noexcept {
    // Sign flips, then FWHT
    PrngType prng(seed);
    flip_sign_bits(v, prng);
    hadamard(v.data(), v.size());
    post_hadamard_normalize_precomputed(v.data(), v.size(), _fwht_norm_scale);
}

// Precondition: v.size() is a power of 2
void Rotator::inv_rand_rotate(std::span<float> v, const PrngType& seed) const noexcept {
    // FWHT, then sign flips
    hadamard(v.data(), v.size());
    post_hadamard_normalize_precomputed(v.data(), v.size(), _fwht_norm_scale);
    PrngType prng(seed);
    flip_sign_bits(v, prng);
}

// See the Rotator "Implementation details" comments for algorithm description and rationale.
void Rotator::rotate_forward(std::span<float> v) const noexcept {
    assert(v.size() == _dimensions);
    const size_t d = _dimensions;
    const size_t d_capped = _dimensions_capped;

    for (uint32_t r = 0; r < RotationRounds; ++r) {
        const RoundSeeds& sfs = _sign_flip_seeds[r];
        fwd_rand_rotate(v.subspan(0, d_capped), sfs.lo);
        if (d != d_capped) {
            fwd_rand_rotate(v.subspan(d - d_capped, d_capped), sfs.hi);
            const size_t overlap = d_capped - (d - d_capped);
            if (overlap < d_capped / 2) {
                const size_t mid_start = (d / 2) - (d_capped / 2);
                fwd_rand_rotate(v.subspan(mid_start, d_capped), sfs.mid);
            }
        }
    }
}

// This is rotate_forward, but with every step in the exact reverse order.
void Rotator::rotate_inverse(std::span<float> v) const noexcept {
    assert(v.size() == _dimensions);
    const size_t d = _dimensions;
    const size_t d_capped = _dimensions_capped;

    for (uint32_t r = 0; r < RotationRounds; ++r) {
        // Important: seeds must also be in reverse order of forward rotation
        const RoundSeeds& sfs = _sign_flip_seeds[RotationRounds - r - 1];
        if (d != d_capped) {
            const size_t overlap = d_capped - (d - d_capped);
            if (overlap < d_capped / 2) {
                const size_t mid_start = (d / 2) - (d_capped / 2);
                inv_rand_rotate(v.subspan(mid_start, d_capped), sfs.mid);
            }
            inv_rand_rotate(v.subspan(d - d_capped, d_capped), sfs.hi);
        }
        inv_rand_rotate(v.subspan(0, d_capped), sfs.lo);
    }
}

// TODO defer Hadamard normalization until the end
//  - we can precompute the resulting scale since the number of sub-rotations
//    is deterministic for a given dimensionality.

} // namespace vespalib::quant
