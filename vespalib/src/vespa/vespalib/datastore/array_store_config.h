// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <vector>

namespace vespalib::datastore {

/**
 * Config specifying layout and buffer allocation strategy for an array store.
 */
class ArrayStoreConfig
{
public:
    /**
     * Specification of buffer allocation strategy for arrays of a given size.
     */
    struct AllocSpec {
        // Minimum number of arrays to allocate in a buffer.
        size_t minArraysInBuffer;
        // Maximum number of arrays to allocate in a buffer.
        size_t maxArraysInBuffer;
        // Number of arrays needed before allocating a new buffer instead of just resizing the first one.
        size_t numArraysForNewBuffer;
        // Grow factor used when allocating a new buffer.
        float allocGrowFactor;
        AllocSpec(size_t minArraysInBuffer_,
                  size_t maxArraysInBuffer_,
                  size_t numArraysForNewBuffer_,
                  float allocGrowFactor_) noexcept
            : minArraysInBuffer(minArraysInBuffer_),
              maxArraysInBuffer(maxArraysInBuffer_),
              numArraysForNewBuffer(numArraysForNewBuffer_),
              allocGrowFactor(allocGrowFactor_) {}
    };

    using AllocSpecVector = std::vector<AllocSpec>;

private:
    AllocSpecVector _allocSpecs;
    bool _enable_free_lists;

    /**
     * Setup an array store with arrays of size [1-(allocSpecs.size()-1)] allocated in buffers and
     * larger arrays are heap allocated. The allocation spec for a given array size is found in the given vector.
     * Allocation spec for large arrays is located at position 0.
     */
    ArrayStoreConfig(const AllocSpecVector &allocSpecs);

public:
    /**
     * Setup an array store with arrays of size [1-maxSmallArraySize] allocated in buffers
     * with the given default allocation spec. Larger arrays are heap allocated.
     */
    ArrayStoreConfig(size_t maxSmallArraySize, const AllocSpec &defaultSpec);

    size_t maxSmallArraySize() const { return _allocSpecs.size() - 1; }
    const AllocSpec &specForSize(size_t arraySize) const;
    ArrayStoreConfig& enable_free_lists(bool enable) & noexcept {
        _enable_free_lists = enable;
        return *this;
    }
    ArrayStoreConfig&& enable_free_lists(bool enable) && noexcept {
        _enable_free_lists = enable;
        return std::move(*this);
    }
    [[nodiscard]] bool enable_free_lists() const noexcept { return _enable_free_lists; }

    /**
     * Generate a config that is optimized for the given memory huge page size.
     */
    static ArrayStoreConfig optimizeForHugePage(size_t maxSmallArraySize,
                                                size_t hugePageSize,
                                                size_t smallPageSize,
                                                size_t entrySize,
                                                size_t maxEntryRefOffset,
                                                size_t minNumArraysForNewBuffer,
                                                float allocGrowFactor);
};

}
