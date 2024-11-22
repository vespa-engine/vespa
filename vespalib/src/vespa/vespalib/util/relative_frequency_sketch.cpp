// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "relative_frequency_sketch.h"
#include <algorithm>
#include <cassert>
#include <cstring>

namespace vespalib {

/**
 * Cf. the description of the Caffeine sketch in [2][3] we use 8 bytes per cache entry and
 * a sample (window) size W that is 10x the cache size (C). It is not immediately clear why
 * W/C = 10 rather than 16 since we use 4 bits and log2(10) = 3.321..., but surely the
 * underlying reason must be very exciting.
 *
 * Note: `Alloc` currently does not support < 512 byte alignment, which is suboptimal if
 * the allocation is small enough to end up on the heap (FIXME).
 */
RawRelativeFrequencySketch::RawRelativeFrequencySketch(size_t count)
    : _buf(alloc::Alloc::alloc_aligned(roundUp2inN(std::max(size_t(64U), count * 8)), 512)),
      _estimated_sample_count(0),
      _window_size((_buf.size() / 8) * 10),
      _block_mask_bits(_buf.size() > 64 ? Optimized::msbIdx(_buf.size() / 64) : 0)
{
    assert(_block_mask_bits <= 44); // Will always be the case in practice, but it's an invariant...
    memset(_buf.get(), 0, _buf.size());
}

RawRelativeFrequencySketch::~RawRelativeFrequencySketch() = default;

/**
 * Add an element by its hash. This involves incrementing 4 distinct counters based on the hash.
 *
 * Our sketch buffer is logically divided into buf_size/64 distinct 64-byte blocks. Each
 * block is in turn logically divided into 4 rows x 32 4-bit counters, laid out sequentially.
 * Each counter is saturated at 15, i.e. there is no overflow.
 *
 * We first select the block based on the B LSBs of the hash, where B is log2(buffer_size/64)
 * and buffer_size is always a power of two. These B bits are considered consumed and are not
 * used for anything else.
 *
 * Within the block we always update exactly 1 counter in each logical row. Use 5 distinct
 * bits from the hash for each of the 4 row updates (4 bits to select a byte out of 16, 1 for
 * selecting either the high or low in-byte nibble).
 *
 * Iff the estimated sample count reaches the window size threshold we implicitly divide all
 * recorded 4-bit counters in half.
 */
template <bool ReturnMinCount>
uint8_t RawRelativeFrequencySketch::add_by_hash_impl(uint64_t hash) noexcept {
    const uint64_t block = hash & ((1ULL << _block_mask_bits) - 1);
    hash >>= _block_mask_bits;
    assert(block*64 + 64 <= _buf.size());
    auto* block_ptr = static_cast<uint8_t*>(_buf.get()) + (block * 64);
    uint8_t new_counters[4];
    // The compiler will happily and easily unroll this loop.
    for (uint8_t i = 0; i < 4; ++i) {
        uint8_t h = hash >> (i*5);
        uint8_t* vp = block_ptr + (i * 16) + (h & 0xf); // row #i byte select
        const uint8_t v = *vp;
        h >>= 4;
        const uint8_t nib_shift = (h & 1) * 4; // High or low nibble shift factor (4 or 0)
        const uint8_t nib_mask  = 0xf << nib_shift;
        const uint8_t nib_old   = (v & nib_mask) >> nib_shift;
        new_counters[i]         = nib_old < 15 ? nib_old + 1 : 15; // Saturated add
        const uint8_t nib_rem   = v & ~nib_mask; // Untouched nibble that should be preserved
        *vp = (new_counters[i] << nib_shift) | nib_rem;
    }
    if (++_estimated_sample_count >= _window_size) [[unlikely]] {
        div_all_by_2();
        _estimated_sample_count /= 2;
    }
    if constexpr (ReturnMinCount) {
        return std::min(std::min(new_counters[0], new_counters[1]),
                        std::min(new_counters[2], new_counters[3]));
    } else {
        return 0;
    }
}

void RawRelativeFrequencySketch::add_by_hash(uint64_t hash) noexcept {
    (void)add_by_hash_impl<false>(hash);
}

uint8_t RawRelativeFrequencySketch::add_and_count_by_hash(uint64_t hash) noexcept {
    return add_by_hash_impl<true>(hash);
}

/**
 * Estimates the count associated with the given hash. This uses the exact same counter
 * addressing as `add_by_hash()`, so refer to that function for a description on the
 * semantics. As the name Count-Min implies we take the _minimum_ of the observed counters
 * and return this value to the caller.
 *
 * This will over-estimate the true frequency iff _all_ counters overlap with at least one
 * other element, but it will never under-estimate (here casually ignoring the effects of
 * counter decaying).
 */
uint8_t RawRelativeFrequencySketch::count_min_by_hash(uint64_t hash) const noexcept {
    const uint64_t block = hash & ((1ULL << _block_mask_bits) - 1);
    hash >>= _block_mask_bits;
    const uint8_t* block_ptr = static_cast<const uint8_t*>(_buf.get()) + (block * 64);
    uint8_t cm[4];
    for (uint8_t i = 0; i < 4; ++i) {
        uint8_t h = hash >> (i*5);
        const uint8_t* vp = block_ptr + (i * 16) + (h & 0xf); // row #i byte select
        h >>= 4;
        const uint8_t nib_shift = (h & 1) * 4; // 4 or 0
        const uint8_t nib_mask = 0xf << nib_shift;
        cm[i] = (*vp & nib_mask) >> nib_shift;
    }
    return std::min(std::min(cm[0], cm[1]), std::min(cm[2], cm[3]));
}

std::strong_ordering
RawRelativeFrequencySketch::estimate_relative_frequency_by_hash(uint64_t lhs_hash, uint64_t rhs_hash) const noexcept {
    return count_min_by_hash(lhs_hash) <=> count_min_by_hash(rhs_hash);
}

/**
 * Divides all the 4-bit counters in the sketch by 2. Since this integral division, we
 * inherently lose some precision for odd-numbered counter values.
 *
 * We speed up the division by treating each 64-byte block as 8x u64 values that can
 * logically be processed in parallel. The compiler will unroll and auto-vectorize the u64
 * fixed-count inner-loop as expected (verified via Godbolt).
 *
 * Each u64 value is right-shifted by 1. This shifts the LSB of all 16 4-bit nibbles (except
 * the last one) into the MSB of the next nibble. We want the semantics as-if each nibble
 * were in its own register, which would mean shifting in a zero bit in the MSB instead.
 * We emulate this by explicitly clearing all nibble MSBs. This effectively divides all
 * nibbles by 2. This should be entirely endian-agnostic.
 *
 * Example:
 *
 * Before:
 *  nibble#: [ 15 ][ 14 ][ 13 ][ 12 ][ ...
 *  bits:     1111  0011  0000  1100   ...
 *  value:      15     3     0    12   ...
 *
 * After shift (_uncorrected_ prior to masking)
 *  nibble#: [ 15 ][ 14 ][ 13 ][ 12 ][ ...
 *  bits:     0111  1001  1000  0110  0...
 *  value:       7     9     8     6   ...
 *
 * We will then apply the following per-nibble mask:
 *  mask:     0111  0111  0111  0111  0...
 *
 * After shift (corrected by masking off nibble MSBs)
 *  nibble#: [ 15 ][ 14 ][ 13 ][ 12 ][ ...
 *  bits:     0111  0001  0000  0110  0...
 *  value:       7     1     0     6   ...
 */
void RawRelativeFrequencySketch::div_all_by_2() noexcept {
    const uint64_t n_blocks = _buf.size() / 64;
    auto* block_ptr = static_cast<uint8_t*>(_buf.get());
    for (uint64_t i = 0; i < n_blocks; ++i) {
        for (uint32_t j = 0; j < 8; ++j) {
            uint64_t chunk;
            static_assert(sizeof(chunk)*8 == 64);
            // Compiler will optimize away memcpys (avoids aliasing).
            memcpy(&chunk, block_ptr + (8 * j), 8);
            chunk >>= 1;
            chunk &= 0x7777'7777'7777'7777ULL; // nibble ~MSB mask
            memcpy(block_ptr + (8 * j), &chunk, 8);
        }
        block_ptr += 64;
    }
}

} // vespalib
