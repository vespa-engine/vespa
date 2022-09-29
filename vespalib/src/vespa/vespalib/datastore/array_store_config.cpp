// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store_config.h"
#include <cassert>

namespace vespalib::datastore {

ArrayStoreConfig::ArrayStoreConfig(uint32_t maxSmallArrayTypeId, const AllocSpec &defaultSpec)
    : _allocSpecs(),
      _enable_free_lists(false)
{
    for (uint32_t type_id = 0; type_id < (maxSmallArrayTypeId + 1); ++type_id) {
        _allocSpecs.push_back(defaultSpec);
    }
}

ArrayStoreConfig::ArrayStoreConfig(const AllocSpecVector &allocSpecs)
    : _allocSpecs(allocSpecs),
      _enable_free_lists(false)
{
}

const ArrayStoreConfig::AllocSpec &
ArrayStoreConfig::spec_for_type_id(uint32_t type_id) const
{
    assert(type_id < _allocSpecs.size());
    return _allocSpecs[type_id];
}

namespace {

size_t
capToLimits(size_t value, size_t minLimit, size_t maxLimit)
{
    size_t result = std::max(value, minLimit);
    return std::min(result, maxLimit);
}

size_t
alignToSmallPageSize(size_t value, size_t minLimit, size_t smallPageSize)
{
    return ((value - minLimit) / smallPageSize) * smallPageSize + minLimit;
}

}

ArrayStoreConfig
ArrayStoreConfig::optimizeForHugePage(uint32_t maxSmallArrayTypeId,
                                      std::function<size_t(uint32_t)> type_id_to_array_size,
                                      size_t hugePageSize,
                                      size_t smallPageSize,
                                      size_t entrySize,
                                      size_t maxEntryRefOffset,
                                      size_t minNumArraysForNewBuffer,
                                      float allocGrowFactor)
{
    AllocSpecVector allocSpecs;
    allocSpecs.emplace_back(0, maxEntryRefOffset, minNumArraysForNewBuffer, allocGrowFactor); // large array spec;
    for (uint32_t type_id = 1; type_id <= maxSmallArrayTypeId; ++type_id) {
        size_t arraySize = type_id_to_array_size(type_id);
        size_t numArraysForNewBuffer = hugePageSize / (entrySize * arraySize);
        numArraysForNewBuffer = capToLimits(numArraysForNewBuffer, minNumArraysForNewBuffer, maxEntryRefOffset);
        numArraysForNewBuffer = alignToSmallPageSize(numArraysForNewBuffer, minNumArraysForNewBuffer, smallPageSize);
        allocSpecs.emplace_back(0, maxEntryRefOffset, numArraysForNewBuffer, allocGrowFactor);
    }
    return ArrayStoreConfig(allocSpecs);
}

}
