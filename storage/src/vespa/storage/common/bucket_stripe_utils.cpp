// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_stripe_utils.h"
#include <vespa/vespalib/util/alloc.h>
#include <cassert>

namespace storage {

namespace {

constexpr uint8_t used_bits_of(uint64_t key) noexcept {
    return static_cast<uint8_t>(key & 0b11'1111ULL);
}

}

size_t
stripe_of_bucket_key(uint64_t key, uint8_t n_stripe_bits) noexcept
{
    if (n_stripe_bits == 0) {
        return 0;
    }
    assert(used_bits_of(key) >= n_stripe_bits);
    // Since bucket keys have count-bits at the LSB positions, we want to look at the MSBs instead.
    return (key >> (64 - n_stripe_bits));
}

uint8_t
calc_num_stripe_bits(uint32_t n_stripes) noexcept
{
    assert(n_stripes > 0);
    if (n_stripes == 1) {
        return 0;
    }
    assert(n_stripes == adjusted_num_stripes(n_stripes));

    auto result = vespalib::Optimized::msbIdx(n_stripes);
    assert(result <= MaxStripeBits);
    return result;
}

uint32_t
adjusted_num_stripes(uint32_t n_stripes) noexcept
{
    if (n_stripes > 1) {
        if (n_stripes > MaxStripes) {
            return MaxStripes;
        }
        return vespalib::roundUp2inN(n_stripes);
    }
    return n_stripes;
}

uint32_t
tune_num_stripes_based_on_cpu_cores(uint32_t cpu_cores) noexcept
{
    // This should match the calculation used when node flavor is available:
    // config-model/src/main/java/com/yahoo/vespa/model/content/Distributor.java
    if (cpu_cores <= 16) {
        return 1;
    } else if (cpu_cores <= 64) {
        return 2;
    } else {
        return 4;
    }
}

}
