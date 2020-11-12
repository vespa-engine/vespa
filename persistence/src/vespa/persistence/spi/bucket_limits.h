#pragma once

#include <cstdint>

namespace storage::spi {

/**
 * Wrapper of constants that specify absolute lower and upper bounds for buckets
 * that are to be processed on a node. These invariants must be maintained by
 * split and join operations, as well as bucket creation.
 */
struct BucketLimits {
    constexpr static uint8_t MinUsedBits = 8;
    constexpr static uint8_t MaxUsedBits = 58;
};

}
