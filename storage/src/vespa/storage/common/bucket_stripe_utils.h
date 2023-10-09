// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket_limits.h>
#include <cstdint>
#include <cstdlib>

namespace storage {

constexpr static uint8_t MaxStripeBits = spi::BucketLimits::MinUsedBits;
constexpr static uint32_t MaxStripes = (1ULL << MaxStripeBits);

/**
 * Returns the stripe in which the given bucket key belongs,
 * when using the given number of stripe bits.
 */
size_t stripe_of_bucket_key(uint64_t key, uint8_t n_stripe_bits) noexcept;

/**
 * Returns the number of stripe bits used to represent the given number of stripes.
 *
 * This also asserts that the number of stripes is valid (power of 2 and within MaxStripes boundary).
 */
uint8_t calc_num_stripe_bits(uint32_t n_stripes) noexcept;

/**
 * Validates the number of stripes and returns the (potentially adjusted) value.
 *
 * This ensures the number of stripes is a power of 2 and within MaxStripes boundary.
 */
[[nodiscard]] uint32_t adjusted_num_stripes(uint32_t n_stripes) noexcept;

/**
 * Tune the number of stripes based on the number of CPU cores.
 */
uint32_t tune_num_stripes_based_on_cpu_cores(uint32_t cpu_cores) noexcept;

}

