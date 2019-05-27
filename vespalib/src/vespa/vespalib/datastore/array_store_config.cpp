// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store_config.h"
#include <cassert>

namespace search::datastore {

ArrayStoreConfig::ArrayStoreConfig(size_t maxSmallArraySize, const AllocSpec &defaultSpec)
    : _allocSpecs()
{
    for (size_t i = 0; i < (maxSmallArraySize + 1); ++i) {
        _allocSpecs.push_back(defaultSpec);
    }
}

ArrayStoreConfig::ArrayStoreConfig(const AllocSpecVector &allocSpecs)
    : _allocSpecs(allocSpecs)
{
}

const ArrayStoreConfig::AllocSpec &
ArrayStoreConfig::specForSize(size_t arraySize) const
{
    assert(arraySize < _allocSpecs.size());
    return _allocSpecs[arraySize];
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
ArrayStoreConfig::optimizeForHugePage(size_t maxSmallArraySize,
                                      size_t hugePageSize,
                                      size_t smallPageSize,
                                      size_t entrySize,
                                      size_t maxEntryRefOffset,
                                      size_t minNumArraysForNewBuffer,
                                      float allocGrowFactor)
{
    AllocSpecVector allocSpecs;
    allocSpecs.emplace_back(0, maxEntryRefOffset, minNumArraysForNewBuffer, allocGrowFactor); // large array spec;
    for (size_t arraySize = 1; arraySize <= maxSmallArraySize; ++arraySize) {
        size_t numArraysForNewBuffer = hugePageSize / (entrySize * arraySize);
        numArraysForNewBuffer = capToLimits(numArraysForNewBuffer, minNumArraysForNewBuffer, maxEntryRefOffset);
        numArraysForNewBuffer = alignToSmallPageSize(numArraysForNewBuffer, minNumArraysForNewBuffer, smallPageSize);
        allocSpecs.emplace_back(0, maxEntryRefOffset, numArraysForNewBuffer, allocGrowFactor);
    }
    return ArrayStoreConfig(allocSpecs);
}

}
